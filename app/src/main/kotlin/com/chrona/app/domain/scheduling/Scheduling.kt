package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Everything the deterministic scheduler is allowed to see.
 *
 * The engine is a pure function of this value: same input, same proposal. Environment-dependent
 * values (`now`, day boundaries, buffer) are injected rather than read from a clock (CH-SCHED-001).
 */
data class ScheduleInput(
    val tasks: List<Task>,
    val fixedCommitments: List<FixedCommitment> = emptyList(),
    val dayStart: ZonedDateTime,
    val dayEnd: ZonedDateTime,
    val relationships: List<TaskRelationship> = emptyList(),
    /** Injected reference time; placement never precedes it (late start / late wake-up). */
    val now: ZonedDateTime? = null,
    /**
     * The user's real availability for this date, as absolute ranges.
     *
     * Consulted only when [restrictToAvailability] is true, which is the default *when at least
     * one window is given*. An explicit empty list together with `restrictToAvailability = true`
     * is how "this day has no capacity at all" is expressed — without it, an empty list would be
     * indistinguishable from "no availability configured", which means the opposite thing.
     *
     * Availability governs *flexible* work only. A lecture at 09:00 is kept whatever the user
     * says their day looks like.
     */
    val availabilityWindows: List<TimeRange> = emptyList(),

    /**
     * When true, flexible work is confined to [availabilityWindows] even when that list is empty.
     *
     * Defaults to "restrict whenever windows were supplied", so a caller that passes windows gets
     * the obvious behaviour and a caller with none keeps the historical whole-horizon default.
     */
    val restrictToAvailability: Boolean = availabilityWindows.isNotEmpty(),
    /** Recovery buffer kept around fixed commitments. */
    val buffer: Duration = Duration.ofMinutes(10),
    /** Placements from the currently applied revision, used to describe what moved. */
    val previousPlacements: List<SchedulePlacement> = emptyList(),
    /** The applied revision this input was read at; recorded in the proposal basis. */
    val basedOnRevisionId: RevisionId? = null,
    val policyVersion: String = "p04-v2",
)

/** A manually entered fixed commitment the scheduler must work around. Never moved by recovery. */
data class FixedCommitment(
    val id: String,
    val title: String,
    val range: TimeRange,
)

/** One scheduled block. A split task has one placement per session. */
data class SchedulePlacement(
    val taskId: TaskId,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val sessionIndex: Int = 0,
)

/** Why a task did not make it onto the plan. Always paired with a plain-language explanation. */
enum class UnplacedReason {
    NO_AVAILABLE_WINDOW,
    DEADLINE_INFEASIBLE,
    DEPENDENCY_INCOMPLETE,
    FIXED_CONFLICT,
    MIN_SESSION_UNAVAILABLE,
    INVALID_DURATION,
    USER_CHOICE_REQUIRED,

    /**
     * The task is requested for a different local day, so it is not today's problem.
     *
     * Distinct from [NO_AVAILABLE_WINDOW] and [DEADLINE_INFEASIBLE] on purpose: a task for
     * tomorrow is not a capacity failure and must never make the day look overloaded. It is
     * still listed so nothing is silently dropped.
     */
    OUTSIDE_HORIZON,
}

data class UnplacedTask(
    val taskId: TaskId,
    val reason: UnplacedReason,
    val explanation: String = "",
)

/** Two things that cannot both keep their time. */
data class ScheduleConflict(
    val taskId: TaskId,
    val otherId: TaskId,
    val explanation: String = "",
)

/** A task whose time changed between the current revision and the proposal. */
data class ScheduleChange(
    val taskId: TaskId,
    val fromStart: ZonedDateTime?,
    val toStart: ZonedDateTime?,
    val explanation: String = "",
)

/** A stated interpretation the user may disagree with. Never silent. */
data class ScheduleAssumption(val text: String)

/**
 * What the proposal was computed from: the current revision and the exact task versions read.
 *
 * Apply rechecks both, so a proposal cannot overwrite a task edited after the preview (AT-016).
 */
data class ScheduleBasis(
    val revisionId: RevisionId?,
    val taskVersions: Map<TaskId, Long>,
)

/**
 * A previewable, explainable plan. Persistent data is untouched until it is applied.
 *
 * [placements] and [unplaced] together always represent every schedulable task, so nothing is
 * ever silently dropped.
 */
data class ScheduleProposal(
    val proposalId: String,
    val basis: ScheduleBasis,
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,
    val conflicts: List<ScheduleConflict>,
    val changes: List<ScheduleChange>,
    val assumptions: List<ScheduleAssumption>,
    val overloaded: Boolean,
    val explanation: String,
    val policyVersion: String,
)

interface ScheduleEngine {
    fun propose(input: ScheduleInput): ScheduleProposal
}

/** An applied plan, kept for undo and for describing movement. */
data class ScheduleRevision(
    val id: RevisionId,
    val basedOnRevisionId: RevisionId?,
    val reasonCode: String,
    val appliedAt: Instant,
    val placements: List<SchedulePlacement>,
)
