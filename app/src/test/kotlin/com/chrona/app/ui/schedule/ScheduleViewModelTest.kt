package com.chrona.app.ui.schedule

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.recovery.ApplyRecoveryResult
import com.chrona.app.domain.recovery.ScheduleRevisionRepository
import com.chrona.app.domain.recovery.UndoResult
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Schedule state holder (UI-001, SCHED-001..003).
 *
 * The claim that matters: building a preview never writes anything, and only an explicit apply
 * changes stored state.
 */
class ScheduleViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val base: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val now: Instant = base.toInstant()

    private val repo = FakeRevisionRepository()

    private class FakeRevisionRepository : ScheduleRevisionRepository {
        var revision: ScheduleRevision? = null
        var applyCount = 0
        var failApply = false

        override suspend fun current(): Outcome<ScheduleRevision?> = Outcome.Success(revision)

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

    private fun viewModel(
        tasks: Outcome<List<Task>> = Outcome.Success(listOf(task())),
    ) = ScheduleViewModel(
        loadTasks = { tasks },
        loadCommitments = { _, _ -> Outcome.Success(emptyList()) },
        loadRelationships = { Outcome.Success(emptyList()) },
        currentRevision = { repo.current() },
        plan = { input -> DeterministicScheduleEngine().propose(input) },
        apply = { proposal, expected, reason ->
            when (val result = repo.apply(proposal, expected, reason, now)) {
                is Outcome.Success -> ApplyRecoveryResult(true, result.value, "Schedule applied.")
                is Outcome.Failed -> ApplyRecoveryResult(false, null, "The schedule could not be applied.")
            }
        },
        undo = { id ->
            when (repo.undo(id, now)) {
                is Outcome.Success -> UndoResult(true, "Schedule undone.")
                is Outcome.Failed -> UndoResult(false, "Nothing to undo.")
            }
        },
        now = { base },
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
}
