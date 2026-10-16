package com.chrona.app.domain.recovery

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.scheduling.DeterministicScheduleEngine
import com.chrona.app.domain.scheduling.FixedCommitment
import com.chrona.app.domain.scheduling.ScheduleBasis
import com.chrona.app.domain.scheduling.ScheduleInput
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** In-memory stand-in for the Room repository; mirrors its stale-revision guarantee. */
private class FakeRevisionRepository : ScheduleRevisionRepository {
    var revision: ScheduleRevision? = null
    var applyFailure: Failure? = null
    var undoFailure: Failure? = null
    var applyCount = 0

    override suspend fun current(): Outcome<ScheduleRevision?> = Outcome.Success(revision)

    override suspend fun apply(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId> {
        applyFailure?.let {
            applyFailure = null
            return Outcome.Failed(it)
        }
        val currentId = revision?.id
        if (currentId != expectedRevision) {
            return Outcome.Failed(Failure.Conflict("The plan changed since this preview."))
        }
        val id = RevisionId("rev-${applyCount + 1}")
        revision = ScheduleRevision(id, expectedRevision, reasonCode, appliedAt, proposal.placements)
        applyCount++
        return Outcome.Success(id)
    }

    override suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit> {
        undoFailure?.let {
            undoFailure = null
            return Outcome.Failed(it)
        }
        if (revision?.id != revisionId) return Outcome.Failed(Failure.NotFound)
        revision = null
        return Outcome.Success(Unit)
    }
}

class RecoveryUseCasesTest {
    private val zone = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val now: Instant = base.toInstant()

    private fun proposal(revisionId: RevisionId? = null) = ScheduleProposal(
        proposalId = "p1",
        basis = ScheduleBasis(revisionId, mapOf(TaskId("t") to 1L)),
        placements = listOf(SchedulePlacement(TaskId("t"), base, base.plusHours(1), 0)),
        unplaced = emptyList(),
        conflicts = emptyList(),
        changes = emptyList(),
        assumptions = emptyList(),
        overloaded = false,
        explanation = "",
        policyVersion = "p04-v1",
    )

    @Test
    fun `apply succeeds and records the revision`() = runBlocking {
        val repo = FakeRevisionRepository()
        val useCase = DefaultApplyRecoveryUseCase(repo) { now }

        val result = useCase(proposal(), expectedRevision = null)

        assertTrue(result.success)
        assertEquals(RevisionId("rev-1"), result.appliedRevisionId)
        assertEquals(RevisionId("rev-1"), repo.revision?.id)
    }

    @Test
    fun `stale proposal is refused with an explanation and no write`() = runBlocking {
        val repo = FakeRevisionRepository()
        repo.revision = ScheduleRevision(RevisionId("rev-existing"), null, ScheduleRevisionReason.APPLIED, now, emptyList())
        val useCase = DefaultApplyRecoveryUseCase(repo) { now }

        val result = useCase(proposal(), expectedRevision = null)

        assertFalse(result.success)
        assertNull(result.appliedRevisionId)
        assertTrue(result.reason.contains("preview", ignoreCase = true))
        assertEquals(RevisionId("rev-existing"), repo.revision?.id)
    }

    @Test
    fun `storage failure surfaces plainly and changes nothing`() = runBlocking {
        val repo = FakeRevisionRepository()
        repo.applyFailure = Failure.StorageFailure("disk")
        val useCase = DefaultApplyRecoveryUseCase(repo) { now }

        val result = useCase(proposal(), expectedRevision = null)

        assertFalse(result.success)
        assertTrue(result.reason.contains("Nothing was changed"))
    }

    @Test
    fun `undo succeeds for the current revision`() = runBlocking {
        val repo = FakeRevisionRepository()
        repo.revision = ScheduleRevision(RevisionId("rev-1"), null, ScheduleRevisionReason.APPLIED, now, emptyList())
        val useCase = DefaultUndoScheduleRevisionUseCase(repo) { now }

        val result = useCase(RevisionId("rev-1"))

        assertTrue(result.success)
        assertNull(repo.revision)
    }

    @Test
    fun `undo of something no longer there explains itself`() = runBlocking {
        val repo = FakeRevisionRepository()
        val useCase = DefaultUndoScheduleRevisionUseCase(repo) { now }

        val result = useCase(RevisionId("missing"))

        assertFalse(result.success)
        assertTrue(result.reason.contains("nothing left to undo", ignoreCase = true))
    }

    @Test
    fun `planner runs the same engine and carries the current revision id`() = runBlocking {
        val planner = DeterministicRecoveryPlanner(DeterministicScheduleEngine())
        val task = com.chrona.app.domain.model.Task(
            id = TaskId("t"),
            title = "t",
            estimatedDuration = Duration.ofMinutes(30),
            createdAt = now,
            updatedAt = now,
        )
        val input = ScheduleInput(listOf(task), emptyList(), base, base.plusHours(4))
        val current = RevisionId("rev-7")

        val result = planner(RecoveryRequest(RecoveryTrigger.NEW_TASK, input, current))

        assertEquals(current, result.basis.revisionId)
        assertEquals(1, result.placements.size)
    }

    @Test
    fun `a commitment overrun moves affected work later in the preview`() = runBlocking {
        val planner = DeterministicRecoveryPlanner(DeterministicScheduleEngine())
        val task = com.chrona.app.domain.model.Task(
            id = TaskId("t"),
            title = "t",
            estimatedDuration = Duration.ofMinutes(60),
            createdAt = now,
            updatedAt = now,
        )
        val overrun = FixedCommitment("class", "Class", TimeRange(base, base.plusHours(1)))
        val input = ScheduleInput(
            tasks = listOf(task),
            fixedCommitments = listOf(overrun),
            dayStart = base,
            dayEnd = base.plusHours(4),
            previousPlacements = listOf(SchedulePlacement(TaskId("t"), base, base.plusHours(1), 0)),
            buffer = Duration.ZERO,
        )

        val result = planner(RecoveryRequest(RecoveryTrigger.COMMITMENT_OVERRUN, input, RevisionId("rev-1")))

        assertEquals(base.plusHours(1), result.placements.single().start)
        assertTrue(result.changes.any { it.taskId == TaskId("t") })
    }
}
