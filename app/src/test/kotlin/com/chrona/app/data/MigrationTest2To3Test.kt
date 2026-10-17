package com.chrona.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.chrona.app.data.local.ChronaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the v2→v3 migration is additive and lossless.
 *
 * v3 only adds `reminder_presentation_history`, so every existing task, revision and placement
 * row must survive untouched. As with v1→v2, the migration runs against a real SQLite database
 * built from the frozen v2 DDL, exactly as an upgrade on a user's device would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest2To3Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Frozen v2 `tasks` DDL (unchanged since v1). */
    private val v2Tasks = "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT " +
        "NOT NULL, `details` TEXT, `priority` TEXT NOT NULL, `estimated_duration_minutes` INTEGER, " +
        "`deadline_instant_epoch_millis` INTEGER, `deadline_zone_id` TEXT, `requested_date_iso` TEXT, " +
        "`earliest_start_epoch_millis` INTEGER, `earliest_start_zone_id` TEXT, `preferred_start` TEXT, " +
        "`preferred_end` TEXT, `flexibility` TEXT NOT NULL, `fixed_start_epoch_millis` INTEGER, " +
        "`fixed_end_epoch_millis` INTEGER, `fixed_zone_id` TEXT, `splittable` INTEGER NOT NULL, " +
        "`minimum_session_minutes` INTEGER, `status` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
        "`project_label` TEXT, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` " +
        "INTEGER NOT NULL, `completed_at_epoch_millis` INTEGER, `postponed_until_epoch_millis` INTEGER, " +
        "`postponed_until_zone_id` TEXT, `version` INTEGER NOT NULL, `assumptions` TEXT NOT NULL " +
        "DEFAULT '', PRIMARY KEY(`id`))"

    private val v2ScheduleRevisions = "CREATE TABLE IF NOT EXISTS `schedule_revisions` " +
        "(`id` TEXT NOT NULL, `based_on_revision_id` TEXT, `reason_code` TEXT NOT NULL, " +
        "`applied_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    private val v2SchedulePlacements = "CREATE TABLE IF NOT EXISTS `schedule_placements` " +
        "(`rowid_local` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `revision_id` TEXT NOT NULL, " +
        "`task_id` TEXT NOT NULL, `start_epoch_millis` INTEGER NOT NULL, `start_zone_id` TEXT NOT NULL, " +
        "`end_epoch_millis` INTEGER NOT NULL, `end_zone_id` TEXT NOT NULL, `session_index` INTEGER NOT NULL)"

    private fun openV2(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-2-3-test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v2Tasks)
                    db.execSQL(v2ScheduleRevisions)
                    db.execSQL(v2SchedulePlacements)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migration from 2 to 3 preserves rows and adds reminder history`() {
        val db = openV2()
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, priority, flexibility, splittable, status, origin, " +
                "created_at_epoch_millis, updated_at_epoch_millis, version, assumptions) " +
                "VALUES ('t1', 'Old task', 'IMPORTANT', 'FLEXIBLE', 0, 'INBOX', 'TYPED', 0, 0, 1, '')",
        )
        db.execSQL(
            "INSERT INTO schedule_revisions (id, based_on_revision_id, reason_code, applied_at_epoch_millis) " +
                "VALUES ('r1', NULL, 'SCHEDULE_APPLIED', 0)",
        )
        db.execSQL(
            "INSERT INTO schedule_placements " +
                "(revision_id, task_id, start_epoch_millis, start_zone_id, end_epoch_millis, " +
                "end_zone_id, session_index) VALUES ('r1', 't1', 0, 'UTC', 60000, 'UTC', 0)",
        )

        ChronaDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT title FROM tasks WHERE id = 't1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old task", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM schedule_placements WHERE revision_id = 'r1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name = 'reminder_presentation_history'",
        ).use { cursor ->
            assertEquals(1, cursor.count)
        }
        db.query("PRAGMA table_info(reminder_presentation_history)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                listOf(
                    "id", "task_id", "scheduled_for_epoch_millis", "surfaced_at_epoch_millis",
                    "surface_type", "outcome", "reason_code", "batch_group", "local_date_iso",
                    "priority", "created_at_epoch_millis",
                ),
                columns,
            )
        }
        db.close()
    }
}
