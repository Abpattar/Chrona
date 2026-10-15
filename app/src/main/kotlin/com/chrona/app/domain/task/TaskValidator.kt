package com.chrona.app.domain.task

import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Task
import java.time.Duration

/** Maximum accepted task title length after trimming. */
const val MAX_TITLE_LENGTH: Int = 200

/** Upper bound on a single estimated session, to reject obvious parser/data errors. */
val MAX_ESTIMATED_DURATION: Duration = Duration.ofHours(24)

/**
 * Field-level validation for a [Task] candidate (DATA_MODEL_AND_INTERFACES.md:
 * "Validate fields at domain boundaries, not solely in text fields").
 *
 * Returns *all* problems so a correction UI can show them together instead of one per
 * submit. Reasons are plain sentences; they are safe to show a user.
 */
object TaskValidator {

    fun validate(task: Task): List<String> = buildList {
        validateTitle(task.title)?.let { add(it) }
        validateDuration(task.estimatedDuration)?.let { add(it) }
        validateMinimumSession(task)?.let { add(it) }
        validateFlexibility(task)?.let { add(it) }
        validateDeadlines(task)?.let { add(it) }
        validateProjectLabel(task.projectLabel)?.let { add(it) }
        validateAssumptions(task.assumptions)?.let { add(it) }
    }

    fun validateTitle(rawTitle: String): String? {
        val title = rawTitle.trim()
        return when {
            title.isEmpty() -> "A task needs a title."
            title.length > MAX_TITLE_LENGTH ->
                "A task title can be at most $MAX_TITLE_LENGTH characters."
            else -> null
        }
    }

    fun validateDuration(duration: Duration?): String? = when {
        duration == null -> null // allowed: an inbox item may still need clarification
        duration.isNegative || duration.isZero -> "Estimated duration must be greater than zero."
        duration > MAX_ESTIMATED_DURATION -> "Estimated duration cannot be longer than 24 hours."
        else -> null
    }

    private fun validateMinimumSession(task: Task): String? {
        val minimum = task.minimumSession ?: return null
        return when {
            !task.splittable -> "A minimum session only applies to a task that can be split."
            minimum.isNegative || minimum.isZero -> "Minimum session must be greater than zero."
            minimum > MAX_ESTIMATED_DURATION -> "Minimum session cannot be longer than 24 hours."
            task.estimatedDuration != null && minimum > task.estimatedDuration ->
                "Minimum session cannot be longer than the estimated duration."
            else -> null
        }
    }

    private fun validateFlexibility(task: Task): String? {
        if (task.flexibility != Flexibility.FIXED) {
            // A flexible task must not silently carry fixed times; that would contradict its
            // own flexibility and confuse the scheduler about which constraint wins.
            return if (task.fixedStart != null || task.fixedEnd != null) {
                "A flexible task cannot have fixed start or end times."
            } else {
                null
            }
        }
        val start = task.fixedStart
        val end = task.fixedEnd
        return when {
            start == null || end == null -> "A fixed task needs both a start and an end time."
            !end.isAfter(start) -> "A fixed task must end after it starts."
            else -> null
        }
    }

    private fun validateDeadlines(task: Task): String? {
        val deadline = task.deadline ?: return null
        val created = task.createdAt
        if (deadline.toInstant().isBefore(created)) {
            return "The deadline is already in the past."
        }
        val earliest = task.earliestStart ?: return null
        if (earliest.isAfter(deadline)) {
            return "The earliest start is after the deadline, so the task cannot be scheduled."
        }
        return null
    }

    private fun validateProjectLabel(label: String?): String? {
        if (label == null) return null
        val trimmed = label.trim()
        return when {
            trimmed.length > MAX_PROJECT_LABEL_LENGTH -> "The project label is too long."
            else -> null
        }
    }

    private fun validateAssumptions(assumptions: List<String>): String? {
        if (assumptions.size > MAX_ASSUMPTIONS) {
            return "A task cannot carry more than $MAX_ASSUMPTIONS interpretation assumptions."
        }
        return null
    }

    private const val MAX_PROJECT_LABEL_LENGTH = 60
    private const val MAX_ASSUMPTIONS = 12
}

/** Field-level validation for a manually entered fixed commitment. */
object CalendarEventValidator {

    fun validate(event: CalendarEvent): List<String> = buildList {
        val title = event.title.trim()
        if (title.isEmpty()) add("A commitment needs a title.")
        if (title.length > MAX_TITLE_LENGTH) {
            add("A commitment title can be at most $MAX_TITLE_LENGTH characters.")
        }
        if (!event.endAt.isAfter(event.startAt)) add("A commitment must end after it starts.")
    }
}