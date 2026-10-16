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
 * Proves the v1→v2 migration is additive and lossless (AT-031).
 *
 * The v2 change only adds the schedule-revision tables, so every existing task row must survive
 * untouched. The migration runs against a real SQLite database created from the frozen v1 task
 * schema, which is exactly what an upgrade on a user's device will do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest1To2Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Frozen v1 `tasks` DDL (from schemas/1.json). v1 is history and never changes. */
    private val v1Tasks = "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT " +
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

    private fun openV1(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(v1Tasks)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migration from 1 to 2 preserves tasks and adds the schedule tables`() {
        val db = openV1()
        db.execSQL(
            "INSERT INTO tasks " +
                "(id, title, priority, flexibility, splittable, status, origin, " +
                "created_at_epoch_millis, updated_at_epoch_millis, version, assumptions) " +
                "VALUES ('t1', 'Old task', 'IMPORTANT', 'FLEXIBLE', 0, 'INBOX', 'TYPED', 0, 0, 1, '')",
        )

        ChronaDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT title FROM tasks WHERE id = 't1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old task", cursor.getString(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('schedule_revisions', 'schedule_placements')",
        ).use { cursor ->
            assertEquals(2, cursor.count)
        }
        db.query("PRAGMA table_info(schedule_placements)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                listOf(
                    "rowid_local", "revision_id", "task_id", "start_epoch_millis",
                    "start_zone_id", "end_epoch_millis", "end_zone_id", "session_index",
                ),
                columns,
            )
        }
        db.close()
    }
}
