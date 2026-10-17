package com.chrona.app.domain.reminder

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.TaskId
import java.time.Instant

/**
 * The deterministic reminder policy (NOTIFY-001, ADR-010, ADR-034).
 *
 * It is a pure function of its input: no clock, storage or Android. The same input always gives
 * the same decisions, which is what makes a seven-day reminder fixture assertable in tests.
 *
 * Evaluation order:
 * 1. per-task suppression — repeated dismissals, recent surfacing (an explicit `LATER`
 *    overrides it), quiet hours and quiet mode;
 * 2. related batching — two or more related tasks become one card;
 * 3. the non-urgent daily cap — counted in *cards*, so a batch costs one, not one per task.
 *
 * `CRITICAL`/`IMPORTANT` are urgent: they are never limited by the cap and are never batched
 * away. `LOW` is non-urgent. A card containing any urgent task is treated as urgent.
 */
class DeterministicReminderPolicy(
    private val config: ReminderPolicyConfig = ReminderPolicyConfig(),
) : ReminderPolicy {

    private data class Card(val tasks: List<ReminderCandidate>, val groupKey: String?)

    override fun decide(input: ReminderPolicyInput): List<ReminderDecision> {
        // Total order so two runs over the same input cannot reorder decisions.
        val ordered = input.candidates.sortedWith(
            compareBy({ urgencyRank(it.priority) }, { it.eligibleAt }, { it.taskId.value }),
        )

        val suppressed = LinkedHashMap<TaskId, ReminderDecision>()
        val surfaceable = ArrayList<ReminderCandidate>()
        for (candidate in ordered) {
            val decision = suppressionFor(candidate, input)
            if (decision != null) suppressed[candidate.taskId] = decision else surfaceable.add(candidate)
        }

        val cards = buildCards(ordered, surfaceable, suppressed.keys, input.style)

        var nonUrgentCards = input.nonUrgentSurfacedToday
        val emitted = ArrayList<Pair<Int, ReminderDecision>>()
        for ((index, card) in cards) {
            val taskIds = card.tasks.map { it.taskId }
            val urgent = card.tasks.any { it.priority != Priority.LOW }
            if (!urgent && nonUrgentCards >= input.nonUrgentDailyCap) {
                emitted.add(
                    index to ReminderDecision(
                        action = ReminderAction.SUPPRESS,
                        tasks = taskIds,
                        reason = ReminderDecisionReason.DAILY_CAP_REACHED,
                        nextEligibleAt = nextLocalDayStart(input),
                    ),
                )
            } else {
                if (!urgent) nonUrgentCards++
                emitted.add(index to surfaceCard(card, input))
            }
        }

        // Suppressed singles keep their own positions; merge them back in input order.
        for ((index, decision) in suppressedOrdered(ordered, suppressed)) {
            emitted.add(index to decision)
        }
        return emitted.sortedBy { it.first }.map { it.second }
    }

    /** One card per related group; tasks with no group (or a loner) become a single card. */
    private fun buildCards(
        ordered: List<ReminderCandidate>,
        surfaceable: List<ReminderCandidate>,
        suppressedIds: Set<TaskId>,
        style: ReminderStyle,
    ): List<Pair<Int, Card>> {
        val groups = batchGroups(surfaceable, style)
        val groupOf = HashMap<TaskId, String>()
        groups.forEach { (key, members) -> members.forEach { groupOf[it.taskId] = key } }

        val cards = ArrayList<Pair<Int, Card>>()
        val emittedGroups = HashSet<String>()
        ordered.forEachIndexed { index, candidate ->
            if (candidate.taskId in suppressedIds) return@forEachIndexed
            val key = groupOf[candidate.taskId]
            if (key != null) {
                if (emittedGroups.add(key)) {
                    cards.add(index to Card(groups.getValue(key).sortedBy { it.taskId.value }, key))
                }
            } else {
                cards.add(index to Card(listOf(candidate), null))
            }
        }
        return cards
    }

    private fun suppressedOrdered(
        ordered: List<ReminderCandidate>,
        suppressed: Map<TaskId, ReminderDecision>,
    ): List<Pair<Int, ReminderDecision>> = ordered.mapIndexedNotNull { index, candidate ->
        suppressed[candidate.taskId]?.let { index to it }
    }

    private fun suppressionFor(
        candidate: ReminderCandidate,
        input: ReminderPolicyInput,
    ): ReminderDecision? {
        val history = input.history[candidate.taskId].orEmpty().sortedBy { it.at }
        val last = history.lastOrNull()

        if (last?.outcome == ReminderOutcome.DISMISSED) {
            val windowStart = input.now.minus(config.dismissalSuppressionWindow)
            val dismissalsInWindow = history.count {
                it.outcome == ReminderOutcome.DISMISSED && !it.at.isBefore(windowStart)
            }
            if (dismissalsInWindow >= config.dismissalsToSuppress) {
                val until = last.at.plus(config.dismissalSuppressionWindow)
                if (input.now.isBefore(until)) {
                    return suppress(candidate, ReminderDecisionReason.RECENTLY_DISMISSED, until)
                }
            }
        }

        // A task explicitly set to `LATER` is not blocked here: its own due time is in the input,
        // so re-surfacing follows the user's request rather than this anti-nag window.
        if (last?.outcome == ReminderOutcome.SHOWN) {
            val until = last.at.plus(config.resurfaceWindow)
            if (input.now.isBefore(until)) {
                return suppress(candidate, ReminderDecisionReason.RECENTLY_SURFACED, until)
            }
        }

        val quietHours = input.quietHours
        if (quietHours != null) {
            val localNow = input.now.atZone(input.zoneId)
            if (quietHours.contains(localNow.toLocalTime())) {
                return suppress(
                    candidate,
                    ReminderDecisionReason.QUIET_HOURS,
                    quietHours.nextEndAfter(localNow).toInstant(),
                )
            }
        }

        if (input.style == ReminderStyle.QUIET && candidate.priority == Priority.LOW) {
            return suppress(candidate, ReminderDecisionReason.QUIET_MODE, null)
        }
        return null
    }

    private fun surfaceCard(card: Card, input: ReminderPolicyInput): ReminderDecision {
        val taskIds = card.tasks.map { it.taskId }
        if (card.tasks.size > 1) {
            return ReminderDecision(
                action = ReminderAction.BATCH,
                tasks = taskIds,
                reason = ReminderDecisionReason.BATCHED_RELATED,
                nextEligibleAt = null,
            )
        }
        // Order follows MASTER_ARCHITECTURE.md §C: overlay when enabled and granted, else the
        // dependable notification, else the in-app card that is always available.
        val action = when {
            input.overlayAvailable -> ReminderAction.OVERLAY_BEST_EFFORT
            input.notificationAvailable -> ReminderAction.NOTIFICATION
            else -> ReminderAction.IN_APP
        }
        return ReminderDecision(
            action = action,
            tasks = taskIds,
            reason = ReminderDecisionReason.ELIGIBLE,
            nextEligibleAt = null,
        )
    }

    private fun batchGroups(
        eligible: List<ReminderCandidate>,
        style: ReminderStyle,
    ): Map<String, List<ReminderCandidate>> {
        // MORE_GUIDANCE deliberately shows each task on its own card.
        if (style == ReminderStyle.MORE_GUIDANCE) return emptyMap()
        return eligible
            .filter { it.relationGroup != null }
            .groupBy { it.relationGroup!! }
            .filterValues { it.size >= 2 }
    }

    private fun suppress(
        candidate: ReminderCandidate,
        reason: ReminderDecisionReason,
        nextEligibleAt: Instant?,
    ) = ReminderDecision(
        action = ReminderAction.SUPPRESS,
        tasks = listOf(candidate.taskId),
        reason = reason,
        nextEligibleAt = nextEligibleAt,
    )

    private fun nextLocalDayStart(input: ReminderPolicyInput): Instant {
        val localDate = input.now.atZone(input.zoneId).toLocalDate()
        return localDate.plusDays(1).atStartOfDay(input.zoneId).toInstant()
    }

    private fun urgencyRank(priority: Priority): Int = when (priority) {
        Priority.CRITICAL -> 0
        Priority.IMPORTANT -> 1
        Priority.LOW -> 2
    }
}
