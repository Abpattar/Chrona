package com.chrona.app.usecase

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.parse.TaskIntentDraft

/**
 * Persists a draft the user reviewed and confirmed.
 *
 * The mapping is deliberately thin: [NewTask] is what every entry point produces, and
 * [CreateTaskUseCase] is what validates it. Voice therefore cannot introduce a second, laxer
 * path into storage, and the record it creates is the same kind of record a typed task
 * produces — only `origin` and `assumptions` differ (TASK-001, TASK-002).
 *
 * Assumptions are persisted with the task so the record can explain itself later. The
 * transcript and the audio are not stored at all.
 */
class CreateTaskFromIntentUseCase(
    private val createTask: CreateTaskUseCase,
) {
    suspend operator fun invoke(draft: TaskIntentDraft): Outcome<Task> =
        createTask(
            NewTask(
                title = draft.title,
                details = draft.detail,
                priority = draft.priority ?: Priority.IMPORTANT,
                estimatedDuration = draft.estimatedDuration,
                deadline = draft.deadline,
                requestedDate = draft.requestedDate,
                preferredWindow = draft.preferredWindow,
                flexibility = draft.flexibility ?: Flexibility.FLEXIBLE,
                fixedStart = draft.fixedRange?.start,
                fixedEnd = draft.fixedRange?.end,
                splittable = draft.splittable ?: false,
                minimumSession = draft.minimumSession,
                origin = TaskOrigin.VOICE,
                assumptions = draft.assumptions.map { it.text },
            ),
        )
}