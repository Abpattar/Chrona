package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleInput(
    val tasks: List<Task>,
    val fixedCommitments: List<FixedCommitment>,
    val dayStart: ZonedDateTime,
    val dayEnd: ZonedDateTime,
    val buffer: Duration = Duration.ofMinutes(10),
    val policyVersion: String = "p04-v1",
)

data class FixedCommitment(
    val id: String,
    val title: String,
    val range: TimeRange,
)

data class SchedulePlacement(
    val taskId: TaskId,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val sessionIndex: Int = 0,
)

enum class UnplacedReason {
    NO_AVAILABLE_WINDOW,
    DEADLINE_INFEASIBLE,
    DEPENDENCY_INCOMPLETE,
    FIXED_CONFLICT,
    MIN_SESSION_UNAVAILABLE,
    INVALID_DURATION,
    USER_CHOICE_REQUIRED,
}

data class UnplacedTask(
    val taskId: TaskId,
    val reason: UnplacedReason,
    val explanation: String = "",
)

data class ScheduleConflict(
    val taskId: TaskId,
    val otherId: TaskId,
    val explanation: String = "",
)

data class ScheduleChange(
    val taskId: TaskId,
    val fromStart: ZonedDateTime?,
    val toStart: ZonedDateTime?,
    val explanation: String = "",
)

data class ScheduleAssumption(
    val text: String,
)

data class ScheduleProposal(
    val proposalId: String,
    val basedOnRevisionId: String?,
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,
    val conflicts: List<ScheduleConflict>,
    val changes: List<ScheduleChange>,
    val assumptions: List<ScheduleAssumption>,
    val explanation: String,
    val policyVersion: String,
)

interface ScheduleEngine {
    fun propose(input: ScheduleInput): ScheduleProposal
}
