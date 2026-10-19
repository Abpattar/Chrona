package com.chrona.app.di

import android.content.Context
import androidx.room.Room
import com.chrona.app.BuildConfig
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.preferences.DataStorePreferencesRepository
import com.chrona.app.data.preferences.settingsDataStore
import com.chrona.app.data.repository.EventRepository
import com.chrona.app.data.repository.PreferencesRepository
import com.chrona.app.data.repository.ReminderHistoryRepository
import com.chrona.app.data.repository.RoomEventRepository
import com.chrona.app.data.repository.RoomRelationshipRepository
import com.chrona.app.data.repository.RoomReminderHistoryRepository
import com.chrona.app.data.repository.RoomScheduleRevisionRepository
import com.chrona.app.data.repository.RoomSimulationStore
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.parse.DeterministicTaskIntentParser
import com.chrona.app.domain.parse.ParsePreferences
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.recovery.ApplyRecoveryUseCase
import com.chrona.app.domain.recovery.DefaultApplyRecoveryUseCase
import com.chrona.app.domain.recovery.DefaultUndoScheduleRevisionUseCase
import com.chrona.app.domain.recovery.DeterministicRecoveryPlanner
import com.chrona.app.domain.recovery.ProposeRecoveryUseCase
import com.chrona.app.domain.recovery.UndoScheduleRevisionUseCase
import com.chrona.app.domain.relation.RelationGraph
import com.chrona.app.domain.reminder.DeterministicReminderPolicy
import com.chrona.app.domain.reminder.ReminderPolicy
import com.chrona.app.domain.reminder.ReminderPresenter
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.DeterministicMultiDayPlanner
import com.chrona.app.domain.scheduling.ScheduleEngine
import com.chrona.app.domain.simulation.LifeSimulationEngine
import com.chrona.app.platform.notification.NotificationPresenter
import com.chrona.app.platform.overlay.OverlayPresenter
import com.chrona.app.platform.cloud.CloudConfig
import com.chrona.app.platform.cloud.CloudTaskUnderstandingProvider
import com.chrona.app.platform.cloud.UrlConnectionHttpTransport
import com.chrona.app.platform.speech.AndroidSpeechRecognitionAdapter
import com.chrona.app.platform.speech.SpeechRecognitionAdapter
import com.chrona.app.usecase.ApplyReminderActionUseCase
import com.chrona.app.usecase.CreateTaskFromIntentUseCase
import com.chrona.app.usecase.CreateTaskUseCase
import com.chrona.app.usecase.DeliverRemindersUseCase
import com.chrona.app.usecase.EvaluateRemindersUseCase
import com.chrona.app.usecase.EventUseCases
import com.chrona.app.usecase.IdGenerator
import com.chrona.app.usecase.ListTasksUseCase
import com.chrona.app.usecase.RecordReminderHistoryUseCase
import com.chrona.app.usecase.TaskLifecycleUseCases
import com.chrona.app.usecase.TimeSource
import com.chrona.app.usecase.AddRelationshipUseCase
import com.chrona.app.usecase.ConfirmRelationshipUseCase
import com.chrona.app.usecase.DeleteRelationshipUseCase
import com.chrona.app.usecase.UpdateRelationshipUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manual composition root (MASTER_ARCHITECTURE.md §3.8).
 *
 * No DI framework: with one module and a handful of collaborators, generated graphs would add
 * a build step and indirection without removing any real complexity. Wiring is explicit so a
 * reader can see the whole object graph in one file.
 *
 * Clocks and ID generation are parameters, not hard-coded calls, so tests can substitute
 * deterministic versions (CODING_CONVENTIONS.md).
 */
class AppContainer(
    context: Context,
    val database: ChronaDatabase = buildDatabase(context),
    val timeSource: TimeSource = SystemTimeSource(),
    val idGenerator: IdGenerator = UuidGenerator(),
) {
    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val eventRepository: EventRepository = RoomEventRepository(database)
    val relationshipRepository = RoomRelationshipRepository(database)
    val scheduleRevisionRepository =
        RoomScheduleRevisionRepository(database, newId = { idGenerator.newId() })

    val createTask = CreateTaskUseCase(taskRepository, idGenerator, timeSource)
    val listTasks = ListTasksUseCase(taskRepository)
    val lifecycle = TaskLifecycleUseCases(taskRepository, timeSource)
    val events = EventUseCases(eventRepository, idGenerator, timeSource)

    /** Relationship editing for the board (P05). All validate before writing. */
    val addRelationship = AddRelationshipUseCase(relationshipRepository, timeSource, idGenerator)
    val updateRelationship = UpdateRelationshipUseCase(relationshipRepository)
    val deleteRelationship = DeleteRelationshipUseCase(relationshipRepository)
    val confirmRelationship = ConfirmRelationshipUseCase(relationshipRepository, timeSource)

    /** One deterministic planner for both first planning and recovery (CH-SCHED-001). */
    val scheduleEngine: ScheduleEngine = DeterministicScheduleEngine()

    /** The same engine, driven day by day across a whole horizon (ADR-040). */
    val multiDayPlanner: com.chrona.app.domain.scheduling.MultiDayPlanner =
        DeterministicMultiDayPlanner(scheduleEngine)

    val proposeRecovery: ProposeRecoveryUseCase = DeterministicRecoveryPlanner(scheduleEngine)
    val applyRecovery: ApplyRecoveryUseCase =
        DefaultApplyRecoveryUseCase(scheduleRevisionRepository) { timeSource.now() }
    val undoSchedule: UndoScheduleRevisionUseCase =
        DefaultUndoScheduleRevisionUseCase(scheduleRevisionRepository) { timeSource.now() }

    /** Settings storage (CH-DATA-003). DataStore, not Room: new toggles need no migration. */
    val preferencesRepository: PreferencesRepository =
        DataStorePreferencesRepository(context.applicationContext.settingsDataStore)

    /**
     * Life simulation storage and engine (ADR-040).
     *
     * Simulation tasks are ordinary rows tagged with the reserved simulation label, so they obey
     * the same persistence and history rules as real tasks. Only `RoomSimulationStore.reset` can
     * remove them, and it is scoped to this run.
     */
    val simulationStore: RoomSimulationStore = RoomSimulationStore(database)
    val simulationEngine: LifeSimulationEngine = LifeSimulationEngine(
        planner = multiDayPlanner,
        store = simulationStore,
        processingMode = processingModeName(),
    )

    /**
     * The honest processing-mode label.
     *
     * "AI-assisted" appears only when the cloud path is actually configured *and* the provider
     * was usable. A build with no cloud config reports the deterministic path, which is what
     * actually runs.
     */
    fun processingModeName(): String = when {
        BuildConfig.CLOUD_ENABLED && BuildConfig.CLOUD_FUNCTION_URL.isNotBlank() ->
            LifeSimulationEngine.PROCESSING_MODE_AI_ASSISTED
        BuildConfig.CLOUD_ENABLED -> LifeSimulationEngine.PROCESSING_MODE_LOCAL_PARSER
        else -> LifeSimulationEngine.PROCESSING_MODE_DETERMINISTIC
    }

    /** Reminder presentation history (NOTIFY-001). Deliberately separate from task state. */
    val reminderHistoryRepository: ReminderHistoryRepository =
        RoomReminderHistoryRepository(database)

    /** The one deterministic policy; pure, so it is also the unit-test seam. */
    val reminderPolicy: ReminderPolicy = DeterministicReminderPolicy()

    val evaluateReminders = EvaluateRemindersUseCase(
        taskRepository = taskRepository,
        scheduleRevisionRepository = scheduleRevisionRepository,
        reminderHistoryRepository = reminderHistoryRepository,
        preferencesRepository = preferencesRepository,
        timeSource = timeSource,
        policy = reminderPolicy,
    )
    val recordReminderHistory =
        RecordReminderHistoryUseCase(reminderHistoryRepository, idGenerator, timeSource)
    val applyReminderAction =
        ApplyReminderActionUseCase(lifecycle, reminderHistoryRepository, idGenerator, timeSource)

    /**
     * Long-lived scope for actions started from system callbacks (overlay taps) that have no
     * lifecycle of their own. A supervisor so one failure never tears down the scope.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val notificationPresenter: ReminderPresenter =
        NotificationPresenter(context.applicationContext)

    val overlayPresenter: ReminderPresenter =
        OverlayPresenter(context.applicationContext) { action, card ->
            card.tasks.firstOrNull()?.let { primary ->
                applicationScope.launch {
                    applyReminderAction(primary.id, action, ReminderSurfaceType.OVERLAY, card.cardId)
                }
            }
        }

    /** Evaluate → present → record: the single delivery path shared by the worker and the UI. */
    val deliverReminders = DeliverRemindersUseCase(
        evaluate = evaluateReminders,
        record = recordReminderHistory,
        overlayPresenter = overlayPresenter,
        notificationPresenter = notificationPresenter,
    )

    /**
     * Task understanding: the deterministic parser by default, wrapped by the optional cloud
     * provider only when a build supplies a function URL.
     *
     * Cloud is an enrichment, not a dependency: it may improve an interpretation, but any
     * failure falls back to the deterministic parser, so the capture flow is never blocked. The
     * device calls Chrona's own Edge Function (which owns the provider keys) and never an AI
     * provider directly; no task text leaves the device unless the owner configures it.
     */
    val taskUnderstanding: TaskUnderstandingProvider = buildTaskUnderstanding()

    private fun buildTaskUnderstanding(): TaskUnderstandingProvider {
        val local = DeterministicTaskIntentParser()
        val config = CloudConfig(
            enabled = BuildConfig.CLOUD_ENABLED,
            functionUrl = BuildConfig.CLOUD_FUNCTION_URL,
            anonKey = BuildConfig.CLOUD_ANON_KEY,
        )
        return if (config.isUsable) {
            CloudTaskUnderstandingProvider(UrlConnectionHttpTransport(), config, local)
        } else {
            local
        }
    }

    /**
     * Parser configuration taken from the user's day hours.
     *
     * Kept as defaults: the parser only needs them to resolve relative phrases like "this
     * evening", and reading them per request would mean a suspending read on the capture path
     * for a value that changes a handful of times a year.
     */
    val parsePreferences: ParsePreferences = ParsePreferences()

    /** On-device capture only; there is no second recogniser to fall back to. */
    val speechRecognition: SpeechRecognitionAdapter = AndroidSpeechRecognitionAdapter(context)

    val createTaskFromIntent = CreateTaskFromIntentUseCase(createTask)

    /**
     * Builds the hard-dependency graph from stored relationships (P05).
     *
     * The board, editor and scheduler all read the direction from [RelationGraph], so a link
     * means the same thing everywhere.
     */
    fun relationGraph(
        relationships: List<com.chrona.app.domain.model.TaskRelationship>,
    ): RelationGraph = RelationGraph(relationships)

    private companion object {
        fun buildDatabase(context: Context): ChronaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChronaDatabase::class.java,
                ChronaDatabase.FILE_NAME,
            )
                // Purely additive migrations; existing user data is preserved (AT-031).
                .addMigrations(
                    ChronaDatabase.MIGRATION_1_2,
                    ChronaDatabase.MIGRATION_2_3,
                    ChronaDatabase.MIGRATION_3_4,
                )
                // No fallbackToDestructiveMigration(): losing real task data silently is worse
                // than failing loudly. Every version bump needs an explicit migration + test.
                .build()
    }
}

/** Wall-clock reference time. The only place production code reads "now". */
class SystemTimeSource(
    private val zoneProvider: () -> java.time.ZoneId = { java.time.ZoneId.systemDefault() },
) : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): java.time.ZoneId = zoneProvider()
}

/** Random opaque IDs. Stable identity does not require the ID to be guessable or ordered. */
class UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

private typealias Instant = java.time.Instant