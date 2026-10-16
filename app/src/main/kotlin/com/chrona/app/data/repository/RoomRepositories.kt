package com.chrona.app.data.repository

import androidx.room.withTransaction
import com.chrona.app.data.local.CalendarEventDao
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.local.TaskDao
import com.chrona.app.data.local.TaskRelationshipDao
import com.chrona.app.data.local.ScheduleRevisionDao
import com.chrona.app.data.local.TaskStatusLogDao
import com.chrona.app.data.local.TaskStatusLogEntity
import com.chrona.app.data.local.toDomain
import com.chrona.app.data.local.toEntity
import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.EventId
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskRelationship
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Room-backed [TaskRepository].
 *
 * Every method returns a typed outcome: a storage failure becomes [Failure.StorageFailure],
 * never an exception the UI has to guess about. On failure the previous state is always
 * intact, so the user can retry without losing anything.
 */
class RoomTaskRepository(
    private val database: ChronaDatabase,
    private val taskDao: TaskDao = database.taskDao(),
    private val statusLogDao: TaskStatusLogDao = database.taskStatusLogDao(),
) : TaskRepository {

    override suspend fun get(taskId: TaskId): Outcome<Task?> = storage {
        taskDao.findById(taskId.value)?.toDomain()
    }

    override suspend fun list(query: TaskQuery): Outcome<List<Task>> = storage {
        val rows = taskDao.findByStatuses(query.includeStatuses.map { it.name }).map { it.toDomain() }
        query.limit?.let { rows.take(it) } ?: rows
    }

    override suspend fun insert(task: Task): Outcome<TaskId> = storage {
        val entity = task.toEntity()
        database.withTransaction {
            taskDao.insert(entity)
            statusLogDao.insert(
                TaskStatusLogEntity(
                    taskId = task.id.value,
                    fromStatus = null,
                    toStatus = task.status.name,
                    reasonCode = ReasonCode.CREATED,
                    changedAtEpochMillis = task.createdAt.toEpochMilli(),
                ),
            )
        }
        task.id
    }

    override suspend fun update(task: Task, expectedVersion: Long): Outcome<Task> = storageOutcome {
        val entity = task.toEntity()
        database.withTransaction {
            val current = taskDao.findById(task.id.value)
            if (current == null) {
                Outcome.Failed(Failure.NotFound)
            } else {
                val rows = taskDao.updateIfVersionMatches(
                    id = entity.id,
                    expectedVersion = expectedVersion,
                    title = entity.title,
                    details = entity.details,
                    priority = entity.priority,
                    estimatedDurationMinutes = entity.estimatedDurationMinutes,
                    deadlineEpochMillis = entity.deadlineEpochMillis,
                    deadlineZoneId = entity.deadlineZoneId,
                    requestedDateIso = entity.requestedDateIso,
                    earliestStartEpochMillis = entity.earliestStartEpochMillis,
                    earliestStartZoneId = entity.earliestStartZoneId,
                    preferredStart = entity.preferredStart,
                    preferredEnd = entity.preferredEnd,
                    flexibility = entity.flexibility,
                    fixedStartEpochMillis = entity.fixedStartEpochMillis,
                    fixedEndEpochMillis = entity.fixedEndEpochMillis,
                    fixedZoneId = entity.fixedZoneId,
                    splittable = entity.splittable,
                    minimumSessionMinutes = entity.minimumSessionMinutes,
                    status = entity.status,
                    projectLabel = entity.projectLabel,
                    updatedAtEpochMillis = entity.updatedAtEpochMillis,
                    completedAtEpochMillis = entity.completedAtEpochMillis,
                    postponedUntilEpochMillis = entity.postponedUntilEpochMillis,
                    postponedUntilZoneId = entity.postponedUntilZoneId,
                    assumptions = entity.assumptions,
                    version = entity.version,
                )
                if (rows == 0) {
                    // Zero rows means the row is no longer at the version the caller read.
                    // Reporting a conflict stops a stale edit overwriting newer work.
                    Outcome.Failed(
                        Failure.Conflict("This task changed somewhere else. Reopen it to see the latest version."),
                    )
                } else {
                    statusLogDao.insert(
                        TaskStatusLogEntity(
                            taskId = entity.id,
                            fromStatus = current.status,
                            toStatus = entity.status,
                            reasonCode = ReasonCode.EDITED,
                            changedAtEpochMillis = entity.updatedAtEpochMillis,
                        ),
                    )
                    Outcome.Success(task.copy(version = expectedVersion + 1))
                }
            }
        }
    }

    override suspend fun delete(taskId: TaskId): Outcome<Unit> = storageOutcome {
        if (taskDao.findById(taskId.value) == null) {
            Outcome.Failed(Failure.NotFound)
        } else {
            // Relations cascade via foreign keys, so no dangling edge can survive this delete.
            taskDao.deleteById(taskId.value)
            Outcome.Success(Unit)
        }
    }

    override suspend fun updateStatus(
        taskId: TaskId,
        transition: (Task) -> Outcome<Task>,
        reasonCode: String,
        occurredAt: Instant,
    ): Outcome<Task> = storageOutcome {
        // Read, validate and both writes happen inside one transaction, so a status change and
        // its audit row commit together or not at all.
        database.withTransaction {
            val current = taskDao.findById(taskId.value)
            if (current == null) {
                Outcome.Failed(Failure.NotFound)
            } else {
                val currentDomain = current.toDomain()
                when (val transitioned = transition(currentDomain)) {
                    is Outcome.Failed -> transitioned

                    is Outcome.Success ->
                        if (transitioned.value == currentDomain) {
                            // The state machine reported no change. Writing anyway would bump the
                            // version and add a history row, so a duplicate tap or a repeated
                            // notification action would show up in History as real work.
                            Outcome.Success(currentDomain)
                        } else {
                            writeStatusChange(current, transitioned.value, reasonCode, occurredAt)
                        }
                }
            }
        }
    }

    private suspend fun writeStatusChange(
        current: com.chrona.app.data.local.TaskEntity,
        next: Task,
        reasonCode: String,
        occurredAt: Instant,
    ): Outcome<Task> = if (taskDao.applyStatusChange(statusLogDao, current, next, reasonCode, occurredAt)) {
        Outcome.Success(next.copy(version = current.version + 1))
    } else {
        Outcome.Failed(Failure.Conflict("This task changed somewhere else. Reopen it and try again."))
    }
}

/**
 * Updates a task and appends exactly one history row, in the caller's transaction.
 *
 * Shared by [RoomTaskRepository] and [RoomScheduleRevisionRepository] so an apply or an undo
 * writes status history the same way a manual lifecycle action does. Returns false when the row
 * moved on, which every caller turns into a typed conflict.
 */
internal suspend fun TaskDao.applyStatusChange(
    statusLogDao: TaskStatusLogDao,
    current: com.chrona.app.data.local.TaskEntity,
    next: Task,
    reasonCode: String,
    occurredAt: Instant,
): Boolean {
    val entity = next.toEntity()
    val rows = updateIfVersionMatches(
        id = entity.id,
        expectedVersion = current.version,
        title = entity.title,
        details = entity.details,
        priority = entity.priority,
        estimatedDurationMinutes = entity.estimatedDurationMinutes,
        deadlineEpochMillis = entity.deadlineEpochMillis,
        deadlineZoneId = entity.deadlineZoneId,
        requestedDateIso = entity.requestedDateIso,
        earliestStartEpochMillis = entity.earliestStartEpochMillis,
        earliestStartZoneId = entity.earliestStartZoneId,
        preferredStart = entity.preferredStart,
        preferredEnd = entity.preferredEnd,
        flexibility = entity.flexibility,
        fixedStartEpochMillis = entity.fixedStartEpochMillis,
        fixedEndEpochMillis = entity.fixedEndEpochMillis,
        fixedZoneId = entity.fixedZoneId,
        splittable = entity.splittable,
        minimumSessionMinutes = entity.minimumSessionMinutes,
        status = entity.status,
        projectLabel = entity.projectLabel,
        updatedAtEpochMillis = entity.updatedAtEpochMillis,
        completedAtEpochMillis = entity.completedAtEpochMillis,
        postponedUntilEpochMillis = entity.postponedUntilEpochMillis,
        postponedUntilZoneId = entity.postponedUntilZoneId,
        assumptions = entity.assumptions,
        version = entity.version,
    )
    if (rows == 0) return false
    statusLogDao.insert(
        TaskStatusLogEntity(
            taskId = entity.id,
            fromStatus = current.status,
            toStatus = entity.status,
            reasonCode = reasonCode,
            changedAtEpochMillis = occurredAt.toEpochMilli(),
        ),
    )
    return true
}

/** Room-backed [EventRepository] for manual fixed commitments. */
class RoomEventRepository(
    database: ChronaDatabase,
    private val eventDao: CalendarEventDao = database.calendarEventDao(),
) : EventRepository {

    override suspend fun list(from: ZonedDateTime, to: ZonedDateTime): Outcome<List<CalendarEvent>> =
        storage {
            eventDao.findOverlapping(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli())
                .map { it.toDomain() }
        }

    override suspend fun get(eventId: EventId): Outcome<CalendarEvent?> = storage {
        eventDao.findById(eventId.value)?.toDomain()
    }

    override suspend fun upsert(
        event: CalendarEvent,
        expectedVersion: Long?,
    ): Outcome<CalendarEvent> = storageOutcome {
        val existing = eventDao.findById(event.id.value)
        if (existing != null && expectedVersion != null && existing.version != expectedVersion) {
            Outcome.Failed(
                Failure.Conflict("This commitment changed somewhere else. Reopen it and try again."),
            )
        } else {
            // The caller has already validated the interval. A fixed commitment is never moved
            // by the scheduler, only by an explicit user edit.
            val stored = event.copy(version = (existing?.version ?: 0L) + 1)
            eventDao.upsert(stored.toEntity())
            Outcome.Success(stored)
        }
    }

    override suspend fun delete(eventId: EventId): Outcome<Unit> = storageOutcome {
        if (eventDao.findById(eventId.value) == null) {
            Outcome.Failed(Failure.NotFound)
        } else {
            eventDao.deleteById(eventId.value)
            Outcome.Success(Unit)
        }
    }
}

/** Room-backed [RelationshipRepository]. */
class RoomRelationshipRepository(
    private val database: ChronaDatabase,
    private val relationshipDao: TaskRelationshipDao = database.taskRelationshipDao(),
) : RelationshipRepository {

    override suspend fun listFor(taskIds: Set<TaskId>): Outcome<List<TaskRelationship>> = storage {
        if (taskIds.isEmpty()) {
            emptyList()
        } else {
            relationshipDao.findForTasks(taskIds.map { it.value }).map { it.toDomain() }
        }
    }

    override suspend fun listAll(): Outcome<List<TaskRelationship>> = storage {
        relationshipDao.findAll().map { it.toDomain() }
    }

    override suspend fun upsert(relationship: TaskRelationship): Outcome<TaskRelationship> = storage {
        relationshipDao.upsert(relationship.toEntity())
        relationship
    }

    override suspend fun delete(relationId: RelationId): Outcome<Unit> = storage {
        relationshipDao.deleteById(relationId.value)
    }

    override suspend fun replaceFor(
        source: TaskId,
        links: List<TaskRelationship>,
    ): Outcome<Unit> = storage {
        database.withTransaction {
            relationshipDao.deleteForSource(source.value)
            links.forEach { relationshipDao.upsert(it.toEntity()) }
        }
    }
}

/**
 * Room-backed [ScheduleRevisionRepository].
 *
 * Apply and undo each run inside one transaction and recheck the expected revision, so a stale
 * proposal is refused rather than overwriting newer work (AT-016, REC-003).
 */
class RoomScheduleRevisionRepository(
    private val database: ChronaDatabase,
    private val newId: () -> String,
    private val revisionDao: ScheduleRevisionDao = database.scheduleRevisionDao(),
    private val taskDao: TaskDao = database.taskDao(),
    private val statusLogDao: TaskStatusLogDao = database.taskStatusLogDao(),
) : com.chrona.app.domain.recovery.ScheduleRevisionRepository {

    override suspend fun current(): Outcome<com.chrona.app.domain.scheduling.ScheduleRevision?> = storage {
        revisionDao.latest()?.let { header ->
            header.toDomain(revisionDao.placements(header.id))
        }
    }

    override suspend fun apply(
        proposal: com.chrona.app.domain.scheduling.ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId> = storageOutcome {
        database.withTransaction {
            val current = revisionDao.latest()?.let { RevisionId(it.id) }
            when {
                current != expectedRevision ->
                    Outcome.Failed(
                        Failure.Conflict("The plan changed since this preview. Rebuild it to see the latest."),
                    )

                proposal.basis.revisionId != expectedRevision ->
                    Outcome.Failed(
                        Failure.Conflict("This preview is out of date. Rebuild it before applying."),
                    )

                else -> {
                    val staleTask = proposal.basis.taskVersions.entries.firstOrNull { (taskId, version) ->
                        val row = taskDao.findById(taskId.value)
                        row == null || row.version != version
                    }
                    if (staleTask != null) {
                        Outcome.Failed(
                            Failure.Conflict("A task changed since this preview. Rebuild the plan before applying."),
                        )
                    } else {
                        applyRevision(proposal, expectedRevision, reasonCode, appliedAt)
                    }
                }
            }
        }
    }

    private suspend fun applyRevision(
        proposal: com.chrona.app.domain.scheduling.ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId> {
        val revisionId = RevisionId(newId())
        revisionDao.insertRevision(
            com.chrona.app.data.local.ScheduleRevisionEntity(
                id = revisionId.value,
                basedOnRevisionId = expectedRevision?.value,
                reasonCode = reasonCode,
                appliedAtEpochMillis = appliedAt.toEpochMilli(),
            ),
        )
        revisionDao.insertPlacements(
            proposal.placements.map { it.toEntity(revisionId.value) },
        )
        // Accepting a schedule moves freshly planned work to PLANNED, with an audit row. Work the
        // user already started, finished or skipped is never touched (REC-003).
        val startsPlanned = com.chrona.app.domain.model.TaskStatus.PLANNED
        proposal.placements.map { it.taskId }.distinct().forEach { taskId ->
            val row = taskDao.findById(taskId.value) ?: return@forEach
            val domain = row.toDomain()
            if (domain.status == com.chrona.app.domain.model.TaskStatus.INBOX ||
                domain.status == com.chrona.app.domain.model.TaskStatus.POSTPONED
            ) {
                taskDao.applyStatusChange(
                    statusLogDao,
                    row,
                    domain.copy(status = startsPlanned, updatedAt = appliedAt),
                    ReasonCode.SCHEDULE_APPLIED,
                    appliedAt,
                )
            }
        }
        return Outcome.Success(revisionId)
    }

    override suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit> = storageOutcome {
        database.withTransaction {
            val header = revisionDao.findById(revisionId.value)
                ?: return@withTransaction Outcome.Failed(Failure.NotFound)
            val latest = revisionDao.latest()
            when {
                latest == null || latest.id != revisionId.value ->
                    Outcome.Failed(Failure.Conflict("Only the most recently applied plan can be undone."))

                // Newer edits, new tasks or moved commitments mean undoing would overwrite them.
                revisionDao.tasksUpdatedAfter(header.appliedAtEpochMillis) > 0 ->
                    Outcome.Failed(
                        Failure.Conflict(
                            "Something changed after this plan, so undoing would overwrite newer work. " +
                                "Build a new plan instead.",
                        ),
                    )

                else -> {
                    val undoneTasks = revisionDao.placements(revisionId.value).map { it.taskId }.distinct()
                    revisionDao.deleteRevision(revisionId.value)
                    val previousTasks = revisionDao.latest()
                        ?.let { revisionDao.placements(it.id).map { p -> p.taskId }.toSet() }
                        ?: emptySet()

                    undoneTasks.forEach { taskId ->
                        val row = taskDao.findById(taskId) ?: return@forEach
                        val domain = row.toDomain()
                        if (domain.status != com.chrona.app.domain.model.TaskStatus.PLANNED) return@forEach
                        val target = if (taskId in previousTasks) {
                            com.chrona.app.domain.model.TaskStatus.PLANNED
                        } else {
                            com.chrona.app.domain.model.TaskStatus.INBOX
                        }
                        taskDao.applyStatusChange(
                            statusLogDao,
                            row,
                            domain.copy(status = target, updatedAt = appliedAt),
                            ReasonCode.SCHEDULE_UNDONE,
                            appliedAt,
                        )
                    }
                    Outcome.Success(Unit)
                }
            }
        }
    }
}

/**
 * Runs a storage operation and converts an unexpected exception into a typed failure.
 *
 * Corruption and constraint violations are real conditions, not programming errors; the UI
 * must show "could not save" with a safe next step rather than crash.
 */
internal suspend inline fun <T> storage(crossinline block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Outcome.Failed(Failure.StorageFailure(error.message ?: "Local storage is unavailable."))
}

/**
 * Same as [storage], for operations that also report *expected* failures (not found,
 * conflict, rejected transition) as a typed outcome of their own.
 */
internal suspend inline fun <T> storageOutcome(
    crossinline block: suspend () -> Outcome<T>,
): Outcome<T> = when (val outer = storage { block() }) {
    is Outcome.Success -> outer.value
    is Outcome.Failed -> outer
}