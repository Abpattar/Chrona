package com.chrona.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    /**
     * Active-surface listing.
     *
     * Ordering is explicit and total: in-progress work first, then by deadline, then by
     * creation time, then by id. Without a total order two runs could return the same rows in
     * different sequences and the UI would appear to shuffle. An optional limit is applied by
     * the repository rather than in SQL so Room can verify the query.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status IN (:statuses)
        ORDER BY
            CASE status WHEN 'IN_PROGRESS' THEN 0 ELSE 1 END,
            CASE WHEN deadline_instant_epoch_millis IS NULL THEN 1 ELSE 0 END,
            deadline_instant_epoch_millis ASC,
            created_at_epoch_millis ASC,
            id ASC
        """,
    )
    suspend fun findByStatuses(statuses: List<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE project_label = :label ORDER BY created_at_epoch_millis ASC, id ASC")
    suspend fun findByProjectLabel(label: String): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE requested_date_iso <= :dateIso
          AND status NOT IN ('COMPLETED', 'SKIPPED', 'ARCHIVED')
        ORDER BY requested_date_iso ASC, id ASC
        """,
    )
    suspend fun findRequestedOnOrBefore(dateIso: String): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY updated_at_epoch_millis DESC, id ASC")
    suspend fun findAll(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    /**
     * Insert, or do nothing if the id already exists.
     *
     * Used by the life simulation so replaying a day cannot duplicate a task, reset its status,
     * or bump its version. Deliberately *not* an upsert: an upsert would silently overwrite an
     * edit made since the row was first written.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Conditional update used for optimistic concurrency.
     *
     * Returns 0 when the row moved on since the caller read it, which the repository turns
     * into a typed `Conflict` instead of silently overwriting newer work.
     */
    @Query(
        """
        UPDATE tasks SET
            title = :title, details = :details, priority = :priority,
            estimated_duration_minutes = :estimatedDurationMinutes,
            deadline_instant_epoch_millis = :deadlineEpochMillis,
            deadline_zone_id = :deadlineZoneId,
            requested_date_iso = :requestedDateIso,
            earliest_start_epoch_millis = :earliestStartEpochMillis,
            earliest_start_zone_id = :earliestStartZoneId,
            preferred_start = :preferredStart, preferred_end = :preferredEnd,
            flexibility = :flexibility,
            fixed_start_epoch_millis = :fixedStartEpochMillis,
            fixed_end_epoch_millis = :fixedEndEpochMillis, fixed_zone_id = :fixedZoneId,
            splittable = :splittable, minimum_session_minutes = :minimumSessionMinutes,
            status = :status, project_label = :projectLabel,
            updated_at_epoch_millis = :updatedAtEpochMillis,
            completed_at_epoch_millis = :completedAtEpochMillis,
            postponed_until_epoch_millis = :postponedUntilEpochMillis,
            postponed_until_zone_id = :postponedUntilZoneId,
            assumptions = :assumptions,
            version = :version + 1
        WHERE id = :id AND version = :expectedVersion
        """,
    )
    suspend fun updateIfVersionMatches(
        id: String,
        expectedVersion: Long,
        title: String,
        details: String?,
        priority: String,
        estimatedDurationMinutes: Int?,
        deadlineEpochMillis: Long?,
        deadlineZoneId: String?,
        requestedDateIso: String?,
        earliestStartEpochMillis: Long?,
        earliestStartZoneId: String?,
        preferredStart: String?,
        preferredEnd: String?,
        flexibility: String,
        fixedStartEpochMillis: Long?,
        fixedEndEpochMillis: Long?,
        fixedZoneId: String?,
        splittable: Boolean,
        minimumSessionMinutes: Int?,
        status: String,
        projectLabel: String?,
        updatedAtEpochMillis: Long,
        completedAtEpochMillis: Long?,
        postponedUntilEpochMillis: Long?,
        postponedUntilZoneId: String?,
        assumptions: String,
        version: Long,
    ): Int
}

@Dao
interface CalendarEventDao {

    /** Overlap query on absolute instants, so zone differences cannot hide a collision. */
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE start_at_epoch_millis < :endEpochMillis
          AND end_at_epoch_millis > :startEpochMillis
        ORDER BY start_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun findOverlapping(startEpochMillis: Long, endEpochMillis: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events ORDER BY start_at_epoch_millis ASC, id ASC")
    suspend fun findAll(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun findById(id: String): CalendarEventEntity?

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun count(): Int
}

@Dao
interface TaskRelationshipDao {

    @Query(
        """
        SELECT * FROM task_relationships
        WHERE source_task_id IN (:taskIds) OR target_task_id IN (:taskIds)
        """,
    )
    suspend fun findForTasks(taskIds: List<String>): List<TaskRelationshipEntity>

    @Query("SELECT * FROM task_relationships")
    suspend fun findAll(): List<TaskRelationshipEntity>

    @Upsert
    suspend fun upsert(relationship: TaskRelationshipEntity)

    @Query("DELETE FROM task_relationships WHERE id = :relationId")
    suspend fun deleteById(relationId: String)

    @Query("DELETE FROM task_relationships WHERE source_task_id = :sourceTaskId")
    suspend fun deleteForSource(sourceTaskId: String)

    /**
     * Removes every relationship touching any of [taskIds].
     *
     * The simulation's reset path needs both columns because a relationship owned by a run may
     * point at a task from another run; filtering only the source column would leave those edges
     * behind once their target is deleted.
     */
    @Query(
        """
        DELETE FROM task_relationships
        WHERE source_task_id IN (:taskIds) OR target_task_id IN (:taskIds)
        """,
    )
    suspend fun deleteForTasks(taskIds: List<String>)

    @Query("SELECT COUNT(*) FROM task_relationships")
    suspend fun count(): Int
}

@Dao
interface TaskStatusLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TaskStatusLogEntity)
    @Query("SELECT * FROM task_status_log WHERE task_id = :taskId ORDER BY changed_at_epoch_millis ASC, rowid_local ASC")
    suspend fun findForTask(taskId: String): List<TaskStatusLogEntity>

    @Query("SELECT * FROM task_status_log ORDER BY changed_at_epoch_millis DESC, rowid_local DESC LIMIT :limit")
    suspend fun findRecent(limit: Int): List<TaskStatusLogEntity>

    /**
     * Clears history for the given tasks.
     *
     * Status rows cascade when their task is deleted, so this only matters for a reset that has
     * already removed the tasks; keeping it makes the reset explicit rather than relying on
     * foreign-key timing.
     */
    @Query("DELETE FROM task_status_log WHERE task_id IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<String>)

    /**
     * Recent status history joined to the task's current title.
     *
     * The join is what lets History say "DBMS revision was completed" rather than showing a bare
     * task id. A log row outlives its subject only while the task exists, and the cascade means
     * a deleted task takes its history with it, so an inner join loses nothing.
     */
    @Query(
        """
        SELECT l.task_id AS task_id, t.title AS task_title, l.from_status AS from_status,
               l.to_status AS to_status, l.reason_code AS reason_code,
               l.changed_at_epoch_millis AS changed_at_epoch_millis
        FROM task_status_log l
        INNER JOIN tasks t ON t.id = l.task_id
        ORDER BY l.changed_at_epoch_millis DESC, l.rowid_local DESC
        LIMIT :limit
        """,
    )
    suspend fun findRecentWithTitles(limit: Int): List<TaskStatusLogWithTitle>

    @Query("SELECT COUNT(*) FROM task_status_log")
    suspend fun count(): Int
}

@Dao
interface ScheduleRevisionDao {

    /** The current plan is the most recently applied revision. */
    @Query("SELECT * FROM schedule_revisions ORDER BY applied_at_epoch_millis DESC, id DESC LIMIT 1")
    suspend fun latest(): ScheduleRevisionEntity?

    /**
     * Revision history, newest first.
     *
     * History is the record of what the planner did and why, so it needs the whole chain rather
     * than only the current head: a user asking "why did my evening change?" needs the revision
     * that changed it, not just the one in force now.
     */
    @Query("SELECT * FROM schedule_revisions ORDER BY applied_at_epoch_millis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ScheduleRevisionEntity>

    @Query("SELECT COUNT(*) FROM schedule_placements WHERE revision_id = :revisionId")
    suspend fun placementCountFor(revisionId: String): Int

    @Query("SELECT * FROM schedule_revisions WHERE id = :id")
    suspend fun findById(id: String): ScheduleRevisionEntity?

    @Query("SELECT * FROM schedule_placements WHERE revision_id = :revisionId ORDER BY start_epoch_millis ASC, session_index ASC, rowid_local ASC")
    suspend fun placements(revisionId: String): List<SchedulePlacementEntity>

    @Query("SELECT COUNT(*) FROM schedule_revisions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM schedule_placements")
    suspend fun placementCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ScheduleRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlacements(placements: List<SchedulePlacementEntity>)

    @Query("DELETE FROM schedule_revisions WHERE id = :id")
    suspend fun deleteRevision(id: String)

    /** Safety check for undo: any task edited strictly after a revision makes it unsafe to undo. */
    @Query("SELECT COUNT(*) FROM tasks WHERE updated_at_epoch_millis > :epochMillis")
    suspend fun tasksUpdatedAfter(epochMillis: Long): Int
}

@Dao
interface ReminderPresentationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ReminderPresentationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<ReminderPresentationEntity>)

    @Query(
        """
        SELECT * FROM reminder_presentation_history
        WHERE task_id = :taskId
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun findForTask(taskId: String): List<ReminderPresentationEntity>

    /** History for a candidate set, newest-first within a task is left to the caller to sort. */
    @Query(
        """
        SELECT * FROM reminder_presentation_history
        WHERE task_id IN (:taskIds)
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun findForTasks(taskIds: List<String>): List<ReminderPresentationEntity>

    /**
     * Non-urgent cards that actually reached a surface on a local date.
     *
     * Counted by distinct card (`batch_group`), so a batch of related tasks costs one, not one
     * per task, and suppressed rows never consume the cap.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT batch_group) FROM reminder_presentation_history
        WHERE local_date_iso = :localDateIso
          AND priority = 'LOW'
          AND outcome = 'SHOWN'
        """,
    )
    suspend fun countNonUrgentSurfacedOn(localDateIso: String): Int

    @Query("SELECT COUNT(*) FROM reminder_presentation_history")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM reminder_presentation_history
        ORDER BY created_at_epoch_millis DESC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun findRecent(limit: Int): List<ReminderPresentationEntity>

    @Query("DELETE FROM reminder_presentation_history")
    suspend fun deleteAll()
}