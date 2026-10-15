package com.chrona.app.domain.task

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskStatus
import java.time.Instant

/**
 * The single place where a task status change is permitted.
 *
 * Rules encoded here, from `SHARED_CONTRACT.md` §"Task and card actions are distinct":
 * - completion, postponement, skip, archive, start and reminder dismissal are *different*
 *   actions;
 * - dismissing a card never changes status, so there is no transition for it at all;
 * - a closed task is never silently reactivated, which protects against a stale UI or a
 *   stale reminder action resurrecting finished work.
 */
object TaskStatusMachine {

    /**
     * Transitions available from [from]. Anything not listed is rejected with a plain reason.
     */
    fun allowedTransitions(from: TaskStatus): Set<TaskStatus> = when (from) {
        TaskStatus.INBOX -> setOf(
            TaskStatus.PLANNED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.SKIPPED,
            TaskStatus.ARCHIVED,
        )

        TaskStatus.PLANNED -> setOf(
            TaskStatus.IN_PROGRESS,
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.SKIPPED,
            TaskStatus.ARCHIVED,
            TaskStatus.INBOX,
        )

        TaskStatus.IN_PROGRESS -> setOf(
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.SKIPPED,
            TaskStatus.ARCHIVED,
        )

        TaskStatus.POSTPONED -> setOf(
            TaskStatus.IN_PROGRESS,
            TaskStatus.PLANNED,
            TaskStatus.COMPLETED,
            TaskStatus.SKIPPED,
            TaskStatus.ARCHIVED,
            TaskStatus.INBOX,
        )

        TaskStatus.COMPLETED -> setOf(TaskStatus.ARCHIVED)
        TaskStatus.SKIPPED -> setOf(TaskStatus.INBOX, TaskStatus.ARCHIVED)
        TaskStatus.ARCHIVED -> setOf(TaskStatus.INBOX)
    }

    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean = to in allowedTransitions(from)

    fun rejectionReason(from: TaskStatus, to: TaskStatus): String = when (from) {
        TaskStatus.COMPLETED ->
            "This task is already completed. Archive it instead of changing its status."
        TaskStatus.ARCHIVED ->
            "This task is archived. Move it back to your list before changing its status."
        else -> "A task cannot go from ${from.label()} to ${to.label()}."
    }

    /**
     * Applies a status change, keeping the timestamp fields consistent with the new state.
     *
     * Returns the same instance when nothing would change, so repeat notification actions and
     * duplicate taps are idempotent rather than churning `updatedAt`.
     */
    fun transition(task: Task, to: TaskStatus, now: Instant): Result = when {
        task.status == to -> Result.unchanged(task)
        !canTransition(task.status, to) -> Result.rejected(rejectionReason(task.status, to))
        else -> Result.changed(apply(task, to, now))
    }

    private fun apply(task: Task, to: TaskStatus, now: Instant): Task = when (to) {
        TaskStatus.COMPLETED -> task.copy(
            status = to,
            completedAt = now,
            updatedAt = now,
            postponedUntil = null,
        )

        TaskStatus.IN_PROGRESS -> task.copy(
            status = to,
            updatedAt = now,
            // Starting work releases an accepted schedule assignment.
            completedAt = null,
        )

        // Reopening clears completion so History never shows a completed timestamp on a task
        // the user has put back into their day.
        TaskStatus.INBOX, TaskStatus.PLANNED -> task.copy(
            status = to,
            updatedAt = now,
            completedAt = null,
            postponedUntil = null,
        )

        TaskStatus.POSTPONED -> task.copy(
            status = to,
            updatedAt = now,
            completedAt = null,
        )

        TaskStatus.SKIPPED, TaskStatus.ARCHIVED -> task.copy(
            status = to,
            updatedAt = now,
            completedAt = null,
        )
    }

        sealed interface Result {
        data class Changed(val task: Task) : Result

        /** The task was already in the requested state; nothing was written. */
        data class Unchanged(val task: Task) : Result

        data class Rejected(val reason: String) : Result

        companion object {
            fun changed(task: Task): Result = Changed(task)
            fun unchanged(task: Task): Result = Unchanged(task)
            fun rejected(reason: String): Result = Rejected(reason)
        }
    }
}

/** Human label for a status. Used in errors and History so wording stays consistent. */
fun TaskStatus.label(): String = when (this) {
    TaskStatus.INBOX -> "in your list"
    TaskStatus.PLANNED -> "planned"
    TaskStatus.IN_PROGRESS -> "in progress"
    TaskStatus.COMPLETED -> "completed"
    TaskStatus.POSTPONED -> "postponed"
    TaskStatus.SKIPPED -> "skipped"
    TaskStatus.ARCHIVED -> "archived"
}

/** Human label for a flexibility, used in the task editor. */
fun Flexibility.label(): String = when (this) {
    Flexibility.FIXED -> "fixed time"
    Flexibility.FLEXIBLE -> "flexible"
}