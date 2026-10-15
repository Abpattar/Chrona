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
) {
    companion object {
        /** Everything the user has not closed, in stable creation order. */
        val ACTIVE = TaskQuery()

        /** Closed work, for History. */
        val CLOSED = TaskQuery(
            includeStatuses = setOf(TaskStatus.COMPLETED, TaskStatus.SKIPPED, TaskStatus.ARCHIVED),
        )
    }
}

/** Filter for fixed commitments overlapping a time range. */
data class TimeRangeQuery(val range: TimeRange)