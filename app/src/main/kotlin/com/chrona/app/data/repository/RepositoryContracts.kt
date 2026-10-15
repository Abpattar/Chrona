package com.chrona.app.data.repository

import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.EventId
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.RelationId
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Repository contract from `SHARED_CONTRACT.md`.
 *
 * Implementations return domain records and typed outcomes, never Room entities, so the
 * storage technology can change without touching use cases or UI.
 */
interface TaskRepository {

    suspend fun get(taskId: TaskId): Outcome<Task?>

    suspend fun list(query: TaskQuery): Outcome<List<Task>>

    suspend fun insert(task: Task): Outcome<TaskId>

    /**
     * Updates a task, but only if it is still at [expectedVersion].
     *
     * A stale version must produce [Failure.Conflict] rather than silently overwriting a
     * newer edit.
     */
    suspend fun update(task: Task, expectedVersion: Long): Outcome<Task>

    suspend fun delete(taskId: TaskId): Outcome<Unit>

    /**
     * Applies a validated status change together with its history row.
     *
     * The task update and the log insert must succeed or fail as one unit: a status change
     * without an audit trail would break the History surface, and an audit trail without the
     * change would be a lie.
     */
    suspend fun updateStatus(
        taskId: TaskId,
        transition: (Task) -> Outcome<Task>,
        reasonCode: String,
        occurredAt: Instant,
    ): Outcome<Task>
}

interface EventRepository {

    suspend fun list(from: ZonedDateTime, to: ZonedDateTime): Outcome<List<CalendarEvent>>

    suspend fun get(eventId: EventId): Outcome<CalendarEvent?>

    suspend fun upsert(event: CalendarEvent, expectedVersion: Long?): Outcome<CalendarEvent>

    suspend fun delete(eventId: EventId): Outcome<Unit>
}

interface RelationshipRepository {

    suspend fun listFor(taskIds: Set<TaskId>): Outcome<List<TaskRelationship>>

    suspend fun listAll(): Outcome<List<TaskRelationship>>

    suspend fun upsert(relationship: TaskRelationship): Outcome<TaskRelationship>

    suspend fun delete(relationId: RelationId): Outcome<Unit>

    /** Replaces every outgoing link of [source] in one transaction. */
    suspend fun replaceFor(source: TaskId, links: List<TaskRelationship>): Outcome<Unit>
}

/** Stable machine codes for status history. Never raw user text (privacy requirement). */
object ReasonCode {
    const val CREATED: String = "TASK_CREATED"
    const val EDITED: String = "TASK_EDITED"
    const val STARTED: String = "TASK_STARTED"
    const val COMPLETED: String = "TASK_COMPLETED"
    const val POSTPONED: String = "TASK_POSTPONED"
    const val SKIPPED: String = "TASK_SKIPPED"
    const val ARCHIVED: String = "TASK_ARCHIVED"
    const val RESTORED: String = "TASK_RESTORED"
    const val DELETED: String = "TASK_DELETED"
    const val EVENT_CREATED: String = "EVENT_CREATED"
    const val EVENT_UPDATED: String = "EVENT_UPDATED"
    const val EVENT_DELETED: String = "EVENT_DELETED"
}

/** Convenience for callers that only care whether a status moved. */
val Outcome<Task>.newStatusOrNull: TaskStatus?
    get() = (this as? Outcome.Success)?.value?.status