package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import java.time.Duration
import java.time.ZonedDateTime

fun List<Task>.filterEligible(): List<Task> = filter {
    it.status == TaskStatus.INBOX ||
    it.status == TaskStatus.PLANNED ||
    it.status == TaskStatus.IN_PROGRESS ||
    it.status == TaskStatus.POSTPONED
}

fun taskComparator(): Comparator<Task> = compareBy<Task> { it.priority.rank() }.thenBy { it.deadline ?: java.time.ZonedDateTime.of(2100,1,1,0,0,0,0, java.time.ZoneId.of("UTC")) }.thenBy { it.id.value }

fun Priority.rank(): Int = when (this) {
    Priority.CRITICAL -> 0
    Priority.IMPORTANT -> 1
    Priority.LOW -> 2
}

fun getAvailableIntervals(day: TimeRange, fixed: List<FixedCommitment>, buffer: Duration): List<TimeRange> {
    val events = fixed.sortedBy { it.range.start }
    val out = mutableListOf<TimeRange>()
    var cursor = day.start
    for (e in events) {
        if (e.range.start.isAfter(cursor.plus(buffer))) {
            out.add(TimeRange(cursor, e.range.start.minus(buffer)))
        }
        if (e.range.end.isAfter(cursor)) {
            cursor = e.range.end
        }
    }
    if (cursor.isBefore(day.end.minus(buffer))) {
        out.add(TimeRange(cursor, day.end))
    }
    return out
}

data class DepGraph(val deps: Map<TaskId, Set<TaskId>>)

fun buildDependencies(tasks: List<Task>): DepGraph = DepGraph(emptyMap())

fun DepGraph.allDepsSatisfied(id: TaskId, placements: List<SchedulePlacement>): Boolean = true

fun placeInIntervals(t: Task, dur: Duration, intervals: List<TimeRange>, placements: MutableList<SchedulePlacement>, fixed: List<FixedCommitment>): Boolean {
    for (iv in intervals.sortedBy { it.start }) {
        val start = iv.start
        val end = start.plus(dur)
        if (end.isAfter(iv.end)) continue
        if (overlapsFixed(start, end, fixed)) continue
        if (overlapsPlacement(start, end, placements)) continue
        placements.add(SchedulePlacement(t.id, start, end, 0))
        return true
    }
    return false
}

fun overlapsFixed(s: ZonedDateTime, e: ZonedDateTime, fixed: List<FixedCommitment>): Boolean =
    fixed.any { it.range.overlapsRange(s, e) }

fun overlapsPlacement(s: ZonedDateTime, e: ZonedDateTime, p: List<SchedulePlacement>): Boolean =
    p.any { TimeRange(it.start, it.end).overlapsRange(s, e) }

fun TimeRange.overlapsRange(s: ZonedDateTime, e: ZonedDateTime): Boolean =
    s.isBefore(end) && e.isAfter(start)
