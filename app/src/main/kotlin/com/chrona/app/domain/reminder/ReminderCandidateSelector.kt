package com.chrona.app.domain.reminder

import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.scheduling.SchedulePlacement
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Decides which tasks have become due for a reminder (pure, deterministic).
 *
 * This is deliberately separate from [ReminderPolicy]: selection answers "is it time to consider
 * this task?", while the policy answers "should we surface it, and how?". Keeping them apart
 * makes both trivially testable and stops clock reads leaking into the policy.
 *
 * A task is due when it is still open and any of these hold:
 * - it is already `IN_PROGRESS`;
 * - its requested date is today;
 * - its deadline has passed;
 * - it has a schedule placement starting within [lead] of [now] (about to start or already due).
 *
 * A task explicitly postponed to a future instant is not due yet.
 */
object ReminderCandidateSelector {

    fun select(
        tasks: List<Task>,
        placements: List<SchedulePlacement>,
        now: Instant,
        zoneId: ZoneId,
        lead: Duration = Duration.ofMinutes(10),
    ): List<ReminderCandidate> {
        val localDate = now.atZone(zoneId).toLocalDate()
        val dayStart = localDate.atStartOfDay(zoneId).toInstant()
        val leadCutoff = now.plus(lead)
        val placementStartByTask: Map<TaskId, Instant> = placements.groupBy { it.taskId }
            .mapValues { (_, blocks) -> blocks.minOf { it.start.toInstant() } }

        return tasks.mapNotNull { task ->
            if (!task.status.isOpen) return@mapNotNull null
            if (task.postponedUntil != null && task.postponedUntil.toInstant().isAfter(now)) {
                return@mapNotNull null
            }

            val dueInstants = buildList {
                if (task.status == com.chrona.app.domain.model.TaskStatus.IN_PROGRESS) add(now)
                if (task.requestedDate == localDate) add(dayStart)
                task.deadline?.toInstant()?.let { if (!it.isAfter(now)) add(it) }
                placementStartByTask[task.id]?.let { start ->
                    if (!start.isAfter(leadCutoff)) add(start)
                }
            }
            if (dueInstants.isEmpty()) return@mapNotNull null

            ReminderCandidate(
                taskId = task.id,
                eligibleAt = dueInstants.min(),
                priority = task.priority,
                // Grouping currently follows the single project/group label the task already
                // carries; no separate relation graph is read here.
                relationGroup = task.projectLabel,
            )
        }.sortedWith(compareBy({ it.eligibleAt }, { it.taskId.value }))
    }
}
