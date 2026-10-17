package com.chrona.app.usecase

import com.chrona.app.data.repository.PreferencesRepository
import com.chrona.app.data.repository.ReminderHistoryRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.domain.recovery.ScheduleRevisionRepository
import com.chrona.app.domain.reminder.DeterministicReminderPolicy
import com.chrona.app.domain.reminder.ReminderAction
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderDecision
import com.chrona.app.domain.reminder.ReminderDecisionReason
import com.chrona.app.domain.reminder.ReminderOutcome
import com.chrona.app.domain.reminder.ReminderPresentation
import com.chrona.app.domain.reminder.ReminderPresenter
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** In-memory reminder history. The card-count query mirrors the DAO's DISTINCT batch_group rule. */
class FakeReminderHistoryRepository : ReminderHistoryRepository {
    val rows = mutableListOf<ReminderPresentation>()

    override suspend fun record(entry: ReminderPresentation): Outcome<Unit> {
        rows.add(entry)
        return Outcome.Success(Unit)
    }

    override suspend fun recordAll(entries: List<ReminderPresentation>): Outcome<Unit> {
        rows.addAll(entries)
        return Outcome.Success(Unit)
    }

    override suspend fun historyFor(taskIds: Set<TaskId>): Outcome<List<ReminderPresentation>> =
        Outcome.Success(rows.filter { it.taskId in taskIds })

    override suspend fun nonUrgentSurfacedOn(localDate: LocalDate): Outcome<Int> = Outcome.Success(
        rows
            .filter {
                it.outcome == ReminderOutcome.SHOWN &&
                    it.priority == Priority.LOW &&
                    it.localDate == localDate
            }
            .mapNotNull { it.batchGroup }
            .distinct()
            .size,
    )

    override suspend fun recent(limit: Int): Outcome<List<ReminderPresentation>> =
        Outcome.Success(rows.takeLast(limit))
}

class FakePreferencesRepository(
    var preferences: UserPreferences = UserPreferences(),
    var fail: Boolean = false,
) : PreferencesRepository {
    override suspend fun load(): Outcome<UserPreferences> =
        if (fail) Outcome.Failed(Failure.StorageFailure("unreadable")) else Outcome.Success(preferences)

    override suspend fun save(preferences: UserPreferences): Outcome<Unit> {
        this.preferences = preferences
        return Outcome.Success(Unit)
    }
}

/** No plan applied yet; reminder evaluation treats a missing revision as "no placements". */
class StubScheduleRevisionRepository : ScheduleRevisionRepository {
    override suspend fun current(): Outcome<ScheduleRevision?> = Outcome.Success(null)

    override suspend fun apply(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId> = Outcome.Success(RevisionId("revision"))

    override suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit> =
        Outcome.Success(Unit)
}

class FakeReminderPresenter(
    override val surface: ReminderSurfaceType,
    private val available: Boolean = true,
    /** Simulates a surface that is available but then fails to show (returns null). */
    private val canPresent: Boolean = true,
) : ReminderPresenter {
    val presented = mutableListOf<ReminderCard>()

    override suspend fun isAvailable(): Boolean = available

    override suspend fun present(card: ReminderCard): ReminderSurfaceType? {
        if (!available || !canPresent) return null
        presented.add(card)
        return surface
    }
}

class ReminderUseCasesTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private val repository = FakeTaskRepository { Fixtures.start }
    private val history = FakeReminderHistoryRepository()
    private val preferences = FakePreferencesRepository()
    private val evaluate = EvaluateRemindersUseCase(
        taskRepository = repository,
        scheduleRevisionRepository = StubScheduleRevisionRepository(),
        reminderHistoryRepository = history,
        preferencesRepository = preferences,
        timeSource = Fixtures.FixedTimeSource(),
        policy = DeterministicReminderPolicy(),
    )

    private fun seed(
        title: String,
        priority: Priority = Priority.IMPORTANT,
        projectLabel: String? = null,
    ): Task {
        val task = Task(
            id = TaskId("t-$title"),
            title = title,
            priority = priority,
            requestedDate = today,
            projectLabel = projectLabel,
            createdAt = Fixtures.start,
            updatedAt = Fixtures.start,
        )
        repository.rows[task.id.value] = task
        return task
    }

    @Test
    fun `a due task becomes an actionable notification decision`() = runTest {
        val task = seed("Study")

        val evaluation = (evaluate(notificationAvailable = true, overlayAvailable = false)
            as Outcome.Success).value

        val decision = evaluation.actionable.single()
        assertEquals(listOf(task.id), decision.tasks)
        assertEquals(ReminderAction.NOTIFICATION, decision.action)
    }

    @Test
    fun `the overlay is preferred only when it is available`() = runTest {
        seed("Study")

        val evaluation = (evaluate(notificationAvailable = true, overlayAvailable = true)
            as Outcome.Success).value

        assertEquals(ReminderAction.OVERLAY_BEST_EFFORT, evaluation.actionable.single().action)
    }

    @Test
    fun `with no platform surface the card falls back to in-app`() = runTest {
        seed("Study")

        val evaluation = (evaluate(notificationAvailable = false, overlayAvailable = false)
            as Outcome.Success).value

        assertEquals(ReminderAction.IN_APP, evaluation.actionable.single().action)
    }

    @Test
    fun `unreadable settings fall back to defaults instead of failing evaluation`() = runTest {
        preferences.fail = true
        seed("Study")

        val evaluation = evaluate(notificationAvailable = true, overlayAvailable = false)

        assertTrue(evaluation is Outcome.Success)
        assertEquals(1, (evaluation as Outcome.Success).value.actionable.size)
    }

    @Test
    fun `a task that is not due yields no actionable decision`() = runTest {
        val evaluation = (evaluate(notificationAvailable = true, overlayAvailable = false)
            as Outcome.Success).value

        assertTrue(evaluation.actionable.isEmpty())
    }
}

class RecordReminderHistoryUseCaseTest {

    private val history = FakeReminderHistoryRepository()
    private val record = RecordReminderHistoryUseCase(
        history,
        Fixtures.SequentialIdGenerator(),
        Fixtures.FixedTimeSource(),
    )

    private fun card(task: Task): ReminderCard = ReminderCard(
        cardId = "reminder:${task.id.value}",
        decision = ReminderDecision(
            action = ReminderAction.NOTIFICATION,
            tasks = listOf(task.id),
            reason = ReminderDecisionReason.ELIGIBLE,
            nextEligibleAt = null,
        ),
        tasks = listOf(task),
    )

    @Test
    fun `a surfaced card writes one SHOWN row per task tagged with the card id`() = runTest {
        val task = Task(TaskId("t-1"), "Study", createdAt = Fixtures.start, updatedAt = Fixtures.start)

        record.surfaced(card(task), ReminderSurfaceType.NOTIFICATION)

        val row = history.rows.single()
        assertEquals(ReminderOutcome.SHOWN, row.outcome)
        assertEquals(ReminderSurfaceType.NOTIFICATION, row.surfaceType)
        assertEquals("reminder:t-1", row.batchGroup)
    }

    @Test
    fun `a suppressed decision writes a SUPPRESSED row with no surface`() = runTest {
        val task = Task(TaskId("t-1"), "Study", createdAt = Fixtures.start, updatedAt = Fixtures.start)
        val evaluation = ReminderEvaluation(
            decisions = listOf(
                ReminderDecision(
                    action = ReminderAction.SUPPRESS,
                    tasks = listOf(task.id),
                    reason = ReminderDecisionReason.QUIET_HOURS,
                    nextEligibleAt = null,
                ),
            ),
            tasksById = mapOf(task.id to task),
            localDate = LocalDate.of(2026, 10, 15),
        )

        record.suppressed(evaluation)

        val row = history.rows.single()
        assertEquals(ReminderOutcome.SUPPRESSED, row.outcome)
        assertEquals(ReminderSurfaceType.NONE, row.surfaceType)
        assertNull(row.surfacedAt)
    }
}

class ApplyReminderActionUseCaseTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val history = FakeReminderHistoryRepository()
    private val lifecycle = TaskLifecycleUseCases(repository, Fixtures.FixedTimeSource())
    private val apply = ApplyReminderActionUseCase(
        lifecycle,
        history,
        Fixtures.SequentialIdGenerator(),
        Fixtures.FixedTimeSource(),
    )

    private fun seed(): Task {
        val task = Task(
            id = TaskId("t-1"),
            title = "Study",
            requestedDate = LocalDate.of(2026, 10, 15),
            createdAt = Fixtures.start,
            updatedAt = Fixtures.start,
        )
        repository.rows[task.id.value] = task
        return task
    }

    @Test
    fun `DONE completes the task and records the action`() = runTest {
        val task = seed()

        val result = apply(task.id, TaskAction.DONE, ReminderSurfaceType.NOTIFICATION)

        val updated = (result as Outcome.Success).value
        assertEquals(com.chrona.app.domain.model.TaskStatus.COMPLETED, updated?.status)
        assertEquals(ReminderOutcome.DONE, history.rows.last().outcome)
    }

    @Test
    fun `LATER postpones the task`() = runTest {
        val task = seed()

        apply(task.id, TaskAction.LATER, ReminderSurfaceType.IN_APP)

        assertEquals(
            com.chrona.app.domain.model.TaskStatus.POSTPONED,
            repository.rows[task.id.value]?.status,
        )
        assertEquals(ReminderOutcome.LATER, history.rows.last().outcome)
    }

    @Test
    fun `DISMISS records history and never changes the task`() = runTest {
        val task = seed()

        val result = apply(task.id, TaskAction.DISMISS, ReminderSurfaceType.NOTIFICATION)

        assertNull((result as Outcome.Success).value)
        assertEquals(
            com.chrona.app.domain.model.TaskStatus.INBOX,
            repository.rows[task.id.value]?.status,
        )
        assertEquals(ReminderOutcome.DISMISSED, history.rows.single().outcome)
    }
}

class DeliverRemindersUseCaseTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private val repository = FakeTaskRepository { Fixtures.start }
    private val history = FakeReminderHistoryRepository()
    private val record = RecordReminderHistoryUseCase(
        history,
        Fixtures.SequentialIdGenerator(),
        Fixtures.FixedTimeSource(),
    )
    private val evaluate = EvaluateRemindersUseCase(
        taskRepository = repository,
        scheduleRevisionRepository = StubScheduleRevisionRepository(),
        reminderHistoryRepository = history,
        preferencesRepository = FakePreferencesRepository(),
        timeSource = Fixtures.FixedTimeSource(),
        policy = DeterministicReminderPolicy(),
    )

    private fun seed() {
        val task = Task(
            id = TaskId("t-1"),
            title = "Study",
            priority = Priority.IMPORTANT,
            requestedDate = today,
            createdAt = Fixtures.start,
            updatedAt = Fixtures.start,
        )
        repository.rows[task.id.value] = task
    }

    @Test
    fun `the dependable notification is used when it is available`() = runTest {
        seed()
        val notification = FakeReminderPresenter(ReminderSurfaceType.NOTIFICATION, available = true)
        val overlay = FakeReminderPresenter(ReminderSurfaceType.OVERLAY, available = false)
        val deliver = DeliverRemindersUseCase(evaluate, record, overlay, notification)

        val deliveries = (deliver() as Outcome.Success).value

        assertEquals(ReminderSurfaceType.NOTIFICATION, deliveries.single().surface)
        assertEquals(ReminderOutcome.SHOWN, history.rows.single().outcome)
    }

    @Test
    fun `with no platform surface nothing is delivered or recorded for the in-app card`() = runTest {
        seed()
        val notification = FakeReminderPresenter(ReminderSurfaceType.NOTIFICATION, available = false)
        val overlay = FakeReminderPresenter(ReminderSurfaceType.OVERLAY, available = false)
        val deliver = DeliverRemindersUseCase(evaluate, record, overlay, notification)

        val deliveries = (deliver() as Outcome.Success).value

        assertTrue(deliveries.isEmpty())
        assertTrue(history.rows.none { it.outcome == ReminderOutcome.SHOWN })
    }

    @Test
    fun `an unavailable overlay falls back to the notification`() = runTest {
        seed()
        val notification = FakeReminderPresenter(ReminderSurfaceType.NOTIFICATION, available = true)
        // Overlay reports itself available but fails to show (returns null), as on a restricted OEM.
        val overlay = FakeReminderPresenter(
            ReminderSurfaceType.OVERLAY,
            available = true,
            canPresent = false,
        )
        val deliver = DeliverRemindersUseCase(evaluate, record, overlay, notification)

        val deliveries = (deliver() as Outcome.Success).value

        assertEquals(ReminderSurfaceType.NOTIFICATION, deliveries.single().surface)
    }

    @Test
    fun `card ids are stable and derived from the task set`() = runTest {
        val a = ReminderDecision(ReminderAction.IN_APP, listOf(TaskId("a")), ReminderDecisionReason.ELIGIBLE, null)
        val b = ReminderDecision(ReminderAction.IN_APP, listOf(TaskId("a")), ReminderDecisionReason.ELIGIBLE, null)
        val c = ReminderDecision(ReminderAction.IN_APP, listOf(TaskId("b")), ReminderDecisionReason.ELIGIBLE, null)

        assertEquals(reminderCardId(a), reminderCardId(b))
        assertNotEquals(reminderCardId(a), reminderCardId(c))
    }
}
