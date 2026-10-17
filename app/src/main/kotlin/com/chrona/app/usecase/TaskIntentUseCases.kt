package com.chrona.app.usecase

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.parse.TaskIntentDraft

/**
 * Persists a draft the user confirmed.
 *
 * The mapping is deliberately thin: [NewTask] is what every entry point produces, and
 * [CreateTaskUseCase] is what validates it. Voice therefore cannot introduce a second, laxer
 * path into storage, and the record it creates is the same kind of record a typed task
 * produces — only `origin` and `assumptions` differ (TASK-001, TASK-002).
 *
 * One utterance can produce several drafts, and [createAll] gives each its own record.
 *
 * Assumptions are persisted with the task so the record can explain itself later. The
 * transcript and the audio are not stored at all.
 */
class CreateTaskFromIntentUseCase(
    private val createTask: CreateTaskUseCase,
) {
    suspend operator fun invoke(draft: TaskIntentDraft): Outcome<Task> =
        createTask(draft.toNewTask())

    /**
     * Creates one independent task per draft understood from a single utterance.
     *
     * Each draft is a separate record, never a detail of the previous one, and each is attempted
     * on its own: a draft the domain rejects is reported as a failure rather than taking the
     * others down with it. Results come back in draft order, so a caller can report exactly what
     * was stored and what was not.
     */
    suspend fun createAll(drafts: List<TaskIntentDraft>): List<Outcome<Task>> =
        drafts.map { invoke(it) }

    private fun TaskIntentDraft.toNewTask() = NewTask(
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
        origin = TaskOrigin.VOICE,
        assumptions = assumptions.map { it.text },
    )
}