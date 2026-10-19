package com.chrona.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Per-day outcome of a life simulation (schema v4).
 *
 * The primary key is `(run_id, day_offset)`, not a synthetic row id. That is what makes re-running
 * a day idempotent: Room's REPLACE conflict strategy deletes the conflicting row and inserts the
 * new one, so with a synthetic key every insert would silently delete the *previous* day and a
 * 14-day run would end up holding only the last day.
 *
 * The report columns are stored rather than recomputed on read, because a day that has already
 * happened must keep reporting what actually happened: if the task pool is later edited, today's
 * numbers must not silently change underneath the user.
 */
@Entity(tableName = "simulation_days", primaryKeys = ["run_id", "day_offset"])
data class SimulationDayEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "day_offset") val dayOffset: Int,
    @ColumnInfo(name = "date_iso") val dateIso: String,
    @ColumnInfo(name = "reference_instant_epoch_millis") val referenceEpochMillis: Long,
    @ColumnInfo(name = "available_windows") val availableWindows: Int,
    @ColumnInfo(name = "lost_minutes") val lostMinutes: Int,
    @ColumnInfo(name = "active_tasks") val activeTasks: Int,
    @ColumnInfo(name = "scheduled_tasks") val scheduledTasks: Int,
    @ColumnInfo(name = "placed_blocks") val placedBlocks: Int,
    @ColumnInfo(name = "completed_tasks") val completedTasks: Int,
    @ColumnInfo(name = "missed_tasks") val missedTasks: Int,
    @ColumnInfo(name = "postponed_tasks") val postponedTasks: Int,
    @ColumnInfo(name = "unscheduled_tasks") val unscheduledTasks: Int,
    @ColumnInfo(name = "overdue_tasks") val overdueTasks: Int,
    @ColumnInfo(name = "at_risk_tasks") val atRiskTasks: Int,
    @ColumnInfo(name = "fixed_commitments") val fixedCommitments: Int,
    @ColumnInfo(name = "conflict_count") val conflictCount: Int,
    @ColumnInfo(name = "moved_tasks") val movedTasks: Int,
    @ColumnInfo(name = "newly_placed_tasks") val newlyPlacedTasks: Int,
    @ColumnInfo(name = "revision_count") val revisionCount: Int,
    @ColumnInfo(name = "scenario_note") val scenarioNote: String,
    /** Unplaced-reason name to count, e.g. `DEADLINE_INFEASIBLE=3`. */
    @ColumnInfo(name = "unplaced_reasons") val unplacedReasons: String,
    @ColumnInfo(name = "runtime_millis") val runtimeMillis: Long,
)

/**
 * One notable thing that happened during a run: an arrival, a completion, a disruption, a move.
 *
 * This is the change history the Simulation screen renders. It is append-only and is cleared only
 * by an explicit reset of that run.
 */
@Entity(
    tableName = "simulation_events",
    indices = [Index("run_id"), Index("day_offset")],
)
data class SimulationEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid_local") val rowId: Long = 0L,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "day_offset") val dayOffset: Int,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "detail") val detail: String,
)

/**
 * The most recent plan, kept only so the next step can diff against it.
 *
 * Replaces an array of revisions with one row per run. A simulation diffs yesterday's plan
 * against today's; it does not need the full history of every plan to do that, and keeping one
 * row keeps reset to a single delete.
 */
@Entity(tableName = "simulation_plans")
data class SimulationPlanEntity(
    @PrimaryKey @ColumnInfo(name = "run_id") val runId: String,
    /** Compact `taskId|startEpochMillis|endEpochMillis|sessionIndex;` encoding. */
    @ColumnInfo(name = "placements") val placements: String,
)

@Dao
interface SimulationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: SimulationDayEntity)

    @Query("SELECT * FROM simulation_days WHERE run_id = :runId ORDER BY day_offset ASC")
    suspend fun days(runId: String): List<SimulationDayEntity>

    @Insert
    suspend fun insertEvent(event: SimulationEventEntity)

    @Query("SELECT * FROM simulation_events WHERE run_id = :runId ORDER BY rowid_local ASC")
    suspend fun events(runId: String): List<SimulationEventEntity>

    @Query("SELECT * FROM simulation_plans WHERE run_id = :runId")
    suspend fun plan(runId: String): SimulationPlanEntity?

    @Query("INSERT OR REPLACE INTO simulation_plans (run_id, placements) VALUES (:runId, :placements)")
    suspend fun savePlan(runId: String, placements: String)

    @Query("SELECT COUNT(*) FROM simulation_days WHERE run_id = :runId")
    suspend fun dayCount(runId: String): Int

    /**
     * Removes only this run's records.
     *
     * Every statement is filtered by `run_id`. The simulation's *tasks* live in the shared
     * `tasks` table and are removed by `RoomSimulationStore.reset`, which matches on the
     * reserved simulation label — never on "all tasks", so a reset cannot touch real user data.
     */
    @Query("DELETE FROM simulation_days WHERE run_id = :runId")
    suspend fun deleteDays(runId: String)

    @Query("DELETE FROM simulation_events WHERE run_id = :runId")
    suspend fun deleteEvents(runId: String)

    @Query("DELETE FROM simulation_plans WHERE run_id = :runId")
    suspend fun deletePlan(runId: String)

    @Query("SELECT COUNT(*) FROM tasks WHERE project_label = :label")
    suspend fun countSimulationTasks(label: String): Int
}

/**
 * A status-history row joined with the title it belongs to.
 *
 * A projection, not a table: History needs to say what happened to a *named* task, and the
 * status log deliberately stores only machine codes so the join happens at read time rather than
 * duplicating user text into an audit table.
 */
data class TaskStatusLogWithTitle(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "task_title") val taskTitle: String,
    @ColumnInfo(name = "from_status") val fromStatus: String?,
    @ColumnInfo(name = "to_status") val toStatus: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String,
    @ColumnInfo(name = "changed_at_epoch_millis") val changedAtEpochMillis: Long,
)
