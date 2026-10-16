package com.chrona.app.domain.parse

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.task.TaskValidator
import java.time.Instant

/**
 * The single bridge from a parser draft to the canonical task rules.
 *
 * `TaskValidator` owns every field rule in Chrona. Rather than restating them for voice input,
 * a draft is projected onto a [Task] candidate and handed to that same validator, so a spoken
 * task and a typed task can never pass different checks (TASK-001).
 *
 * The candidate is never persisted. It exists only to be validated.
 */
object TaskIntentDraftValidator {

    fun validate(draft: TaskIntentDraft, createdAt: Instant): List<String> =
        TaskValidator.validate(draft.toTaskCandidate(createdAt))
}

/** Stable placeholder id: a draft has no identity until the user confirms it. */
private const val DRAFT_ID = "draft"

/** Projects a draft onto a [Task] candidate so the canonical rules can validate it. */
internal fun TaskIntentDraft.toTaskCandidate(createdAt: Instant): Task = Task(
    id = TaskId(DRAFT_ID),
    title = title,
    details = detail,
    priority = priority ?: Priority.IMPORTANT,
    estimatedDuration = estimatedDuration,
    deadline = deadline,
    requestedDate = requestedDate,
    preferredWindow = preferredWindow,
    flexibility = flexibility ?: Flexibility.FLEXIBLE,
    fixedStart = fixedRange?.start,
    fixedEnd = fixedRange?.end,
    splittable = splittable ?: false,
    minimumSession = minimumSession,
    createdAt = createdAt,
    updatedAt = createdAt,
    // The user-visible assumption sentences are persisted with the task, so the record can
    // explain itself later. The transcript itself is never stored.
    assumptions = assumptions.map { it.text },
)