package com.chrona.app.domain.recovery

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.scheduling.ScheduleEngine
import com.chrona.app.domain.scheduling.ScheduleInput
import com.chrona.app.domain.scheduling.ScheduleProposal
import java.time.Instant

/** What set recovery in motion. Purely descriptive; it never mutates a task by itself. */
enum class RecoveryTrigger {
    LATE_START,
    COMMITMENT_OVERRUN,
    TASK_DELAYED,
    NEW_TASK,
    BREAK_REQUESTED,
    TASK_CANCELLED,
    AVAILABILITY_CHANGED,
}

/**
 * A recovery request.
 *
 * [input] already carries the changed world (a new task, a moved commitment, an injected `now`);
 * the planner does not invent changes. [currentRevisionId] is what apply will recheck.
 */
data class RecoveryRequest(
    val trigger: RecoveryTrigger,
    val input: ScheduleInput,
    val currentRevisionId: RevisionId?,
)

/** Outcome of applying a proposal. Plain-language [reason] on failure; never a stack trace. */
data class ApplyRecoveryResult(
    val success: Boolean,
    val appliedRevisionId: RevisionId?,
    val reason: String = "",
)

/** Outcome of undoing a revision. Failure always explains and points at a safe next step. */
data class UndoResult(
    val success: Boolean,
    val reason: String = "",
)

interface ProposeRecoveryUseCase {
    suspend operator fun invoke(request: RecoveryRequest): ScheduleProposal
}

interface ApplyRecoveryUseCase {
    suspend operator fun invoke(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String = ScheduleRevisionReason.APPLIED,
    ): ApplyRecoveryResult
}

interface UndoScheduleRevisionUseCase {
    suspend operator fun invoke(revisionId: RevisionId): UndoResult
}

/**
 * Plans recovery by running the same deterministic engine over the changed input.
 *
 * There is deliberately no second algorithm: recovery and first planning must agree, or the
 * explanation shown before apply would not match what apply does.
 */
class DeterministicRecoveryPlanner(
    private val engine: ScheduleEngine,
) : ProposeRecoveryUseCase {

    override suspend fun invoke(request: RecoveryRequest): ScheduleProposal =
        engine.propose(request.input.copy(basedOnRevisionId = request.currentRevisionId))
}

/** Applies a previewed proposal, rechecking the revision and task versions in one transaction. */
class DefaultApplyRecoveryUseCase(
    private val repository: ScheduleRevisionRepository,
    private val now: () -> Instant,
) : ApplyRecoveryUseCase {

    override suspend fun invoke(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
    ): ApplyRecoveryResult =
        when (val result = repository.apply(proposal, expectedRevision, reasonCode, now())) {
            is Outcome.Success -> ApplyRecoveryResult(true, result.value, "Schedule applied.")
            is Outcome.Failed -> ApplyRecoveryResult(false, null, describe(result.failure))
        }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString("\n")
        Failure.NotFound -> "That task no longer exists."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not save the schedule. Nothing was changed."
        else -> "The schedule could not be applied. Nothing was changed."
    }
}

/** Undoes an applied revision when it is still current and safe. */
class DefaultUndoScheduleRevisionUseCase(
    private val repository: ScheduleRevisionRepository,
    private val now: () -> Instant,
) : UndoScheduleRevisionUseCase {

    override suspend fun invoke(revisionId: RevisionId): UndoResult =
        when (val result = repository.undo(revisionId, now())) {
            is Outcome.Success -> UndoResult(true, "Schedule undone.")
            is Outcome.Failed -> UndoResult(false, describe(result.failure))
        }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Conflict -> failure.message
        Failure.NotFound -> "There is nothing left to undo."
        is Failure.StorageFailure -> "Chrona could not undo the schedule. Nothing was changed."
        else -> "The schedule could not be undone."
    }
}
