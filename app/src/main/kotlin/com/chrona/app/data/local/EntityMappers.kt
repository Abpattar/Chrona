package com.chrona.app.data.local

import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.EventId
import com.chrona.app.domain.model.EventSource
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TimeWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The only place storage rows and domain records meet.
 *
 * Keeping mappers out of the entity declarations keeps the schema readable and means a
 * storage representation change touches exactly one file per entity. Room entities never
 * cross into domain, use-case or UI code (SHARED_CONTRACT.md §Repository contract).
 */

fun TaskEntity.toDomain(): Task = Task(
    id = TaskId(id),
    title = title,
    details = details,
    priority = Priority.valueOf(priority),
    estimatedDuration = estimatedDurationMinutes?.let { Duration.ofMinutes(it.toLong()) },
    deadline = deadlineEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zoneOrUtc(deadlineZoneId))
    },
    requestedDate = requestedDateIso?.let(LocalDate::parse),
    earliestStart = earliestStartEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zoneOrUtc(earliestStartZoneId))
    },
    preferredWindow = preferredStart?.let { start ->
        preferredEnd?.let { end -> TimeWindow(LocalTime.parse(start), LocalTime.parse(end)) }
    },
    flexibility = Flexibility.valueOf(flexibility),
    fixedStart = fixedStartEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zoneOrUtc(fixedZoneId))
    },
    fixedEnd = fixedEndEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zoneOrUtc(fixedZoneId))
    },
    splittable = splittable,
    minimumSession = minimumSessionMinutes?.let { Duration.ofMinutes(it.toLong()) },
    status = com.chrona.app.domain.model.TaskStatus.valueOf(status),
    origin = TaskOrigin.valueOf(origin),
    projectLabel = projectLabel,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
    postponedUntil = postponedUntilEpochMillis?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zoneOrUtc(postponedUntilZoneId))
    },
    version = version,
    assumptions = if (assumptions.isBlank()) {
        emptyList()
    } else {
        assumptions.split(ASSUMPTION_SEPARATOR)
    },
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id.value,
    title = title.trim(),
    details = details?.trim()?.takeIf { it.isNotEmpty() },
    priority = priority.name,
    estimatedDurationMinutes = estimatedDuration?.toMinutes()?.toInt(),
    deadlineEpochMillis = deadline?.toInstant()?.toEpochMilli(),
    deadlineZoneId = deadline?.zone?.id,
    requestedDateIso = requestedDate?.toString(),
    earliestStartEpochMillis = earliestStart?.toInstant()?.toEpochMilli(),
    earliestStartZoneId = earliestStart?.zone?.id,
    preferredStart = preferredWindow?.start?.toString(),
    preferredEnd = preferredWindow?.end?.toString(),
    flexibility = flexibility.name,
    fixedStartEpochMillis = fixedStart?.toInstant()?.toEpochMilli(),
    fixedEndEpochMillis = fixedEnd?.toInstant()?.toEpochMilli(),
    fixedZoneId = fixedStart?.zone?.id,
    splittable = splittable,
    minimumSessionMinutes = minimumSession?.toMinutes()?.toInt(),
    status = status.name,
    origin = origin.name,
    projectLabel = projectLabel?.trim()?.takeIf { it.isNotEmpty() },
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    completedAtEpochMillis = completedAt?.toEpochMilli(),
    postponedUntilEpochMillis = postponedUntil?.toInstant()?.toEpochMilli(),
    postponedUntilZoneId = postponedUntil?.zone?.id,
    version = version,
    assumptions = assumptions.joinToString(ASSUMPTION_SEPARATOR),
)

fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = EventId(id),
    title = title,
    details = details,
    startAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startAtEpochMillis), zoneOrUtc(zoneId)),
    endAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endAtEpochMillis), zoneOrUtc(zoneId)),
    zoneId = zoneId,
    isFixed = isFixed,
    source = EventSource.valueOf(source),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    version = version,
)

fun CalendarEvent.toEntity(): CalendarEventEntity = CalendarEventEntity(
    id = id.value,
    title = title.trim(),
    details = details?.trim()?.takeIf { it.isNotEmpty() },
    startAtEpochMillis = startAt.toInstant().toEpochMilli(),
    endAtEpochMillis = endAt.toInstant().toEpochMilli(),
    zoneId = zoneId,
    isFixed = isFixed,
    source = source.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    version = version,
)

fun TaskRelationshipEntity.toDomain(): TaskRelationship = TaskRelationship(
    id = RelationId(id),
    sourceTaskId = TaskId(sourceTaskId),
    targetTaskId = TaskId(targetTaskId),
    type = RelationType.valueOf(type),
    label = label,
    explanation = explanation,
    createdBy = RelationCreatedBy.valueOf(createdBy),
    confirmedAt = confirmedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun TaskRelationship.toEntity(): TaskRelationshipEntity = TaskRelationshipEntity(
    id = id.value,
    sourceTaskId = sourceTaskId.value,
    targetTaskId = targetTaskId.value,
    type = type.name,
    label = label,
    explanation = explanation,
    createdBy = createdBy.name,
    confirmedAtEpochMillis = confirmedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

/** Room cannot persist [ZoneId]; fall back to UTC only for absent or unreadable values. */
private fun zoneOrUtc(zoneId: String?): ZoneId =
    zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC