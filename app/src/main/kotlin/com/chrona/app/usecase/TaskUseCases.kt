package com.chrona.app.usecase

import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.EventId
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.task.CalendarEventValidator
import com.chrona.app.domain.task.TaskStatusMachine
import com.chrona.app.domain.task.TaskValidator
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Reads tasks for one surface.
 *
 * Today, Schedule, Board and History all go through use cases rather than a DAO, so the
 * surfaces cannot drift apart and none of them can accidentally include archived work.
 */
class ListTasksUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(query: TaskQuery = TaskQuery.ACTIVE): Outcome<List<Task>> =
        taskRepository.list(query)
}

class GetTaskUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(taskId: TaskId): Outcome<Task> =
        when (val result = taskRepository.get(taskId)) {
            is Outcome.Success -> result.value
                ?.let { Outcome.Success(it) }
                ?: Outcome.Failed(Failure.NotFound)

            is Outcome.Failed -> result
        }
}

/** Editable fields a user may change without creating a new task. */
data class TaskEdits(
    val title: String,
    val details: String? = null,
    val priority: Priority = Priority.IMPORTANT,
    val estimatedDuration: java.time.Duration? = null,
    val deadline: ZonedDateTime? = null,
    val requestedDate: java.time.LocalDate? = null,
    val preferredWindow: com.chrona.app.domain.model.TimeWindow? = null,
    val flexibility: Flexibility = Flexibility.FLEXIBLE,
    val fixedStart: ZonedDateTime? = null,
    val fixedEnd: ZonedDateTime? = null,
    val splittable: Boolean = false,
    val minimumSession: java.time.Duration? = null,
    val projectLabel: String? = null,
)

/**
 * Edits an existing task.
 *
 * The caller must pass the version it read. If the task moved on, the repository returns a
 * conflict and the user's edit is not applied over newer work (MASTER_ARCHITECTURE.md §5).
 */
class EditTaskUseCase(
    private val taskRepository: TaskRepository,
    private val timeSource: TimeSource,
) {
    suspend operator fun invoke(taskId: TaskId, expectedVersion: Long, edits: TaskEdits): Outcome<Task> {
        val existing = when (val result = GetTaskUseCase(taskRepository)(taskId)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }

        val updated = existing.copy(
            title = edits.title.trim(),
            details = edits.details,
            priority = edits.priority,
            estimatedDuration = edits.estimatedDuration,
            deadline = edits.deadline,
            requestedDate = edits.requestedDate,
            preferredWindow = edits.preferredWindow,
            flexibility = edits.flexibility,
            fixedStart = edits.fixedStart,
            fixedEnd = edits.fixedEnd,
            splittable = edits.splittable,
            minimumSession = edits.minimumSession,
            projectLabel = edits.projectLabel,
            updatedAt = timeSource.now(),
        )

        val problems = TaskValidator.validate(updated)
        if (problems.isNotEmpty()) {
            return Outcome.Failed(Failure.Validation(problems))
        }
        return taskRepository.update(updated, expectedVersion)
    }
}

/**
 * Status transitions (CH-USE-001 family).
 *
 * Every transition here is an explicit user action. There is deliberately no
 * `dismissReminder` method: dismissing a card is presentation state and must never reach the
 * task record (CARD-003).
 *
 * Repeat calls are idempotent: completing an already-completed task returns the task
 * unchanged instead of bumping its version, so a duplicated notification tap cannot corrupt
 * History.
 */
class TaskLifecycleUseCases(
    private val taskRepository: TaskRepository,
    private val timeSource: TimeSource,
) {
    suspend fun start(taskId: TaskId): Outcome<Task> = change(taskId, TaskStatus.IN_PROGRESS)

    suspend fun complete(taskId: TaskId): Outcome<Task> = change(taskId, TaskStatus.COMPLETED)

    suspend fun skip(taskId: TaskId): Outcome<Task> = change(taskId, TaskStatus.SKIPPED)

    suspend fun archive(taskId: TaskId): Outcome<Task> = change(taskId, TaskStatus.ARCHIVED)

    suspend fun restore(taskId: TaskId): Outcome<Task> = change(taskId, TaskStatus.INBOX)

    /**
     * Postpones a task.
     *
     * [until] is resolved by the caller's zone; Chrona records the absolute instant plus the
     * zone so a later device timezone change cannot silently shift what the user agreed to.
     */
    suspend fun postpone(taskId: TaskId, until: ZonedDateTime?, reason: String? = null): Outcome<Task> {
        val now = timeSource.now()
        val outcome = taskRepository.updateStatus(
            taskId = taskId,
            transition = { current ->
                when (val result = TaskStatusMachine.transition(current, TaskStatus.POSTPONED, now)) {
                    is TaskStatusMachine.Result.Changed -> Outcome.Success(
                        result.task.copy(postponedUntil = until),
                    )

                    is TaskStatusMachine.Result.Unchanged -> Outcome.Success(result.task)
                    is TaskStatusMachine.Result.Rejected -> Outcome.Failed(
                        Failure.Conflict(result.reason),
                    )
                }
            },
            reasonCode = com.chrona.app.data.repository.ReasonCode.POSTPONED,
            occurredAt = now,
        )
        return outcome
    }

    private suspend fun change(taskId: TaskId, target: TaskStatus): Outcome<Task> {
        // Idempotence is a domain rule, so it is enforced here where every caller sees it: a
        // repeated tap or a duplicated notification action must not create a second history
        // entry or bump the version. The repository applies the same guard inside its
        // transaction, because P06 action handlers reach it directly.
        val existing = when (val result = GetTaskUseCase(taskRepository)(taskId)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        if (existing.status == target) return Outcome.Success(existing)

        val now = timeSource.now()
        return taskRepository.updateStatus(
            taskId = taskId,
            transition = { current ->
                when (val result = TaskStatusMachine.transition(current, target, now)) {
                    is TaskStatusMachine.Result.Changed -> Outcome.Success(result.task)
                    // Already in the requested state: report success without writing, so a
                    // duplicate tap is a no-op rather than a new history entry.
                    is TaskStatusMachine.Result.Unchanged -> Outcome.Success(result.task)
                    is TaskStatusMachine.Result.Rejected -> Outcome.Failed(
                        Failure.Conflict(result.reason),
                    )
                }
            },
            reasonCode = reasonCodeFor(target),
            occurredAt = now,
        )
    }

    private fun reasonCodeFor(target: TaskStatus): String = when (target) {
        TaskStatus.IN_PROGRESS -> com.chrona.app.data.repository.ReasonCode.STARTED
        TaskStatus.COMPLETED -> com.chrona.app.data.repository.ReasonCode.COMPLETED
        TaskStatus.POSTPONED -> com.chrona.app.data.repository.ReasonCode.POSTPONED
        TaskStatus.SKIPPED -> com.chrona.app.data.repository.ReasonCode.SKIPPED
        TaskStatus.ARCHIVED -> com.chrona.app.data.repository.ReasonCode.ARCHIVED
        TaskStatus.INBOX -> com.chrona.app.data.repository.ReasonCode.RESTORED
        TaskStatus.PLANNED -> com.chrona.app.data.repository.ReasonCode.EDITED
    }
}

/**
 * Permanently removes a task.
 *
 * Relations cascade with it, so no dangling edge survives. Archive is the reversible option
 * and is offered separately; nothing in the UI deletes without an explicit user action.
 */
class DeleteTaskUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(taskId: TaskId): Outcome<Unit> = taskRepository.delete(taskId)
}

/** What a caller wants to create for a manual fixed commitment. */
data class NewEvent(
    val title: String,
    val startAt: ZonedDateTime,
    val endAt: ZonedDateTime,
    val details: String? = null,
    val isFixed: Boolean = true,
)

/** Manual fixed-commitment entry, stored now so the scheduler has something to respect in P04. */
class EventUseCases(
    private val eventRepository: com.chrona.app.data.repository.EventRepository,
    private val idGenerator: IdGenerator,
    private val timeSource: TimeSource,
) {
    suspend fun create(request: NewEvent): Outcome<CalendarEvent> {
        val now = timeSource.now()
        val event = CalendarEvent(
            id = EventId(idGenerator.newId()),
            title = request.title,
            details = request.details,
            startAt = request.startAt,
            endAt = request.endAt,
            zoneId = request.startAt.zone.id,
            isFixed = request.isFixed,
            createdAt = now,
            updatedAt = now,
        )
        val problems = CalendarEventValidator.validate(event)
        if (problems.isNotEmpty()) return Outcome.Failed(Failure.Validation(problems))
        return eventRepository.upsert(event, expectedVersion = null)
    }

    suspend fun list(from: ZonedDateTime, to: ZonedDateTime): Outcome<List<CalendarEvent>> =
        eventRepository.list(from, to)

    suspend fun delete(eventId: EventId): Outcome<Unit> = eventRepository.delete(eventId)
}