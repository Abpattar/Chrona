package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
class DeterministicScheduleEngine : ScheduleEngine {
    override fun propose(input: ScheduleInput): ScheduleProposal {
        val day = TimeRange(input.dayStart, input.dayEnd)
        val fixed = input.fixedCommitments.sortedBy { it.range.start }
        val tasks = input.tasks.filterEligible().sortedWith(taskComparator())
        val placements = mutableListOf<SchedulePlacement>()
        val unplaced = mutableListOf<UnplacedTask>()
        val assumptions = mutableListOf<ScheduleAssumption>()
        val intervals = getAvailableIntervals(day, fixed, input.buffer)
        for (t in tasks) {
            if (!buildDependencies(tasks).allDepsSatisfied(t.id, placements)) {
                unplaced.add(UnplacedTask(t.id, UnplacedReason.DEPENDENCY_INCOMPLETE))
                continue
            }
            val dur = t.estimatedDuration ?: Duration.ofMinutes(30)
            if (dur.isZero || dur.isNegative) {
                unplaced.add(UnplacedTask(t.id, UnplacedReason.INVALID_DURATION))
                continue
            }
            val placed = placeInIntervals(t, dur, intervals, placements, fixed)
            if (!placed) {
                unplaced.add(UnplacedTask(t.id, UnplacedReason.NO_AVAILABLE_WINDOW))
            }
        }
        return ScheduleProposal(
            proposalId = UUID.randomUUID().toString(),
            basedOnRevisionId = null,
            placements = placements.sortedBy { it.start },
            unplaced = unplaced,
            conflicts = emptyList(),
            changes = emptyList(),
            assumptions = assumptions,
            explanation = "placements=${placements.size}, unplaced=${unplaced.size}",
            policyVersion = input.policyVersion,
        )
    }
}
