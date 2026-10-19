package com.chrona.app.ui.schedule

import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import com.chrona.app.usecase.BuildDayProposalUseCase
import com.chrona.app.usecase.DayProposalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Schedule state holder (UI-001, SCHED-001..003).
 *
 * The claim that matters: building a preview never writes anything, and only an explicit apply
 * changes stored state. The horizon assertions are the regression tests for the midnight-planner
 * defect — before the fix the screen planned midnight-to-midnight whatever the user had asked for.
 */
class ScheduleViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val base: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val now: Instant = base.toInstant()

    private val repo = FakeRevisionRepository()

    private class FakeRevisionRepository : com.chrona.app.domain.recovery.ScheduleRevisionRepository {
        var revision: ScheduleRevision? = null
        var applyCount = 0
        var failApply = false

        override suspend fun current(): Outcome<ScheduleRevision?> = Outcome.Success(revision)

        override suspend fun history(limit: Int): Outcome<List<ScheduleRevision>> =
            Outcome.Success(listOfNotNull(revision))

        override suspend fun apply(
            proposal: ScheduleProposal,
            expectedRevision: RevisionId?,
            reasonCode: String,
            appliedAt: Instant,
        ): Outcome<RevisionId> {
            if (failApply) return Outcome.Failed(Failure.Conflict("The plan changed."))
            if (revision?.id != expectedRevision) return Outcome.Failed(Failure.Conflict("stale"))
            val id = RevisionId("rev-${applyCount + 1}")
            revision = ScheduleRevision(id, expectedRevision, reasonCode, appliedAt, proposal.placements)
            applyCount++
            return Outcome.Success(id)
        }

        override suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit> {
            if (revision?.id != revisionId) return Outcome.Failed(Failure.NotFound)
            revision = null
            return Outcome.Success(Unit)
        }
    }

    private fun task(id: String = "t") = Task(
        id = TaskId(id),
        title = "Read",
        priority = Priority.IMPORTANT,
        estimatedDuration = Duration.ofMinutes(60),
        createdAt = now,
        updatedAt = now,
    )

    /** The shared builder, with the same collaborators production wires. */
    private fun builder(
        tasks: Outcome<List<Task>> = Outcome.Success(listOf(task())),
        preferences: UserPreferences = UserPreferences(),
    ): suspend () -> Outcome<DayProposalResult> = {
        BuildDayProposalUseCase(
            loadTasks = { tasks },
            loadCommitments = { _: ZonedDateTime, _: ZonedDateTime -> Outcome.Success(emptyList<CalendarEvent>()) },
            loadRelationships = { Outcome.Success(emptyList<TaskRelationship>()) },
            loadPreferences = { Outcome.Success(preferences) },
            loadRevision = { repo.current() },
            engine = DeterministicScheduleEngine(),
            now = { base },
            zone = zone,
        ).invoke()
    }

    private fun viewModel(
        tasks: Outcome<List<Task>> = Outcome.Success(listOf(task())),
        preferences: UserPreferences = UserPreferences(),
    ) = ScheduleViewModel(
        buildProposal = builder(tasks, preferences),
        apply = { proposal, expected, reason ->
            when (val result = repo.apply(proposal, expected, reason, now)) {
                is Outcome.Success -> com.chrona.app.domain.recovery.ApplyRecoveryResult(
                    true, result.value, "Schedule applied.",
                )

                is Outcome.Failed -> com.chrona.app.domain.recovery.ApplyRecoveryResult(
                    false, null, "The schedule could not be applied.",
                )
            }
        },
        undo = { id ->
            when (repo.undo(id, now)) {
                is Outcome.Success -> com.chrona.app.domain.recovery.UndoResult(true, "Schedule undone.")
                is Outcome.Failed -> com.chrona.app.domain.recovery.UndoResult(false, "Nothing to undo.")
            }
        },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `building a preview writes nothing`() {
        val vm = viewModel()

        assertNotNull(vm.state.value.proposal)
        assertFalse(vm.state.value.isLoading)
        assertEquals(0, repo.applyCount)
        assertNull(repo.revision)
    }

    @Test
    fun `apply records a revision and becomes the current one`() {
        val vm = viewModel()

        vm.applyProposal()

        assertEquals(1, repo.applyCount)
        assertNotNull(vm.state.value.currentRevisionId)
        assertNotNull(repo.revision)
    }

    @Test
    fun `undo clears the current revision`() {
        val vm = viewModel()
        vm.applyProposal()

        vm.undoLast()

        assertNull(repo.revision)
        assertNull(vm.state.value.currentRevisionId)
    }

    @Test
    fun `a load failure surfaces a message and no proposal`() {
        val vm = viewModel(tasks = Outcome.Failed(Failure.StorageFailure("disk")))

        assertNull(vm.state.value.proposal)
        assertTrue(vm.state.value.message?.isNotBlank() == true)
    }

    @Test
    fun `the planning horizon follows the user's configured day hours`() {
        val vm = viewModel(
            preferences = UserPreferences(dayStart = LocalTime.of(9, 0), dayEnd = LocalTime.of(20, 0)),
        )

        val horizon = vm.state.value.horizon
        assertNotNull(horizon)
        assertEquals(LocalTime.of(9, 0), horizon!!.start.toLocalTime())
        assertEquals(LocalTime.of(20, 0), horizon.end.toLocalTime())
        assertEquals(1, horizon.windows.size)
    }

    @Test
    fun `flexible work is never placed outside the configured availability`() {
        val vm = viewModel(
            preferences = UserPreferences(
                availabilityWindows = listOf(
                    com.chrona.app.domain.model.TimeWindow(LocalTime.of(12, 0), LocalTime.of(13, 0)),
                ),
            ),
        )

        val placement = vm.state.value.proposal!!.placements.single()
        assertEquals(LocalTime.of(12, 0), placement.start.toLocalTime())
        assertEquals(LocalTime.of(13, 0), placement.end.toLocalTime())
    }

    /** An overnight day is a real configuration, not a bug: it must wrap onto the next date. */
    @Test
    fun `a day that wraps past midnight ends on the following date`() {
        val vm = viewModel(
            preferences = UserPreferences(dayStart = LocalTime.of(20, 0), dayEnd = LocalTime.of(2, 0)),
        )

        val horizon = vm.state.value.horizon!!
        assertEquals(LocalTime.of(20, 0), horizon.start.toLocalTime())
        assertEquals(LocalTime.of(2, 0), horizon.end.toLocalTime())
        assertEquals(horizon.start.toLocalDate().plusDays(1), horizon.end.toLocalDate())
    }

    @Test
    fun `an empty task pool still produces a plan rather than a blank screen`() {
        val vm = viewModel(tasks = Outcome.Success(emptyList()))

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.proposal)
        assertEquals(0, vm.state.value.allTasks.size)
    }

    @Test
    fun `a rejected apply leaves the stored plan untouched and explains why`() {
        val vm = viewModel()
        repo.failApply = true

        vm.applyProposal()

        assertNull(repo.revision)
        assertEquals(0, repo.applyCount)
        assertTrue(vm.state.value.message?.isNotBlank() == true)
    }

    @Test
    fun `fixed commitments are kept exactly where the user put them`() {
        val fixedStart = base.withHour(9).withMinute(0)
        val fixed = task("lecture").copy(
            flexibility = com.chrona.app.domain.model.Flexibility.FIXED,
            fixedStart = fixedStart,
            fixedEnd = fixedStart.plusHours(1),
        )
        val vm = viewModel(tasks = Outcome.Success(listOf(fixed)))

        val placement = vm.state.value.proposal!!.placements.single()
        assertEquals(fixedStart.toInstant(), placement.start.toInstant())
        assertTrue(vm.state.value.fixedTaskIds.contains(fixed.id))
    }
}
