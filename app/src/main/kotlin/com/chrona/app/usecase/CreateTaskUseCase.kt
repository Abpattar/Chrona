package com.chrona.app.usecase

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.task.TaskValidator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Stable ID generation.
 *
 * Injected rather than calling `UUID.randomUUID()` inline so tests can produce identical
 * identities and assert on them (CODING_CONVENTIONS.md: inject ID generators).
 */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * Reference time, injected everywhere.
 *
 * Domain code never reads the global clock, so "what time is it" is an explicit input to
 * every test (MASTER_ARCHITECTURE.md §3.3).
 */
fun interface TimeSource {
    fun now(): Instant

    fun zone(): java.time.ZoneId = java.time.ZoneId.systemDefault()
}

/** What a caller wants to create. All fields optional except a title. */
data class NewTask(
    val title: String,
    val details: String? = null,
    val priority: Priority = Priority.IMPORTANT,
    val estimatedDuration: Duration? = null,
    val deadline: ZonedDateTime? = null,
    val requestedDate: LocalDate? = null,
    val earliestStart: ZonedDateTime? = null,
    val preferredWindow: TimeWindow? = null,
    val flexibility: Flexibility = Flexibility.FLEXIBLE,
    val fixedStart: ZonedDateTime? = null,
    val fixedEnd: ZonedDateTime? = null,
    val splittable: Boolean = false,
    val minimumSession: Duration? = null,
    val projectLabel: String? = null,
    val origin: TaskOrigin = TaskOrigin.TYPED,
    /** Interpretation assumptions to show the user. Never raw model traces. */
    val assumptions: List<String> = emptyList(),
)

/**
 * Creates one task from typed (or, later, voice) input.
 *
 * Validation happens here, at the domain boundary, rather than only in the text fields, so
 * the parser, a future widget and an instrumentation test all get identical rules
 * (TASK-002, `PHASE_02_LOCAL_DATA_VERTICAL_SLICE.md` "Required behavior").
 */
class CreateTaskUseCase(
    private val taskRepository: com.chrona.app.data.repository.TaskRepository,
    private val idGenerator: IdGenerator,
    private val timeSource: TimeSource,
) {
    suspend operator fun invoke(request: NewTask): Outcome<Task> {
        val now = timeSource.now()
        val candidate = Task(
            id = TaskId(idGenerator.newId()),
            // Trim here rather than only in the storage mapper, so the domain record the rest
            // of the app reads is identical to the row on disk.
            title = request.title.trim(),
            details = request.details,
            priority = request.priority,
            estimatedDuration = request.estimatedDuration,
            deadline = request.deadline,
            requestedDate = request.requestedDate,
            earliestStart = request.earliestStart,
            preferredWindow = request.preferredWindow,
            flexibility = request.flexibility,
            fixedStart = request.fixedStart,
            fixedEnd = request.fixedEnd,
            splittable = request.splittable,
            minimumSession = request.minimumSession,
            status = com.chrona.app.domain.model.TaskStatus.INBOX,
            origin = request.origin,
            projectLabel = request.projectLabel,
            createdAt = now,
            updatedAt = now,
            version = 1L,
            assumptions = request.assumptions,
        )

        val problems = TaskValidator.validate(candidate)
        if (problems.isNotEmpty()) {
            return Outcome.Failed(Failure.Validation(problems))
        }

        return when (val stored = taskRepository.insert(candidate)) {
            is Outcome.Success -> Outcome.Success(candidate)
            is Outcome.Failed -> stored
        }
    }
}