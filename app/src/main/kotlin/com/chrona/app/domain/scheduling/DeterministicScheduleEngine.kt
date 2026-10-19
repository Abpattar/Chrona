package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.relation.RelationGraph
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

/**
 * The deterministic scheduling heuristic (CH-SCHED-001).
 *
 * Rules, in priority order:
 * 1. Fixed commitments and FIXED tasks are never moved.
 * 2. A confirmed hard dependency is scheduled before its dependent; a loop is rejected outright.
 * 3. A deadline that cannot be met is reported, never quietly overrun.
 * 4. Among ready tasks, the nearest deadline wins, then priority, then id (deadline-aware).
 *
 * It is deliberately a heuristic, not an optimiser: every outcome can be explained in one
 * sentence, which is what a trustworthy planner needs (MASTER_ARCHITECTURE.md §5).
 */
class DeterministicScheduleEngine : ScheduleEngine {

    private val defaultDuration: Duration = Duration.ofMinutes(30)

    override fun propose(input: ScheduleInput): ScheduleProposal {
        val effectiveStart = input.now?.takeIf { it.isAfter(input.dayStart) } ?: input.dayStart
        val assumptions = mutableListOf<ScheduleAssumption>()
        val placements = mutableListOf<SchedulePlacement>()
        val unplaced = mutableListOf<UnplacedTask>()
        val conflicts = mutableListOf<ScheduleConflict>()

        val eligible = input.tasks.eligibleForScheduling()
        val graph = RelationGraph(input.relationships)
        val horizon = input.horizonDate()

        // A date-only request outside today is not a capacity problem. Keep it visible as
        // OUTSIDE_HORIZON instead of silently planning it into the wrong day.
        val deferred = eligible.filter { it.horizonMembership(horizon) == HorizonMembership.AFTER }
        val deferredIds = deferred.map { it.id }.toSet()
        for (task in deferred) {
            unplaced += UnplacedTask(
                task.id,
                UnplacedReason.OUTSIDE_HORIZON,
                "It is requested for ${task.requestedDate}, so it is left for that day.",
            )
        }
        if (deferred.isNotEmpty()) {
            assumptions += ScheduleAssumption(
                "Task(s) requested for another day were left for that day, not planned into today.",
            )
        }

        // An overdue open task is carried forward, never silently moved without saying so.
        eligible.filter { it.horizonMembership(horizon) == HorizonMembership.BEFORE }.forEach { task ->
            assumptions += ScheduleAssumption(
                "\u201c${task.title}\u201d was requested for ${task.requestedDate} and is still open, " +
                    "so it is planned into today.",
            )
        }

        val considered = eligible.filter { it.id !in deferredIds }

        if (input.availabilityWindows.isNotEmpty()) {
            assumptions += ScheduleAssumption(
                "Flexible work was kept inside your available hours for this day.",
            )
        }

        graph.findHardDependencyCycle()?.let { cycle ->
            val path = cycle.joinToString(" -> ") { it.value }
            assumptions += ScheduleAssumption(
                "A dependency loop ($path) was found, so those tasks were left unplanned until it is fixed.",
            )
            considered.forEach {
                unplaced += UnplacedTask(
                    it.id,
                    UnplacedReason.DEPENDENCY_INCOMPLETE,
                    "Left unplanned because it takes part in a dependency loop.",
                )
            }
            return buildProposal(
                input, placements, unplaced, conflicts, emptyList(), assumptions,
                overloaded = false,
                explanation = "A dependency loop blocked planning: $path",
            )
        }

        val fixedTasks = considered.filter { it.fixedRange != null }.sortedBy { it.fixedRange!!.start }
        val flexible = considered.filter { it.fixedRange == null }
        val completion = mutableMapOf<TaskId, ZonedDateTime>()
        val fixedRanges = mutableListOf<com.chrona.app.domain.model.TimeRange>()

        for (task in fixedTasks) {
            val range = task.fixedRange!!
            when {
                range.start.isBefore(input.dayStart) || range.end.isAfter(input.dayEnd) ->
                    unplaced += UnplacedTask(task.id, UnplacedReason.FIXED_CONFLICT, "Its fixed time falls outside the day.")

                input.fixedCommitments.any { it.range.overlaps(range) } ->
                    unplaced += UnplacedTask(task.id, UnplacedReason.FIXED_CONFLICT, "Its fixed time clashes with a commitment.")

                fixedRanges.any { it.overlaps(range) } ->
                    unplaced += UnplacedTask(task.id, UnplacedReason.FIXED_CONFLICT, "Its fixed time clashes with another fixed task.")

                else -> {
                    placements += SchedulePlacement(task.id, range.start, range.end, 0)
                    fixedRanges += range
                    completion[task.id] = range.end
                }
            }
        }

        val busy = busyRanges(input.fixedCommitments, fixedTasks.filter { completion.containsKey(it.id) })
        // Flexible work is placed inside the user's real availability, not merely wherever the
        // clock is free between midnight and midnight. With no availability configured the
        // capacity is the whole horizon, which is the historical behaviour.
        val free = input.flexibleCapacity(effectiveStart)
            .flatMap { freeIntervals(it, busy, input.buffer) }
            .toMutableList()

        val ordered = dependencyOrder(flexible, graph)
        val flexibleIds = flexible.map { it.id }.toSet()
        val titles = input.tasks.associate { it.id to it.title }

        for (task in ordered) {
            val prereqs = graph.prerequisitesOf(task.id).filter {
                it in flexibleIds || completion.containsKey(it) || it in deferredIds
            }
            val unfinished = prereqs.firstOrNull { completion[it] == null }
            if (unfinished != null) {
                unplaced += UnplacedTask(
                    task.id,
                    UnplacedReason.DEPENDENCY_INCOMPLETE,
                    "It waits for \u201c${titles[unfinished] ?: unfinished.value}\u201d, which is not planned yet.",
                )
                continue
            }

            val duration = task.estimatedDuration ?: defaultDuration
            if (task.estimatedDuration == null) {
                assumptions += ScheduleAssumption(
                    "\u201c${task.title}\u201d had no duration, so ${defaultDuration.toMinutes()} minutes was assumed.",
                )
            }
            if (duration.isZero || duration.isNegative) {
                unplaced += UnplacedTask(task.id, UnplacedReason.INVALID_DURATION, "Its duration is not usable.")
                continue
            }

            val earliest = listOfNotNull(
                effectiveStart,
                task.earliestStart?.takeIf { it.isAfter(effectiveStart) },
                prereqs.mapNotNull { completion[it] }.maxOrNull(),
            ).maxOrNull() ?: effectiveStart

            val preferred = task.preferredStartOn(input.dayStart.toLocalDate(), input.dayStart.zone)
                ?.takeIf { it.isAfter(earliest) }
                ?.takeIf { !it.isAfter(task.deadline ?: FAR_FUTURE) }

            val result = if (task.splittable && task.minimumSession != null) {
                placeSplit(task, duration, task.minimumSession!!, free, preferred, earliest, task.deadline)
            } else {
                placeSingle(task, duration, free, preferred, earliest, task.deadline)
            }

            when (result) {
                is PlacementResult.Placed -> {
                    result.placements.forEach { placements += it }
                    completion[task.id] = result.placements.maxOf { it.end }
                }

                PlacementResult.DeadlineInfeasible ->
                    unplaced += UnplacedTask(
                        task.id, UnplacedReason.DEADLINE_INFEASIBLE,
                        "There is no slot left before its deadline.",
                    )

                PlacementResult.MinSessionUnavailable ->
                    unplaced += UnplacedTask(
                        task.id, UnplacedReason.MIN_SESSION_UNAVAILABLE,
                        "Only gaps shorter than its minimum session were free, so it was left whole.",
                    )

                PlacementResult.NoWindow ->
                    unplaced += UnplacedTask(
                        task.id, UnplacedReason.NO_AVAILABLE_WINDOW,
                        "The day had no free room left for it.",
                    )
            }
        }

        val overloaded = unplaced.any {
            it.reason == UnplacedReason.NO_AVAILABLE_WINDOW ||
                it.reason == UnplacedReason.DEADLINE_INFEASIBLE ||
                it.reason == UnplacedReason.MIN_SESSION_UNAVAILABLE ||
                it.reason == UnplacedReason.FIXED_CONFLICT
        }
        if (overloaded) {
            assumptions += ScheduleAssumption(
                "There is more planned work than the day can hold. That is a capacity limit, not a " +
                    "personal failure. Nothing was silently dropped \u2014 everything that did not fit is listed.",
            )
        }

        val changes = describeChanges(input, placements)
        val explanation = buildString {
            append(placements.size)
            append(" block(s) planned, ")
            append(unplaced.size)
            append(" could not be placed.")
            if (changes.isNotEmpty()) {
                append(" ")
                append(changes.size)
                append(" task(s) moved.")
            }
        }

        return buildProposal(input, placements, unplaced, conflicts, changes, assumptions, overloaded, explanation)
    }

    private fun placeSingle(
        task: Task,
        duration: Duration,
        free: MutableList<com.chrona.app.domain.model.TimeRange>,
        preferred: ZonedDateTime?,
        earliest: ZonedDateTime,
        deadline: ZonedDateTime?,
    ): PlacementResult {
        val slot = findSlot(free, preferred ?: earliest, duration, deadline)
            ?: findSlot(free, earliest, duration, deadline)
            ?: return if (findSlot(free, earliest, duration, null) != null) {
                PlacementResult.DeadlineInfeasible
            } else {
                PlacementResult.NoWindow
            }

        free.consume(slot.start, slot.end)
        return PlacementResult.Placed(listOf(SchedulePlacement(task.id, slot.start, slot.end, 0)))
    }

    private fun placeSplit(
        task: Task,
        duration: Duration,
        minimumSession: Duration,
        free: MutableList<com.chrona.app.domain.model.TimeRange>,
        preferred: ZonedDateTime?,
        earliest: ZonedDateTime,
        deadline: ZonedDateTime?,
    ): PlacementResult {
        val sessions = mutableListOf<SchedulePlacement>()
        var remaining = duration
        var from = earliest
        var deadlineHit = false

        for (interval in free.sortedBy { it.start }) {
            if (remaining.isZero) break
            var start = if (interval.start.isAfter(from)) interval.start else from
            if (sessions.isEmpty() && preferred != null && preferred.isAfter(start)) start = preferred
            if (!start.isBefore(interval.end)) continue
            if (deadline != null && !start.isBefore(deadline)) {
                deadlineHit = true
                continue
            }

            var available = Duration.between(start, interval.end)
            if (deadline != null) {
                available = minOf(available, Duration.between(start, deadline))
            }
            if (available < minimumSession) continue

            val take = if (available < remaining) available else remaining
            if (take < minimumSession) continue

            sessions += SchedulePlacement(task.id, start, start.plus(take), sessions.size)
            remaining -= take
            from = start.plus(take)
        }

        if (!remaining.isZero) {
            return if (deadlineHit && sessions.isEmpty()) {
                PlacementResult.DeadlineInfeasible
            } else {
                PlacementResult.MinSessionUnavailable
            }
        }

        sessions.forEach { free.consume(it.start, it.end) }
        return PlacementResult.Placed(sessions)
    }

    private fun findSlot(
        free: List<com.chrona.app.domain.model.TimeRange>,
        from: ZonedDateTime,
        duration: Duration,
        deadline: ZonedDateTime?,
    ): Slot? {
        for (interval in free.sortedBy { it.start }) {
            val start = if (interval.start.isAfter(from)) interval.start else from
            val end = start.plus(duration)
            if (end.isAfter(interval.end)) continue
            if (deadline != null && end.isAfter(deadline)) continue
            return Slot(start, end)
        }
        return null
    }

    /** Compares the proposal against the previously applied placements so movement is explained. */
    private fun describeChanges(input: ScheduleInput, placements: List<SchedulePlacement>): List<ScheduleChange> {
        val previous = input.previousPlacements.groupBy { it.taskId }
        val planned = placements.groupBy { it.taskId }
        val changes = mutableListOf<ScheduleChange>()
        val ids = (previous.keys + planned.keys).sortedBy { it.value }
        for (id in ids) {
            val before = previous[id]?.minByOrNull { it.start }?.start
            val after = planned[id]?.minByOrNull { it.start }?.start
            if (before == after) continue
            val explanation = when {
                before == null -> "Newly planned."
                after == null -> "Could not be placed this time."
                // Wall-clock to the minute. A raw ZonedDateTime prints nanosecond precision
                // ("10:35:33.580 to 11:10:23.569999"), which is noise, not information.
                else -> "Moved from ${CLOCK_FORMAT.format(before)} to ${CLOCK_FORMAT.format(after)}."
            }
            changes += ScheduleChange(id, before, after, explanation)
        }
        return changes
    }

    private fun buildProposal(
        input: ScheduleInput,
        placements: List<SchedulePlacement>,
        unplaced: List<UnplacedTask>,
        conflicts: List<ScheduleConflict>,
        changes: List<ScheduleChange>,
        assumptions: List<ScheduleAssumption>,
        overloaded: Boolean,
        explanation: String,
    ): ScheduleProposal {
        val sortedPlacements = placements.sortedWith(
            compareBy({ it.start }, { it.sessionIndex }, { it.taskId.value }),
        )
        return ScheduleProposal(
            proposalId = deterministicId(input.policyVersion, sortedPlacements, unplaced, input),
            basis = ScheduleBasis(
                revisionId = input.basedOnRevisionId,
                taskVersions = input.tasks.associate { it.id to it.version },
            ),
            placements = sortedPlacements,
            unplaced = unplaced.sortedBy { it.taskId.value },
            conflicts = conflicts,
            changes = changes,
            assumptions = assumptions,
            overloaded = overloaded,
            explanation = explanation,
            policyVersion = input.policyVersion,
        )
    }

    private fun deterministicId(
        policyVersion: String,
        placements: List<SchedulePlacement>,
        unplaced: List<UnplacedTask>,
        input: ScheduleInput,
    ): String {
        val signature = buildString {
            append(policyVersion).append('|')
            append(input.dayStart.toInstant()).append('-').append(input.dayEnd.toInstant()).append('|')
            placements.forEach {
                append(it.taskId.value).append('@').append(it.start.toInstant())
                    .append('-').append(it.end.toInstant()).append('#').append(it.sessionIndex).append(';')
            }
            append('|')
            unplaced.sortedBy { it.taskId.value }.forEach { append(it.taskId.value).append(':').append(it.reason).append(';') }
        }
        return UUID.nameUUIDFromBytes(signature.toByteArray()).toString()
    }

    private companion object {
        /** How a placement time is written in a sentence meant for a person. */
        private val CLOCK_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }

    private data class Slot(val start: ZonedDateTime, val end: ZonedDateTime)

    private sealed interface PlacementResult {
        data class Placed(val placements: List<SchedulePlacement>) : PlacementResult
        data object DeadlineInfeasible : PlacementResult
        data object MinSessionUnavailable : PlacementResult
        data object NoWindow : PlacementResult
    }
}
