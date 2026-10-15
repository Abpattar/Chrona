package com.chrona.app.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A half-open local-time window: [start] inclusive, [end] exclusive.
 *
 * Used for soft preferred windows and for generated schedule blocks. A window is never a
 * deadline; keeping them as separate types stops the most common planning bug.
 */
data class TimeWindow(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(end > start) { "Window end ($end) must be after start ($start)" }
    }

    val duration: Duration get() = Duration.between(start, end)

    operator fun contains(time: LocalTime): Boolean = time >= start && time < end
}

/** Absolute, timezone-aware interval used for fixed commitments and assignments. */
data class TimeRange(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    init {
        require(end > start) { "Range end ($end) must be after start ($start)" }
    }

    /** Overlap on absolute instants, so zone differences cannot create phantom collisions. */
    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    operator fun contains(instant: Instant): Boolean =
        !instant.isBefore(start.toInstant()) && instant.isBefore(end.toInstant())
}

/**
 * The task record (DATA_MODEL_AND_INTERFACES.md §Task).
 *
 * Immutable: every mutation returns a new instance, which is what lets repositories use
 * optimistic concurrency on [version] safely.
 */
data class Task(
    val id: TaskId,
    val title: String,
    val details: String? = null,
    val priority: Priority = Priority.IMPORTANT,
    /** Absent while an inbox item still needs clarification. */
    val estimatedDuration: Duration? = null,
    /** Absolute latest acceptable instant. Distinct from [preferredWindow]. */
    val deadline: ZonedDateTime? = null,
    /** Date-only request. Distinct from an instant [deadline]. */
    val requestedDate: LocalDate? = null,
    val earliestStart: ZonedDateTime? = null,
    /** Soft local-time preference. Missing a preference is not a hard conflict. */
    val preferredWindow: TimeWindow? = null,
    val flexibility: Flexibility = Flexibility.FLEXIBLE,
    /** Fixed tasks must carry an explicit start/end. */
    val fixedStart: ZonedDateTime? = null,
    val fixedEnd: ZonedDateTime? = null,
    val splittable: Boolean = false,
    /** Required only when [splittable] is true. */
    val minimumSession: Duration? = null,
    val status: TaskStatus = TaskStatus.INBOX,
    val origin: TaskOrigin = TaskOrigin.TYPED,
    /** One simple grouping label. Not a project subsystem. */
    val projectLabel: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val postponedUntil: ZonedDateTime? = null,
    /** Optimistic concurrency counter for edits and proposals. */
    val version: Long = 1L,
    /** Minimised, versioned text describing interpretation assumptions. Never a model trace. */
    val assumptions: List<String> = emptyList(),
) {
    /** Absolute interval for a FIXED task, or null when the task is flexible. */
    val fixedRange: TimeRange?
        get() = if (flexibility == Flexibility.FIXED && fixedStart != null && fixedEnd != null) {
            TimeRange(fixedStart, fixedEnd)
        } else {
            null
        }
}

/**
 * A manually entered fixed commitment (DATA_MODEL_AND_INTERFACES.md §CalendarEvent).
 *
 * Fixed commitments are hard constraints: automatic recovery must never move one.
 */
data class CalendarEvent(
    val id: EventId,
    val title: String,
    val details: String? = null,
    val startAt: ZonedDateTime,
    val endAt: ZonedDateTime,
    val zoneId: String,
    val isFixed: Boolean = true,
    val source: EventSource = EventSource.MANUAL,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1L,
) {
    val range: TimeRange get() = TimeRange(startAt, endAt)
}

/** How a relationship edge came to exist. A suggestion is not a constraint until confirmed. */
enum class RelationCreatedBy { USER, SUGGESTED }

/**
 * A typed link between two tasks (DATA_MODEL_AND_INTERFACES.md §TaskRelationship).
 *
 * Direction is canonical: `sourceTaskId DEPENDS_ON targetTaskId` means **source waits for
 * target**. The same direction is used by the graph, editor, validation and scheduler.
 */
data class TaskRelationship(
    val id: RelationId,
    val sourceTaskId: TaskId,
    val targetTaskId: TaskId,
    val type: RelationType,
    val label: String? = null,
    val explanation: String? = null,
    val createdBy: RelationCreatedBy = RelationCreatedBy.USER,
    /** A suggested hard dependency stays inactive until this is set. */
    val confirmedAt: Instant? = null,
    val createdAt: Instant,
) {
    /** Only a confirmed edge constrains scheduling order. */
    val isActiveHardDependency: Boolean
        get() = type.isHardDependency && confirmedAt != null
}