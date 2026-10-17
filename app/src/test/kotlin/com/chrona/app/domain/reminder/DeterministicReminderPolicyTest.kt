package com.chrona.app.domain.reminder

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.QuietHours
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.TaskId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DeterministicReminderPolicyTest {

    private val policy = DeterministicReminderPolicy()
    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-10-15T10:00:00Z")

    private fun candidate(
        id: String,
        priority: Priority = Priority.IMPORTANT,
        group: String? = null,
        eligibleAt: Instant = now,
    ) = ReminderCandidate(TaskId(id), eligibleAt, priority, group)

    private fun input(
        candidates: List<ReminderCandidate>,
        style: ReminderStyle = ReminderStyle.BALANCED,
        cap: Int = 2,
        quietHours: QuietHours? = null,
        surfacedToday: Int = 0,
        history: Map<TaskId, List<ReminderHistoryEntry>> = emptyMap(),
        notificationAvailable: Boolean = true,
        overlayAvailable: Boolean = false,
        at: Instant = now,
    ) = ReminderPolicyInput(
        now = at,
        zoneId = zone,
        style = style,
        candidates = candidates,
        nonUrgentDailyCap = cap,
        quietHours = quietHours,
        nonUrgentSurfacedToday = surfacedToday,
        history = history,
        notificationAvailable = notificationAvailable,
        overlayAvailable = overlayAvailable,
    )

    private fun decisionsByTask(input: ReminderPolicyInput) =
        policy.decide(input).associateBy { it.tasks.single() }

    @Test
    fun `urgent task surfaces via notification when available`() {
        val decisions = decisionsByTask(input(listOf(candidate("a"))))
        assertEquals(ReminderAction.NOTIFICATION, decisions.getValue(TaskId("a")).action)
        assertEquals(ReminderDecisionReason.ELIGIBLE, decisions.getValue(TaskId("a")).reason)
    }

    @Test
    fun `overlay is preferred when enabled and granted`() {
        val decisions = decisionsByTask(input(listOf(candidate("a")), overlayAvailable = true))
        assertEquals(ReminderAction.OVERLAY_BEST_EFFORT, decisions.getValue(TaskId("a")).action)
    }

    @Test
    fun `in-app is the fallback when no other surface is available`() {
        val decisions = decisionsByTask(
            input(listOf(candidate("a")), notificationAvailable = false, overlayAvailable = false),
        )
        assertEquals(ReminderAction.IN_APP, decisions.getValue(TaskId("a")).action)
    }

    @Test
    fun `quiet mode suppresses low priority tasks`() {
        val decisions = decisionsByTask(
            input(listOf(candidate("a", Priority.LOW)), style = ReminderStyle.QUIET),
        )
        assertEquals(ReminderAction.SUPPRESS, decisions.getValue(TaskId("a")).action)
        assertEquals(ReminderDecisionReason.QUIET_MODE, decisions.getValue(TaskId("a")).reason)
    }

    @Test
    fun `quiet mode still surfaces important tasks`() {
        val decisions = decisionsByTask(
            input(listOf(candidate("a", Priority.IMPORTANT)), style = ReminderStyle.QUIET),
        )
        assertEquals(ReminderAction.NOTIFICATION, decisions.getValue(TaskId("a")).action)
    }

    @Test
    fun `non-urgent cap is reached and defers to the next local day`() {
        val decisions = decisionsByTask(
            input(listOf(candidate("a", Priority.LOW)), cap = 2, surfacedToday = 2),
        )
        val decision = decisions.getValue(TaskId("a"))
        assertEquals(ReminderAction.SUPPRESS, decision.action)
        assertEquals(ReminderDecisionReason.DAILY_CAP_REACHED, decision.reason)
        assertEquals(Instant.parse("2026-10-16T00:00:00Z"), decision.nextEligibleAt)
    }

    @Test
    fun `urgent tasks bypass the non-urgent cap`() {
        val decisions = decisionsByTask(
            input(listOf(candidate("a", Priority.CRITICAL)), cap = 0, surfacedToday = 5),
        )
        assertEquals(ReminderAction.NOTIFICATION, decisions.getValue(TaskId("a")).action)
    }

    @Test
    fun `cap counts only non-urgent tasks surfaced in the same run`() {
        val decisions = policy.decide(
            input(
                listOf(
                    candidate("a", Priority.LOW, eligibleAt = now.plusSeconds(1)),
                    candidate("b", Priority.LOW, eligibleAt = now.plusSeconds(2)),
                ),
                cap = 1,
            ),
        ).associateBy { it.tasks.single() }
        assertEquals(ReminderAction.NOTIFICATION, decisions.getValue(TaskId("a")).action)
        assertEquals(ReminderAction.SUPPRESS, decisions.getValue(TaskId("b")).action)
        assertEquals(ReminderDecisionReason.DAILY_CAP_REACHED, decisions.getValue(TaskId("b")).reason)
    }

    @Test
    fun `quiet hours inside the window defer to the window end`() {
        val quiet = QuietHours(java.time.LocalTime.of(21, 0), java.time.LocalTime.of(7, 0))
        val night = Instant.parse("2026-10-15T22:30:00Z")
        val decision = decisionsByTask(
            input(listOf(candidate("a", eligibleAt = night)), quietHours = quiet, at = night),
        ).getValue(TaskId("a"))
        assertEquals(ReminderAction.SUPPRESS, decision.action)
        assertEquals(ReminderDecisionReason.QUIET_HOURS, decision.reason)
        assertEquals(Instant.parse("2026-10-16T07:00:00Z"), decision.nextEligibleAt)
    }

    @Test
    fun `quiet hours crossing midnight defer to the next morning`() {
        val quiet = QuietHours(java.time.LocalTime.of(22, 0), java.time.LocalTime.of(6, 0))
        val afterMidnight = Instant.parse("2026-10-15T03:00:00Z")
        val decision = decisionsByTask(
            input(listOf(candidate("a", eligibleAt = afterMidnight)), quietHours = quiet, at = afterMidnight),
        ).getValue(TaskId("a"))
        assertEquals(ReminderDecisionReason.QUIET_HOURS, decision.reason)
        assertEquals(Instant.parse("2026-10-15T06:00:00Z"), decision.nextEligibleAt)
    }

    @Test
    fun `outside quiet hours the task surfaces normally`() {
        val quiet = QuietHours(java.time.LocalTime.of(21, 0), java.time.LocalTime.of(7, 0))
        val decision = decisionsByTask(input(listOf(candidate("a")), quietHours = quiet))
            .getValue(TaskId("a"))
        assertEquals(ReminderAction.NOTIFICATION, decision.action)
    }

    @Test
    fun `repeated dismissals suppress for the window`() {
        val dismissed = listOf(
            ReminderHistoryEntry(ReminderOutcome.DISMISSED, now.minus(Duration.ofHours(1))),
            ReminderHistoryEntry(ReminderOutcome.DISMISSED, now.minus(Duration.ofMinutes(30))),
        )
        val decision = decisionsByTask(input(listOf(candidate("a")), history = mapOf(TaskId("a") to dismissed)))
            .getValue(TaskId("a"))
        assertEquals(ReminderAction.SUPPRESS, decision.action)
        assertEquals(ReminderDecisionReason.RECENTLY_DISMISSED, decision.reason)
        assertEquals(now.minus(Duration.ofMinutes(30)).plus(Duration.ofHours(12)), decision.nextEligibleAt)
    }

    @Test
    fun `a single dismissal does not suppress`() {
        val dismissed = listOf(
            ReminderHistoryEntry(ReminderOutcome.DISMISSED, now.minus(Duration.ofMinutes(30))),
        )
        val decision = decisionsByTask(input(listOf(candidate("a")), history = mapOf(TaskId("a") to dismissed)))
            .getValue(TaskId("a"))
        assertEquals(ReminderAction.NOTIFICATION, decision.action)
    }

    @Test
    fun `a recently surfaced task is not surfaced again`() {
        val shown = listOf(ReminderHistoryEntry(ReminderOutcome.SHOWN, now.minus(Duration.ofHours(1))))
        val decision = decisionsByTask(input(listOf(candidate("a")), history = mapOf(TaskId("a") to shown)))
            .getValue(TaskId("a"))
        assertEquals(ReminderAction.SUPPRESS, decision.action)
        assertEquals(ReminderDecisionReason.RECENTLY_SURFACED, decision.reason)
    }

    @Test
    fun `an explicit later overrides the resurface window`() {
        val history = listOf(
            ReminderHistoryEntry(ReminderOutcome.SHOWN, now.minus(Duration.ofHours(1))),
            ReminderHistoryEntry(ReminderOutcome.LATER, now.minus(Duration.ofMinutes(5))),
        )
        val decision = decisionsByTask(input(listOf(candidate("a")), history = mapOf(TaskId("a") to history)))
            .getValue(TaskId("a"))
        assertEquals(ReminderAction.NOTIFICATION, decision.action)
    }

    @Test
    fun `related tasks in balanced mode are batched into one decision`() {
        val decisions = policy.decide(
            input(
                listOf(
                    candidate("a", group = "project-x", eligibleAt = now.plusSeconds(1)),
                    candidate("b", group = "project-x", eligibleAt = now.plusSeconds(2)),
                ),
            ),
        )
        val batch = decisions.single()
        assertEquals(ReminderAction.BATCH, batch.action)
        assertEquals(ReminderDecisionReason.BATCHED_RELATED, batch.reason)
        assertEquals(listOf(TaskId("a"), TaskId("b")), batch.tasks)
    }

    @Test
    fun `more guidance mode does not batch`() {
        val decisions = policy.decide(
            input(
                listOf(
                    candidate("a", group = "project-x", eligibleAt = now.plusSeconds(1)),
                    candidate("b", group = "project-x", eligibleAt = now.plusSeconds(2)),
                ),
                style = ReminderStyle.MORE_GUIDANCE,
            ),
        )
        assertEquals(2, decisions.size)
        assertTrue(decisions.all { it.action == ReminderAction.NOTIFICATION })
    }

    @Test
    fun `a single related task is surfaced individually, not batched`() {
        val decisions = policy.decide(input(listOf(candidate("a", group = "project-x"))))
        assertEquals(ReminderAction.NOTIFICATION, decisions.single().action)
    }

    @Test
    fun `decisions are deterministic for the same input`() {
        val request = input(
            listOf(
                candidate("b", group = "g", eligibleAt = now.plusSeconds(2)),
                candidate("a", group = "g", eligibleAt = now.plusSeconds(1)),
                candidate("c", Priority.LOW, eligibleAt = now.plusSeconds(3)),
            ),
        )
        assertEquals(policy.decide(request), policy.decide(request))
    }

    @Test
    fun `suppressed tasks carry no action tasks beyond their own id`() {
        val decision = decisionsByTask(
            input(listOf(candidate("a", Priority.LOW)), style = ReminderStyle.QUIET),
        ).getValue(TaskId("a"))
        assertEquals(listOf(TaskId("a")), decision.tasks)
        assertNull(decision.nextEligibleAt)
    }
}
