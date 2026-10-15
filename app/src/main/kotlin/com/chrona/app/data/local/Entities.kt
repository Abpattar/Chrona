package com.chrona.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room storage types (DATA_MODEL_AND_INTERFACES.md).
 *
 * Entity classes exist only inside the data layer. The mappers to and from domain models
 * live in `EntityMappers.kt`, so these declarations stay pure storage with no reference to
 * domain types at all. Enums are stored by name so reordering the Kotlin enum cannot silently
 * reinterpret existing rows.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("status"),
        Index("deadline_instant_epoch_millis"),
        Index("project_label"),
        Index("updated_at_epoch_millis"),
    ],
)
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,

    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "details") val details: String?,

    @ColumnInfo(name = "priority") val priority: String,
    @ColumnInfo(name = "estimated_duration_minutes") val estimatedDurationMinutes: Int?,
    @ColumnInfo(name = "deadline_instant_epoch_millis") val deadlineEpochMillis: Long?,
    @ColumnInfo(name = "deadline_zone_id") val deadlineZoneId: String?,
    @ColumnInfo(name = "requested_date_iso") val requestedDateIso: String?,
    @ColumnInfo(name = "earliest_start_epoch_millis") val earliestStartEpochMillis: Long?,
    @ColumnInfo(name = "earliest_start_zone_id") val earliestStartZoneId: String?,
    @ColumnInfo(name = "preferred_start") val preferredStart: String?,
    @ColumnInfo(name = "preferred_end") val preferredEnd: String?,
    @ColumnInfo(name = "flexibility") val flexibility: String,
    @ColumnInfo(name = "fixed_start_epoch_millis") val fixedStartEpochMillis: Long?,
    @ColumnInfo(name = "fixed_end_epoch_millis") val fixedEndEpochMillis: Long?,
    @ColumnInfo(name = "fixed_zone_id") val fixedZoneId: String?,
    @ColumnInfo(name = "splittable") val splittable: Boolean,
    @ColumnInfo(name = "minimum_session_minutes") val minimumSessionMinutes: Int?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "project_label") val projectLabel: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "postponed_until_epoch_millis") val postponedUntilEpochMillis: Long?,
    @ColumnInfo(name = "postponed_until_zone_id") val postponedUntilZoneId: String?,
    @ColumnInfo(name = "version") val version: Long,

    /** Minimal, versioned interpretation text. No audio, no model traces. */
    @ColumnInfo(name = "assumptions", defaultValue = "") val assumptions: String,
)

/**
 * Storage for a manually entered fixed commitment.
 *
 * A calendar provider source would be added here, but only after an approved decision
 * (ADR-008). `source` exists so that day is possible without a schema break.
 */
@Entity(
    tableName = "calendar_events",
    indices = [
        Index("start_at_epoch_millis"),
        Index("updated_at_epoch_millis"),
    ],
)
data class CalendarEventEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "details") val details: String?,
    @ColumnInfo(name = "start_at_epoch_millis") val startAtEpochMillis: Long,
    @ColumnInfo(name = "end_at_epoch_millis") val endAtEpochMillis: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "is_fixed") val isFixed: Boolean,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "version") val version: Long,
)

/**
 * Storage for a typed relationship.
 *
 * Both foreign keys cascade, so deleting a task can never leave a dangling relation
 * (DATA_MODEL_AND_INTERFACES.md §Persistence).
 */
@Entity(
    tableName = "task_relationships",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("source_task_id"),
        Index("target_task_id"),
        Index(value = ["source_task_id", "target_task_id", "type"], unique = true),
    ],
)
data class TaskRelationshipEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "source_task_id") val sourceTaskId: String,
    @ColumnInfo(name = "target_task_id") val targetTaskId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "explanation") val explanation: String?,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "confirmed_at_epoch_millis") val confirmedAtEpochMillis: Long?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

/**
 * Append-only status history.
 *
 * The audit trail that makes "dismissing a card is not completing a task" provable rather
 * than assumed. Rows cascade away with their task, so History can never outlive its subject.
 */
@Entity(
    tableName = "task_status_log",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("task_id"),
        Index("changed_at_epoch_millis"),
    ],
)
data class TaskStatusLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid_local") val rowId: Long = 0L,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "from_status") val fromStatus: String?,
    @ColumnInfo(name = "to_status") val toStatus: String,
    /** Stable machine code, never raw user text. */
    @ColumnInfo(name = "reason_code") val reasonCode: String,
    @ColumnInfo(name = "changed_at_epoch_millis") val changedAtEpochMillis: Long,
)

/** Separator for the flattened [TaskEntity.assumptions] list. */
internal const val ASSUMPTION_SEPARATOR: String = "\n"