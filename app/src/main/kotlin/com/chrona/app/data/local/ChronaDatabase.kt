package com.chrona.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Chrona's local store, schema version 4 (ADR-007, ADR-027, ADR-035, ADR-040).
 *
 * There is deliberately **no** `fallbackToDestructiveMigration()`. Real user task data must
 * never be dropped to make a schema change succeed; every future version needs an explicit
 * migration plus a migration test (DATA_MODEL_AND_INTERFACES.md §Persistence and lifecycle
 * rules).
 */
@Database(
    entities = [
        TaskEntity::class,
        CalendarEventEntity::class,
        TaskRelationshipEntity::class,
        TaskStatusLogEntity::class,
        ScheduleRevisionEntity::class,
        SchedulePlacementEntity::class,
        ReminderPresentationEntity::class,
        SimulationDayEntity::class,
        SimulationEventEntity::class,
        SimulationPlanEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class ChronaDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun taskRelationshipDao(): TaskRelationshipDao
    abstract fun taskStatusLogDao(): TaskStatusLogDao
    abstract fun scheduleRevisionDao(): ScheduleRevisionDao
    abstract fun reminderPresentationDao(): ReminderPresentationDao
    abstract fun simulationDao(): SimulationDao

    companion object {
        const val FILE_NAME: String = "chrona.db"

        /**
         * Adds the schedule-revision tables. Purely additive: existing task, event, relationship
         * and history rows are untouched (AT-031).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_revisions` (
                        `id` TEXT NOT NULL,
                        `based_on_revision_id` TEXT,
                        `reason_code` TEXT NOT NULL,
                        `applied_at_epoch_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_revisions_applied_at_epoch_millis` " +
                        "ON `schedule_revisions` (`applied_at_epoch_millis`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedule_placements` (
                        `rowid_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `revision_id` TEXT NOT NULL,
                        `task_id` TEXT NOT NULL,
                        `start_epoch_millis` INTEGER NOT NULL,
                        `start_zone_id` TEXT NOT NULL,
                        `end_epoch_millis` INTEGER NOT NULL,
                        `end_zone_id` TEXT NOT NULL,
                        `session_index` INTEGER NOT NULL,
                        FOREIGN KEY(`revision_id`) REFERENCES `schedule_revisions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_placements_revision_id` " +
                        "ON `schedule_placements` (`revision_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_placements_task_id` " +
                        "ON `schedule_placements` (`task_id`)",
                )
            }
        }

        /**
         * Adds the reminder-presentation history table. Purely additive: existing rows are
         * untouched. No foreign key, matching schedule placements: history is a record that
         * outlives the task it describes and is cleared by the P07 delete path.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminder_presentation_history` (
                        `id` TEXT NOT NULL,
                        `task_id` TEXT NOT NULL,
                        `scheduled_for_epoch_millis` INTEGER NOT NULL,
                        `surfaced_at_epoch_millis` INTEGER,
                        `surface_type` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `reason_code` TEXT NOT NULL,
                        `batch_group` TEXT,
                        `local_date_iso` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `created_at_epoch_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_presentation_history_task_id` " +
                        "ON `reminder_presentation_history` (`task_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_presentation_history_local_date_iso` " +
                        "ON `reminder_presentation_history` (`local_date_iso`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_presentation_history_created_at_epoch_millis` " +
                        "ON `reminder_presentation_history` (`created_at_epoch_millis`)",
                )
            }
        }

        /**
         * Adds the life-simulation record tables (ADR-040).
         *
         * Purely additive: no existing table is altered, so task, event, relationship, revision
         * and reminder rows are untouched. Simulation *tasks* are ordinary rows in `tasks` marked
         * by `project_label`, which is why reset can scope itself to one marker instead of
         * touching the user's data.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simulation_days` (
                        `run_id` TEXT NOT NULL,
                        `day_offset` INTEGER NOT NULL,
                        `date_iso` TEXT NOT NULL,
                        `reference_instant_epoch_millis` INTEGER NOT NULL,
                        `available_windows` INTEGER NOT NULL,
                        `lost_minutes` INTEGER NOT NULL,
                        `active_tasks` INTEGER NOT NULL,
                        `scheduled_tasks` INTEGER NOT NULL,
                        `placed_blocks` INTEGER NOT NULL,
                        `completed_tasks` INTEGER NOT NULL,
                        `missed_tasks` INTEGER NOT NULL,
                        `postponed_tasks` INTEGER NOT NULL,
                        `unscheduled_tasks` INTEGER NOT NULL,
                        `overdue_tasks` INTEGER NOT NULL,
                        `at_risk_tasks` INTEGER NOT NULL,
                        `fixed_commitments` INTEGER NOT NULL,
                        `conflict_count` INTEGER NOT NULL,
                        `moved_tasks` INTEGER NOT NULL,
                        `newly_placed_tasks` INTEGER NOT NULL,
                        `revision_count` INTEGER NOT NULL,
                        `scenario_note` TEXT NOT NULL,
                        `unplaced_reasons` TEXT NOT NULL,
                        `runtime_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`run_id`, `day_offset`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simulation_events` (
                        `rowid_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` TEXT NOT NULL,
                        `day_offset` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `task_id` TEXT,
                        `detail` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simulation_events_run_id` " +
                        "ON `simulation_events` (`run_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_simulation_events_day_offset` " +
                        "ON `simulation_events` (`day_offset`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `simulation_plans` (
                        `run_id` TEXT NOT NULL,
                        `placements` TEXT NOT NULL,
                        PRIMARY KEY(`run_id`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
