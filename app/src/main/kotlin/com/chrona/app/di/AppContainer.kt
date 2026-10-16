package com.chrona.app.di

import android.content.Context
import androidx.room.Room
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.EventRepository
import com.chrona.app.data.repository.RoomEventRepository
import com.chrona.app.data.repository.RoomRelationshipRepository
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.parse.DeterministicTaskIntentParser
import com.chrona.app.domain.parse.ParsePreferences
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.relation.RelationGraph
import com.chrona.app.platform.speech.AndroidSpeechRecognitionAdapter
import com.chrona.app.platform.speech.SpeechRecognitionAdapter
import com.chrona.app.usecase.CreateTaskFromIntentUseCase
import com.chrona.app.usecase.CreateTaskUseCase
import com.chrona.app.usecase.EventUseCases
import com.chrona.app.usecase.IdGenerator
import com.chrona.app.usecase.ListTasksUseCase
import com.chrona.app.usecase.TaskLifecycleUseCases
import com.chrona.app.usecase.TimeSource
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

    val createTask = CreateTaskUseCase(taskRepository, idGenerator, timeSource)
    val listTasks = ListTasksUseCase(taskRepository)
    val lifecycle = TaskLifecycleUseCases(taskRepository, timeSource)
    val events = EventUseCases(eventRepository, idGenerator, timeSource)

    /**
     * The deterministic parser. There is no provider switch: the interface exists so the parser
     * is a test seam and a replaceable boundary, not an invitation to add a remote provider
     * (SHARED_CONTRACT.md, "MVP includes a deterministic parser only").
     */
    val taskUnderstanding: TaskUnderstandingProvider = DeterministicTaskIntentParser()

    /**
     * Parser configuration taken from the user's day hours.
     *
     * `UserPreferences` has no storage yet (CH-DATA-003), so the defaults are used until the
     * settings screen persists real values in P06/P07.
     */
    val parsePreferences: ParsePreferences = ParsePreferences()

    /** On-device capture only; there is no second recogniser to fall back to. */
    val speechRecognition: SpeechRecognitionAdapter = AndroidSpeechRecognitionAdapter(context)

    val createTaskFromIntent = CreateTaskFromIntentUseCase(createTask)

    /** Placeholder for P05: builds the dependency graph from stored relationships. */
    fun relationGraph(): RelationGraph = RelationGraph()

    private companion object {
        fun buildDatabase(context: Context): ChronaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChronaDatabase::class.java,
                ChronaDatabase.FILE_NAME,
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