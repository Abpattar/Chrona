package com.chrona.app.domain.recovery

import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.scheduling.ScheduleInput
import com.chrona.app.domain.scheduling.ScheduleProposal

enum class RecoveryTrigger {
    LATE_START, COMMITMENT_OVERRUN, TASK_DELAYED, NEW_TASK, BREAK_REQUESTED, TASK_CANCELLED, AVAILABILITY_CHANGED
}

data class RecoveryRequest(
    val trigger: RecoveryTrigger,
    val input: ScheduleInput,
    val currentRevisionId: RevisionId?,
)

interface ProposeRecoveryUseCase {
    suspend operator fun invoke(request: RecoveryRequest): ScheduleProposal
}

data class ApplyRecoveryResult(
    val success: Boolean,
    val appliedRevisionId: RevisionId?,
    val reason: String = "",
)

interface ApplyRecoveryUseCase {
    suspend operator fun invoke(proposalId: String, expectedRevision: RevisionId?): ApplyRecoveryResult
}

data class UndoResult(
    val success: Boolean,
    val reason: String = "",
)

interface UndoScheduleRevisionUseCase {
    suspend operator fun invoke(revisionId: RevisionId): UndoResult
}
