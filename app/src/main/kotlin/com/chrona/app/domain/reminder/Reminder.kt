package com.chrona.app.domain.reminder

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.QuietHours
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.TaskId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A task that has become due, offered to the reminder policy (SHARED_CONTRACT.md §Reminder).
 *
 * The policy never reads storage: eligibility is decided elsewhere and passed in, so the
 * decision is a pure function of explicit input (ADR-003).
 */
data class ReminderCandidate(
    val taskId: TaskId,
    val eligibleAt: Instant,
    val priority: Priority,
    /** Groups related tasks for batching; null means the task stands alone. */
    val relationGroup: String?,
)

/** What the policy wants done with a group of tasks. */
enum class ReminderAction {
    /** Do not surface; the reason explains why and when it may become eligible again. */
    SUPPRESS,

    /** In-app Quest Card only. Always available, so it is the final fallback. */
    IN_APP,

    /** Ordinary notification. The dependable surface. */
    NOTIFICATION,

    /** Optional compact panel over other apps; best-effort, falls back to notification/in-app. */
    OVERLAY_BEST_EFFORT,

    /** Several related tasks surfaced together as one card. */
    BATCH,
}

/**
 * Stable machine codes explaining a decision.
 *
 * These are recorded in presentation history instead of free text, so reminders can be audited
 * without storing anything sensitive (ERROR_HANDLING_AND_SECURITY.md §Required controls).
 */
enum class ReminderDecisionReason(val code: String) {
    ELIGIBLE("REMINDER_ELIGIBLE"),
    BATCHED_RELATED("REMINDER_BATCHED_RELATED"),
    QUIET_MODE("REMINDER_QUIET_MODE"),
    QUIET_HOURS("REMINDER_QUIET_HOURS"),
    DAILY_CAP_REACHED("REMINDER_DAILY_CAP"),
    RECENTLY_DISMISSED("REMINDER_RECENTLY_DISMISSED"),
    RECENTLY_SURFACED("REMINDER_RECENTLY_SURFACED"),
}

/** A single policy decision covering one or more tasks. */
data class ReminderDecision(
    val action: ReminderAction,
    val tasks: List<TaskId>,
    val reason: ReminderDecisionReason,
    /** When a suppressed task may be reconsidered; null when there is no scheduled retry. */
    val nextEligibleAt: Instant?,
)

/** Which surface actually showed a reminder. `NONE` marks a suppressed (never-shown) row. */
enum class ReminderSurfaceType { IN_APP, NOTIFICATION, OVERLAY, NONE }

/**
 * The outcome of a reminder presentation.
 *
 * `SHOWN`/`SUPPRESSED` are policy/platform facts; `STARTED`/`DONE`/`LATER`/`DISMISSED` are user
 * actions. None of these is a task status: dismissing a card never completes a task (ADR-011).
 */
enum class ReminderOutcome { SHOWN, STARTED, DONE, LATER, DISMISSED, SUPPRESSED, FAILED_FALLBACK }

/** One row of `ReminderPresentationHistory` (DATA_MODEL_AND_INTERFACES.md). */
data class ReminderPresentation(
    val id: String,
    val taskId: TaskId,
    val scheduledFor: Instant,
    val surfacedAt: Instant?,
    val surfaceType: ReminderSurfaceType,
    val outcome: ReminderOutcome,
    val reasonCode: String,
    val batchGroup: String?,
    val localDate: LocalDate,
    /**
     * Priority at the moment of presentation. Stored so the non-urgent daily cap can be counted
     * without reading back a task that may already be deleted.
     */
    val priority: Priority,
    val createdAt: Instant,
)

/** A prior presentation, reduced to what the policy needs to reason about repetition. */
data class ReminderHistoryEntry(
    val outcome: ReminderOutcome,
    val at: Instant,
)

/**
 * Tunable, deterministic policy constants.
 *
 * Kept separate from [UserPreferences] because these are product hypotheses about *how* to be
 * respectful, whereas preferences are what the user chose (ADR-010 / ADR-034).
 */
data class ReminderPolicyConfig(
    /** A card already shown is not shown again within this window unless the user said Later. */
    val resurfaceWindow: Duration = Duration.ofHours(6),
    /** Dismissals older than this do not count towards suppression. */
    val dismissalSuppressionWindow: Duration = Duration.ofHours(12),
    /** How many dismissals within the window before nagging is suppressed. */
    val dismissalsToSuppress: Int = 2,
)

/**
 * Everything the policy is allowed to see (SHARED_CONTRACT.md §Reminder).
 *
 * Platform availability and all history are explicit input, so the same input always yields the
 * same decisions and the behaviour is fully testable without Android.
 */
data class ReminderPolicyInput(
    val now: Instant,
    val zoneId: ZoneId,
    val style: ReminderStyle,
    val candidates: List<ReminderCandidate>,
    /** Editable hypothesis, default two (ADR-010). Applies to non-urgent (`LOW`) tasks only. */
    val nonUrgentDailyCap: Int,
    val quietHours: QuietHours?,
    /** Non-urgent cards already surfaced on the local date of [now]. */
    val nonUrgentSurfacedToday: Int,
    /** Recent presentation history per task, oldest first. */
    val history: Map<TaskId, List<ReminderHistoryEntry>>,
    val notificationAvailable: Boolean,
    /** True only when the user enabled the overlay *and* the permission is granted. */
    val overlayAvailable: Boolean,
)

/**
 * The pure reminder decision function.
 *
 * Implementations must be deterministic, must not read a clock or storage, and must never change
 * task state — surfacing and dismissing reminders are presentation concerns (ADR-011).
 */
interface ReminderPolicy {
    fun decide(input: ReminderPolicyInput): List<ReminderDecision>
}

/**
 * The four Quest Card actions (CH-UI-001).
 *
 * Their semantics are deliberately distinct and must never be conflated:
 * - [START] begins focused work and moves the task to in-progress;
 * - [DONE] completes the task through the canonical lifecycle use case;
 * - [LATER] postpones the task explicitly and requires a replacement time or a transparent
 *   unscheduled outcome;
 * - [DISMISS] closes this card and touches presentation history only. It is never completion,
 *   deletion or a silent postpone (CARD-003).
 */
enum class TaskAction { START, DONE, LATER, DISMISS }

/** A ready-to-surface Quest Card: the decision plus the task data a surface needs to render it. */
data class ReminderCard(
    /** Stable identity so a re-delivered notification replaces rather than duplicates. */
    val cardId: String,
    val decision: ReminderDecision,
    val tasks: List<com.chrona.app.domain.model.Task>,
)

/**
 * A platform surface that can show a Quest Card (CH-PLAT-002/003).
 *
 * The domain and use cases depend only on this boundary; the Android notification and overlay
 * implementations live in `platform/`. Delivery is best-effort: [present] returning null means
 * "could not show here" and the caller falls back, so a denied permission or a force-stopped app
 * never blocks the core loop (ADR-009).
 */
interface ReminderPresenter {
    val surface: ReminderSurfaceType

    /** True only when this surface may be used right now (permission granted, platform allows). */
    suspend fun isAvailable(): Boolean

    /** Shows [card] and returns the surface actually used, or null when it could not be shown. */
    suspend fun present(card: ReminderCard): ReminderSurfaceType?
}

