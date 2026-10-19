package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship

/**
 * A day the multi-day planner is allowed to place work into.
 *
 * [isAvailable] exists because an empty [windows] list cannot carry both meanings: "no
 * availability configured, use the whole day" and "this day is entirely unavailable" are
 * opposites. Without the flag a day the user declared impossible would be treated as wide open
 * and quietly filled.
 */
data class PlanningDay(
    val date: java.time.LocalDate,
    val start: java.time.ZonedDateTime,
    val end: java.time.ZonedDateTime,
    /** Absolute capacity for flexible work. Empty with [isAvailable] means no capacity at all. */
    val windows: List<com.chrona.app.domain.model.TimeRange> = emptyList(),
    val isAvailable: Boolean = true,
)

/**
 * Everything a multi-day plan is computed from.
 *
 * The task pool is the whole world of work; the planner decides *which day* each item lands on.
 * There is no per-day input list, so a task that cannot fit today is genuinely carried forward
 * rather than silently dropped between two calls.
 */
data class MultiDayInput(
    val days: List<PlanningDay>,
    val tasks: List<Task>,
    val fixedCommitments: List<FixedCommitment> = emptyList(),
    val relationships: List<TaskRelationship> = emptyList(),
    val now: java.time.ZonedDateTime? = null,
    val buffer: java.time.Duration = java.time.Duration.ofMinutes(10),
    val previousPlacements: List<SchedulePlacement> = emptyList(),
    val basedOnRevisionId: RevisionId? = null,
    val policyVersion: String = DEFAULT_MULTI_DAY_POLICY,
)

/** What the planner did on one day. Kept per day so the UI and the report can show a timeline. */
data class DayPlan(
    val date: java.time.LocalDate,
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,
    val conflicts: List<ScheduleConflict>,
    val changes: List<ScheduleChange>,
    val assumptions: List<ScheduleAssumption>,
    val overloaded: Boolean,
    val explanation: String,
)

/**
 * A plan spanning several days.
 *
 * The reconciliation invariant is the important one: `placements.map { it.taskId }.toSet()` and
 * `unplaced.map { it.taskId }` together cover every eligible task exactly once. A caller can
 * therefore prove nothing was dropped instead of trusting that it was not.
 */
data class MultiDayPlan(
    val placements: List<SchedulePlacement>,
    val unplaced: List<UnplacedTask>,
    val changes: List<ScheduleChange>,
    val conflicts: List<ScheduleConflict>,
    val assumptions: List<ScheduleAssumption>,
    val days: List<DayPlan>,
    val policyVersion: String,
) {
    val overloaded: Boolean get() = days.any { it.overloaded }
    val placedTaskIds: Set<TaskId> get() = placements.map { it.taskId }.toSet()
    val unplacedTaskIds: Set<TaskId> get() = unplaced.map { it.taskId }.toSet()

    /** Every task the planner was asked to place: placed plus reported. */
    fun isReconciled(eligibleTaskIds: Set<TaskId>): Boolean =
        placedTaskIds + unplacedTaskIds == eligibleTaskIds &&
            placedTaskIds.intersect(unplacedTaskIds.toSet()).isEmpty()
}

const val DEFAULT_MULTI_DAY_POLICY: String = "p04-v2"

interface MultiDayPlanner {
    fun plan(input: MultiDayInput): Outcome<MultiDayPlan>
}

/**
 * Plans a whole horizon by driving the single-day [ScheduleEngine] forward, one day at a time.
 *
 * Why an orchestrator and not a second scheduler: the engine already owns every rule that makes a
 * plan trustworthy — fixed commitments are never moved, a confirmed dependency is placed before
 * its dependent, a deadline that cannot be met is reported rather than overrun. A separate
 * multi-day algorithm would have to re-implement all of it and could disagree with the daily
 * screen. Here the *only* new idea is which day to try next; every placement is still made by the
 * engine the user already trusts.
 *
 * How a task moves forward:
 * 1. Every unplaced task is offered to every remaining day, in engine order (nearest deadline
 *    first, then priority, then id).
 * 2. Whatever the engine places is committed and removed from the pool.
 * 3. Whatever it does not place stays in the pool and is offered again tomorrow, so an assignment
 *    that cannot fit today can still land three days before its deadline.
 * 4. A task requested for a future date is reported `OUTSIDE_HORIZON` on the earlier days and is
 *    simply left in the pool, which is exactly the behaviour the daily engine already had.
 * 5. After the final day, whatever is still pending is reported with the last reason the engine
 *    gave, so an unplaced task is never lost — it is always either placed or explained.
 */
class DeterministicMultiDayPlanner(
    private val engine: ScheduleEngine,
) : MultiDayPlanner {

    override fun plan(input: MultiDayInput): Outcome<MultiDayPlan> {
        val days = input.days.sortedBy { it.start }
        if (days.isEmpty()) {
            return Outcome.Failed(
                Failure.Validation(listOf("There are no days to plan into.")),
            )
        }
        if (days.zipWithNext().any { (a, b) -> !a.end.isBefore(b.start) }) {
            return Outcome.Failed(
                Failure.Validation(listOf("The planning days overlap, so a placement could be counted twice.")),
            )
        }

        val eligible = input.tasks.eligibleForScheduling()
        val eligibleIds = eligible.map { it.id }.toSet()
        val pool = eligible.toMutableList()
        val globalAssumptions = mutableListOf<ScheduleAssumption>()
        val allChanges = mutableListOf<ScheduleChange>()
        val allConflicts = mutableListOf<ScheduleConflict>()
        val dayPlans = mutableListOf<DayPlan>()
        /** Last reason each still-unplaced task was given, so the final report is the real one. */
        val lastUnplaced = linkedMapOf<TaskId, UnplacedTask>()

        for ((index, day) in days.withIndex()) {
            val commitments = input.fixedCommitments.filter {
                it.range.overlaps(com.chrona.app.domain.model.TimeRange(day.start, day.end))
            }
            // Only this day's previous placements are compared. Carrying every earlier day's
            // blocks into a later day's diff would report the whole horizon as "moved" every
            // time the horizon shifted, which tells the user nothing.
            val previousOnThisDay = input.previousPlacements.filter {
                it.start.toLocalDate() == day.date
            }
            val proposal = engine.propose(
                ScheduleInput(
                    tasks = pool.toList(),
                    fixedCommitments = commitments,
                    dayStart = day.start,
                    dayEnd = day.end,
                    relationships = input.relationships,
                    now = input.now,
                    availabilityWindows = day.windows,
                    // Restrict whenever the day says anything about its hours. An available day
                    // with no windows keeps the whole-horizon default; an unavailable day
                    // restricts to nothing, which is what makes it genuinely empty.
                    restrictToAvailability = !day.isAvailable || day.windows.isNotEmpty(),
                    buffer = input.buffer,
                    previousPlacements = previousOnThisDay,
                    basedOnRevisionId = input.basedOnRevisionId,
                    policyVersion = input.policyVersion,
                ),
            )

            val placedIds = proposal.placements.map { it.taskId }.toSet()
            pool.removeAll { it.id in placedIds }
            // A task the engine could not place today but places tomorrow is placed, not
            // unplaced. Forgetting the earlier "no room" reason here is what keeps the
            // placed/unplaced sets disjoint across the whole plan.
            placedIds.forEach { lastUnplaced.remove(it) }
            // Re-recording on each attempt keeps the freshest explanation as the final one.
            for (entry in proposal.unplaced) if (entry.taskId !in placedIds) lastUnplaced[entry.taskId] = entry

            // Only movement among the tasks this day actually plans is this day's change list.
            val dayChanges = proposal.changes.filter { it.taskId in placedIds }
            allChanges += dayChanges
            allConflicts += proposal.conflicts
            // Assumption noise is stated once for the horizon rather than repeated per day.
            if (index == 0) globalAssumptions += proposal.assumptions

            dayPlans += DayPlan(
                date = day.date,
                placements = proposal.placements,
                unplaced = proposal.unplaced,
                conflicts = proposal.conflicts,
                changes = dayChanges,
                assumptions = proposal.assumptions,
                overloaded = proposal.overloaded,
                explanation = proposal.explanation,
            )
        }

        // Anything still in the pool after the last day never got a final answer. Report it as
        // unplaceable rather than letting it vanish between the plan and the next run.
        pool.forEach { task ->
            lastUnplaced.putIfAbsent(
                task.id,
                UnplacedTask(
                    task.id,
                    UnplacedReason.NO_AVAILABLE_WINDOW,
                    "The planning horizon ended before there was room for it.",
                ),
            )
        }

        val plan = MultiDayPlan(
            placements = dayPlans.flatMap { it.placements }
                .sortedWith(compareBy({ it.start }, { it.sessionIndex }, { it.taskId.value })),
            unplaced = lastUnplaced.values.filter { it.taskId in eligibleIds }.sortedBy { it.taskId.value },
            changes = allChanges.sortedWith(compareBy({ it.taskId.value }, { it.fromStart })),
            conflicts = allConflicts,
            assumptions = globalAssumptions.distinct(),
            days = dayPlans,
            policyVersion = input.policyVersion,
        )
        return Outcome.Success(plan)
    }
}
