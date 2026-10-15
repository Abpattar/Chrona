package com.chrona.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * Chrona's local store, schema version 1 (ADR-007).
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChronaDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun taskRelationshipDao(): TaskRelationshipDao
    abstract fun taskStatusLogDao(): TaskStatusLogDao

    companion object {
        const val FILE_NAME: String = "chrona.db"
    }
}