package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.relation.RelationGraph
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Pure helpers for the deterministic scheduler.
 *
 * Kept out of the engine class so each rule (eligibility, tie-breaking, free time, dependency
 * order) is testable on its own and there is exactly one definition of it.
 */

/** Tasks the scheduler may place. Everything else is inert for planning. */
fun List<Task>.eligibleForScheduling(): List<Task> = filter { it.status.isActive }

/** Higher priority sorts first. Direction is explicit so no inversion slips in. */
fun Priority.rank(): Int = when (this) {
    Priority.CRITICAL -> 0
    Priority.IMPORTANT -> 1
    Priority.LOW -> 2
}

/**
 * Total, deadline-aware ordering: earliest deadline first, then priority, then id.
 *
 * Earliest-deadline-first is Jackson's rule: for independent work with no release times it
 * minimises the maximum lateness, so a feasible set of deadlines is not missed merely because
 * a high-priority task took the only slot a sooner deadline needed. Priority remains the
 * tie-break among tasks whose deadlines tie, and every task without a deadline sorts last.
 *
 * Total because two runs over the same tasks must produce the same order; id breaks every
 * remaining tie (CH-SCHED-004).
 */
fun schedulingComparator(): Comparator<Task> =
    compareBy<Task> { it.deadline ?: FAR_FUTURE }
        .thenBy { it.priority.rank() }
        .thenBy { it.id.value }

/** Deterministic far-future sentinel for "no deadline"; never a real horizon. */
val FAR_FUTURE: ZonedDateTime = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC)

/**
 * Where a task's requested date sits relative to the single local day being planned.
 *
 * The horizon is one local day, `[dayStart, dayEnd)`. A date-only request outside it is not a
 * capacity failure; it is simply not today's work.
 */
enum class HorizonMembership {
    /** Today, or no date request at all. Eligible for today's plan. */
    BELONGS,

    /** Requested for an earlier day and still open: carried forward into today, with an assumption. */
    BEFORE,

    /** Requested for a later day: left for that day, reported as [UnplacedReason.OUTSIDE_HORIZON]. */
    AFTER,
}

/** The local date the engine plans. Single source of truth for the horizon's date. */
fun ScheduleInput.horizonDate(): LocalDate = dayStart.toLocalDate()

/**
 * The absolute ranges in which *flexible* work may be placed.
 *
 * Availability is intersected with the horizon and with [from] (the injected `now`, which may be
 * later than midnight for a late start), then clipped to `dayEnd`. The result is sorted and
 * merged so the intervals are disjoint, which is what `consume` relies on when it carves a
 * placement out of one interval.
 *
 * An empty [ScheduleInput.availabilityWindows] means the whole horizon is available, so a caller
 * that has not configured availability keeps working exactly as before.
 */
fun ScheduleInput.flexibleCapacity(from: ZonedDateTime): List<TimeRange> {
    if (!restrictToAvailability) return listOf(TimeRange(maxOf(dayStart, from), dayEnd))
        .filter { it.end.isAfter(it.start) }
    val clipped = availabilityWindows.mapNotNull { window ->
        val start = maxOf(window.start, dayStart, from)
        val end = minOf(window.end, dayEnd)
        if (end.isAfter(start)) TimeRange(start, end) else null
    }
    return mergeRanges(clipped)
}

/** Sorts and merges overlapping or touching ranges, so the output is disjoint and ordered. */
fun mergeRanges(ranges: List<TimeRange>): List<TimeRange> {
    if (ranges.size < 2) return ranges
    val merged = mutableListOf<TimeRange>()
    for (range in ranges.sortedBy { it.start }) {
        val last = merged.lastOrNull()
        if (last != null && !range.start.isAfter(last.end)) {
            if (range.end.isAfter(last.end)) merged[merged.lastIndex] = TimeRange(last.start, range.end)
        } else {
            merged += range
        }
    }
    return merged
}

fun Task.horizonMembership(horizon: LocalDate): HorizonMembership {
    val requested = requestedDate ?: return HorizonMembership.BELONGS
    return when {
        requested.isBefore(horizon) -> HorizonMembership.BEFORE
        requested.isAfter(horizon) -> HorizonMembership.AFTER
        else -> HorizonMembership.BELONGS
    }
}

fun taskInterval(start: ZonedDateTime, end: ZonedDateTime): TimeRange = TimeRange(start, end)

/**
 * Absolute blocked time: external fixed commitments plus FIXED tasks.
 *
 * FIXED tasks are hard constraints in exactly the same sense as a class; the scheduler never
 * moves either.
 */
fun busyRanges(commitments: List<FixedCommitment>, fixedTasks: List<Task>): List<TimeRange> =
    (commitments.map { it.range } + fixedTasks.mapNotNull { it.fixedRange })
        .sortedBy { it.start }

/**
 * Free intervals inside [day] after removing [busy], with [buffer] kept clear on each side of a
 * busy block. Buffers are never invented between two placements.
 */
fun freeIntervals(day: TimeRange, busy: List<TimeRange>, buffer: Duration): List<TimeRange> {
    val out = mutableListOf<TimeRange>()
    var cursor = day.start
    for (block in busy.sortedBy { it.start }) {
        val segmentEnd = block.start.minus(buffer)
        if (segmentEnd.isAfter(cursor)) out += TimeRange(cursor, segmentEnd)
        val afterBlock = block.end.plus(buffer)
        if (afterBlock.isAfter(cursor)) cursor = afterBlock
    }
    if (cursor.isBefore(day.end)) out += TimeRange(cursor, day.end)
    return out
}

/** Removes [start, end) from a free-interval list, leaving any remainder before/after it. */
fun MutableList<TimeRange>.consume(start: ZonedDateTime, end: ZonedDateTime) {
    val index = indexOfFirst { !start.isBefore(it.start) && !end.isAfter(it.end) }
    if (index < 0) return
    val interval = removeAt(index)
    if (start.isAfter(interval.start)) add(index, TimeRange(interval.start, start))
    if (end.isBefore(interval.end)) add(index + (if (start.isAfter(interval.start)) 1 else 0), TimeRange(end, interval.end))
}

/**
 * The absolute preferred start for a task on [day], or null when the preference is unusable.
 *
 * A preferred window is a soft, local-time hint; it can never create a hard conflict (CH-SCHED-005).
 */
fun Task.preferredStartOn(day: LocalDate, zone: java.time.ZoneId): ZonedDateTime? {
    val window = preferredWindow ?: return null
    return runCatching {
        window.start.atDate(day).atZone(zone)
    }.getOrNull()
}

/**
 * Dependency-safe processing order for [tasks].
 *
 * A task is ready only once every prerequisite has left the pool. Among ready tasks the
 * [schedulingComparator] decides (earliest deadline, then priority, then id), so a hard
 * dependency always comes first (REL-002) and, among the independent work, the nearest
 * deadline is protected. Any remaining tasks (a cycle) are appended in comparator order so
 * nothing is dropped.
 */
fun dependencyOrder(tasks: List<Task>, graph: RelationGraph): List<Task> {
    val ids = tasks.map { it.id }.toSet()
    val remaining = tasks.toMutableList()
    val order = mutableListOf<Task>()
    while (remaining.isNotEmpty()) {
        val pending = remaining.map { it.id }.toSet()
        val ready = remaining.filter { task ->
            graph.prerequisitesOf(task.id).none { it in ids && it in pending }
        }
        if (ready.isEmpty()) break
        val next = ready.sortedWith(schedulingComparator()).first()
        order += next
        remaining.remove(next)
    }
    order += remaining.sortedWith(schedulingComparator())
    return order
}
