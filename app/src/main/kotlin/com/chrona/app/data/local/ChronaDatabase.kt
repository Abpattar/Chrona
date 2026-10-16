package com.chrona.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Chrona's local store, schema version 2 (ADR-007, ADR-027).
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
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChronaDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun taskRelationshipDao(): TaskRelationshipDao
    abstract fun taskStatusLogDao(): TaskStatusLogDao
    abstract fun scheduleRevisionDao(): ScheduleRevisionDao

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
    }
}
