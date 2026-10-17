package com.chrona.app.usecase

import com.chrona.app.data.repository.PreferencesRepository
import com.chrona.app.data.repository.ReminderHistoryRepository
import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.domain.recovery.ScheduleRevisionRepository
import com.chrona.app.domain.reminder.ReminderAction
import com.chrona.app.domain.reminder.ReminderCandidateSelector
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderDecision
import com.chrona.app.domain.reminder.ReminderHistoryEntry
import com.chrona.app.domain.reminder.ReminderOutcome
import com.chrona.app.domain.reminder.ReminderPolicy
import com.chrona.app.domain.reminder.ReminderPolicyInput
import com.chrona.app.domain.reminder.ReminderPresentation
import com.chrona.app.domain.reminder.ReminderPresenter
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** The read-only result of one policy evaluation. */
data class ReminderEvaluation(
    val decisions: List<ReminderDecision>,
    val tasksById: Map<TaskId, Task>,
    val localDate: LocalDate,
) {
    /** Cards that want to reach a surface: everything not suppressed. */
    val actionable: List<ReminderDecision> get() = decisions.filter { it.action != ReminderAction.SUPPRESS }

    fun tasksFor(decision: ReminderDecision): List<Task> =
        decision.tasks.mapNotNull { tasksById[it] }
}

/**
 * Gathers the policy input from repositories and returns the policy's decisions (NOTIFY-001).
 *
 * Read-only: evaluation writes nothing, so it is safe to call from the UI on resume as well as
 * from the background reconciliation worker. Per-candidate and daily-card facts are injected into
 * the pure policy, so this is the only place that touches storage or the clock.
 */
class EvaluateRemindersUseCase(
    private val taskRepository: TaskRepository,
    private val scheduleRevisionRepository: ScheduleRevisionRepository,
    private val reminderHistoryRepository: ReminderHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val timeSource: TimeSource,
    private val policy: ReminderPolicy,
    private val lead: Duration = Duration.ofMinutes(DEFAULT_LEAD_MINUTES),
) {
    suspend operator fun invoke(
        notificationAvailable: Boolean,
        overlayAvailable: Boolean,
    ): Outcome<ReminderEvaluation> {
        val now = timeSource.now()
        val zone = timeSource.zone()
        val localDate = now.atZone(zone).toLocalDate()

        // Settings being unreadable must never stop reminders: fall back to the documented
        // defaults (QUIET style, cap two) rather than failing the whole evaluation.
        val preferences = when (val result = preferencesRepository.load()) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> UserPreferences()
        }
        val tasks = when (val result = taskRepository.list(TaskQuery.ACTIVE)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }
        val placements = when (val result = scheduleRevisionRepository.current()) {
            is Outcome.Success -> result.value?.placements.orEmpty()
            is Outcome.Failed -> emptyList()
        }
        val tasksById = tasks.associateBy { it.id }

        val candidates = ReminderCandidateSelector.select(tasks, placements, now, zone, lead)
        if (candidates.isEmpty()) {
            return Outcome.Success(ReminderEvaluation(emptyList(), tasksById, localDate))
        }

        val candidateIds = candidates.map { it.taskId }.toSet()
        val history = when (val result = reminderHistoryRepository.historyFor(candidateIds)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> emptyList()
        }.groupBy { it.taskId }.mapValues { (_, rows) ->
            rows.map { ReminderHistoryEntry(outcome = it.outcome, at = it.surfacedAt ?: it.createdAt) }
        }
        val surfacedToday = when (val result = reminderHistoryRepository.nonUrgentSurfacedOn(localDate)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> 0
        }

        val input = ReminderPolicyInput(
            now = now,
            zoneId = zone,
            style = preferences.reminderStyle,
            candidates = candidates,
            nonUrgentDailyCap = preferences.nonUrgentDailyCap,
            quietHours = preferences.quietHours,
            nonUrgentSurfacedToday = surfacedToday,
            history = history,
            notificationAvailable = notificationAvailable,
            overlayAvailable = overlayAvailable,
        )
        return Outcome.Success(ReminderEvaluation(policy.decide(input), tasksById, localDate))
    }

    private companion object {
        /** A placement starting within ten minutes counts as due. */
        const val DEFAULT_LEAD_MINUTES = 10L
    }
}

/**
 * Writes reminder decisions and outcomes to history.
 *
 * This is the only writer for `reminder_presentation_history`, and it never touches task state:
 * a dismissal recorded here cannot complete, delete or postpone a task (CARD-003).
 */
class RecordReminderHistoryUseCase(
    private val history: ReminderHistoryRepository,
    private val idGenerator: IdGenerator,
    private val timeSource: TimeSource,
) {
    /** Persists a SUPPRESSED row for every suppressed decision, for a complete audit trail. */
    suspend fun suppressed(evaluation: ReminderEvaluation): Outcome<Unit> {
        val now = timeSource.now()
        val rows = evaluation.decisions
            .filter { it.action == ReminderAction.SUPPRESS }
            .flatMap { decision ->
                decision.tasks.mapNotNull { taskId ->
                    val priority = evaluation.tasksById[taskId]?.priority
                    if (priority == null) {
                        null
                    } else {
                        row(
                            taskId = taskId,
                            priority = priority,
                            reasonCode = decision.reason.code,
                            surface = ReminderSurfaceType.NONE,
                            outcome = ReminderOutcome.SUPPRESSED,
                            surfacedAt = null,
                            batchGroup = null,
                            now = now,
                            localDate = evaluation.localDate,
                        )
                    }
                }
            }
        return history.recordAll(rows)
    }

    /** Persists a SHOWN row for each task of a card that reached [surface]. */
    suspend fun surfaced(card: ReminderCard, surface: ReminderSurfaceType): Outcome<Unit> {
        val now = timeSource.now()
        val localDate = now.atZone(timeSource.zone()).toLocalDate()
        val rows = card.tasks.map { task ->
            row(
                taskId = task.id,
                priority = task.priority,
                reasonCode = card.decision.reason.code,
                surface = surface,
                outcome = ReminderOutcome.SHOWN,
                surfacedAt = now,
                // Every task of one card shares the card id, so the cap counts cards, not tasks.
                batchGroup = card.cardId,
                now = now,
                localDate = localDate,
            )
        }
        return history.recordAll(rows)
    }

    private fun row(
        taskId: TaskId,
        priority: Priority,
        reasonCode: String,
        surface: ReminderSurfaceType,
        outcome: ReminderOutcome,
        surfacedAt: Instant?,
        batchGroup: String?,
        now: Instant,
        localDate: LocalDate,
    ) = ReminderPresentation(
        id = idGenerator.newId(),
        taskId = taskId,
        scheduledFor = now,
        surfacedAt = surfacedAt,
        surfaceType = surface,
        outcome = outcome,
        reasonCode = reasonCode,
        batchGroup = batchGroup,
        localDate = localDate,
        priority = priority,
        createdAt = now,
    )
}

/**
 * Applies a Quest Card action with its exact semantics (CH-UI-001, CARD-003).
 *
 * START, DONE and LATER go through the canonical lifecycle use cases, so the same state machine
 * and audit rules apply whether the action came from the overlay, a notification or the in-app
 * card. DISMISS writes presentation history only and returns `null`: it is never completion,
 * deletion or a silent postpone.
 */
class ApplyReminderActionUseCase(
    private val lifecycle: TaskLifecycleUseCases,
    private val history: ReminderHistoryRepository,
    private val idGenerator: IdGenerator,
    private val timeSource: TimeSource,
) {
    /**
     * @return the updated task for START/DONE/LATER, or `null` for DISMISS.
     */
    suspend operator fun invoke(
        taskId: TaskId,
        action: TaskAction,
        surface: ReminderSurfaceType,
        cardId: String? = null,
        until: ZonedDateTime? = null,
    ): Outcome<Task?> {
        return when (action) {
            TaskAction.DISMISS -> when (val result = dismiss(taskId, surface, cardId)) {
                is Outcome.Success -> Outcome.Success(null)
                is Outcome.Failed -> result
            }

            TaskAction.START -> apply(lifecycle.start(taskId), ReminderOutcome.STARTED, surface, cardId)
            TaskAction.DONE -> apply(lifecycle.complete(taskId), ReminderOutcome.DONE, surface, cardId)
            TaskAction.LATER -> apply(lifecycle.postpone(taskId, until), ReminderOutcome.LATER, surface, cardId)
        }
    }

    /** Records a dismissal. Kept separate so a dismissal path can never route through a task write. */
    suspend fun dismiss(taskId: TaskId, surface: ReminderSurfaceType, cardId: String? = null): Outcome<Unit> {
        val now = timeSource.now()
        // The task may already be gone; a dismissal of a deleted card is simply a no-op.
        return history.record(
            ReminderPresentation(
                id = idGenerator.newId(),
                taskId = taskId,
                scheduledFor = now,
                surfacedAt = null,
                surfaceType = surface,
                outcome = ReminderOutcome.DISMISSED,
                reasonCode = ACTION_REASON_PREFIX + ReminderOutcome.DISMISSED.name,
                batchGroup = cardId,
                localDate = now.atZone(timeSource.zone()).toLocalDate(),
                // Priority is unknown here and irrelevant to dismissal; LOW keeps it out of the cap.
                priority = Priority.LOW,
                createdAt = now,
            ),
        )
    }

    private suspend fun apply(
        result: Outcome<Task>,
        outcome: ReminderOutcome,
        surface: ReminderSurfaceType,
        cardId: String?,
    ): Outcome<Task?> = when (result) {
        is Outcome.Failed -> result
        is Outcome.Success -> {
            val task = result.value
            val now = timeSource.now()
            history.record(
                ReminderPresentation(
                    id = idGenerator.newId(),
                    taskId = task.id,
                    scheduledFor = now,
                    surfacedAt = null,
                    surfaceType = surface,
                    outcome = outcome,
                    reasonCode = ACTION_REASON_PREFIX + outcome.name,
                    batchGroup = cardId,
                    localDate = now.atZone(timeSource.zone()).toLocalDate(),
                    priority = task.priority,
                    createdAt = now,
                ),
            )
            Outcome.Success(task)
        }
    }

    private companion object {
        const val ACTION_REASON_PREFIX = "REMINDER_"
    }
}

/**
 * Stable Quest Card identity derived from its task set.
 *
 * Shared by the delivery path and the in-app surface so the same card is the same card
 * everywhere. That matters for the non-urgent cap, which counts distinct card ids: if the two
 * surfaces invented different ids, showing a card in-app after a notification would inflate the
 * daily count.
 */
fun reminderCardId(decision: ReminderDecision): String =
    "reminder:" + decision.tasks.joinToString(",") { it.value }

/** What actually happened when a reconciler tried to deliver one card. */
data class ReminderDelivery(
    val card: ReminderCard,
    val surface: ReminderSurfaceType,
)

/**
 * Evaluates reminders and delivers actionable cards to the best available surface (MASTER_ARCHITECTURE §C).
 *
 * The overlay is preferred only when the decision asked for it and the overlay is available;
 * otherwise the dependable notification is used; if neither platform surface is available the
 * card is left for the in-app surface, which is always available while the app is open. A
 * presenter returning null (denied permission, force-stopped app, OEM restriction) is a fallback,
 * never an error that blocks the core loop (ADR-009).
 */
class DeliverRemindersUseCase(
    private val evaluate: EvaluateRemindersUseCase,
    private val record: RecordReminderHistoryUseCase,
    private val overlayPresenter: ReminderPresenter,
    private val notificationPresenter: ReminderPresenter,
) {
    suspend operator fun invoke(): Outcome<List<ReminderDelivery>> {
        val overlayAvailable = overlayPresenter.isAvailable()
        val notificationAvailable = notificationPresenter.isAvailable()
        val evaluation = when (val result = evaluate(notificationAvailable, overlayAvailable)) {
            is Outcome.Success -> result.value
            is Outcome.Failed -> return result
        }

        record.suppressed(evaluation)
        val deliveries = ArrayList<ReminderDelivery>()
        for (decision in evaluation.actionable) {
            val card = ReminderCard(
                cardId = reminderCardId(decision),
                decision = decision,
                tasks = evaluation.tasksFor(decision),
            )
            val surface = present(card, decision, overlayAvailable, notificationAvailable) ?: continue
            record.surfaced(card, surface)
            deliveries.add(ReminderDelivery(card, surface))
        }
        return Outcome.Success(deliveries)
    }

    private suspend fun present(
        card: ReminderCard,
        decision: ReminderDecision,
        overlayAvailable: Boolean,
        notificationAvailable: Boolean,
    ): ReminderSurfaceType? = when {
        decision.action == ReminderAction.OVERLAY_BEST_EFFORT && overlayAvailable ->
            overlayPresenter.present(card) ?: fallbackToNotification(card, notificationAvailable)

        notificationAvailable -> notificationPresenter.present(card)
        // No platform surface: the in-app card will show it when the app is open. Nothing is
        // recorded yet, because nothing has reached the user.
        else -> null
    }

    private suspend fun fallbackToNotification(
        card: ReminderCard,
        notificationAvailable: Boolean,
    ): ReminderSurfaceType? = if (notificationAvailable) notificationPresenter.present(card) else null

    companion object {
        const val REMINDER_WORK_NAME = "chrona-reminder-reconciliation"
    }
}
