package com.chrona.app.domain.model

import java.time.LocalDate

/**
 * Filter for listing tasks (SHARED_CONTRACT.md §TaskRepository).
 *
 * [includeStatuses] is an explicit set rather than a flag so a caller can never accidentally
 * include ARCHIVED or COMPLETED work in an active list.
 */
data class TaskQuery(
    val includeStatuses: Set<TaskStatus> = setOf(
        TaskStatus.INBOX,
        TaskStatus.PLANNED,
        TaskStatus.IN_PROGRESS,
        TaskStatus.POSTPONED,
    ),
    val projectLabel: String? = null,
    val dueOnOrBefore: LocalDate? = null,
    val limit: Int? = null,
    /**
     * Whether rows created by the in-app life simulation are in scope.
     *
     * Defaults to false because simulation tasks live in the same table as real ones: without
     * this, loading a 142-task simulation would bury the user's own day under invented work in
     * Today, the schedule and reminders. Surfaces that genuinely show both — the task browser —
     * opt in, and the rows stay clearly marked either way.
     */
    val includeSimulated: Boolean = false,
) {
    companion object {
        /** Everything the user has not closed, in stable creation order. */
        val ACTIVE = TaskQuery()

        /** Closed work, for History. */
        val CLOSED = TaskQuery(
            includeStatuses = setOf(TaskStatus.COMPLETED, TaskStatus.SKIPPED, TaskStatus.ARCHIVED),
        )

        /** Real and simulated work together, for the task browser. */
        val EVERYTHING = TaskQuery(
            includeStatuses = TaskStatus.entries.toSet(),
            includeSimulated = true,
        )
    }
}

/** Filter for fixed commitments overlapping a time range. */
data class TimeRangeQuery(val range: TimeRange)