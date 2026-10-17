package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.recovery.DeterministicRecoveryPlanner
import com.chrona.app.domain.recovery.RecoveryRequest
import com.chrona.app.domain.recovery.RecoveryTrigger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Latency harness for the deterministic engine on representative day sizes.
 *
 * It asserts the invariant (every eligible task is placed or reported) and prints timings.
 * It deliberately does not assert a wall-clock threshold: unit-test timing is not a reliable
 * benchmark, and a fragile bound would fail on a loaded machine without catching a real
 * regression. Read the printed numbers when the policy changes.
 */
class ScheduleEngineBenchmarkTest {
    private val engine = DeterministicScheduleEngine()
    private val zone = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone)
    private val createdAt: Instant = base.toInstant()

    private fun workload(size: Int): ScheduleInput {
        val tasks = (0 until size).map { i ->
            val priority = Priority.entries[i % Priority.entries.size]
            val duration = Duration.ofMinutes((15 + (i * 10) % 90).toLong())
            val deadline = if (i % 4 == 0) base.plusHours(12) else null
            Task(
                id = TaskId("t%03d".format(i)),
                title = "task $i",
                priority = priority,
                estimatedDuration = duration,
                deadline = deadline,
                splittable = i % 5 == 0 && duration > Duration.ofMinutes(30),
                minimumSession = if (i % 5 == 0) Duration.ofMinutes(15) else null,
                status = com.chrona.app.domain.model.TaskStatus.PLANNED,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }
        val relationships = (1 until size).filter { it % 7 == 0 }.map { i ->
            TaskRelationship(
                id = RelationId("r$i"),
                sourceTaskId = TaskId("t%03d".format(i)),
                targetTaskId = TaskId("t%03d".format(i - 1)),
                type = RelationType.DEPENDS_ON,
                confirmedAt = createdAt,
                createdAt = createdAt,
            )
        }
        return ScheduleInput(
            tasks = tasks,
            dayStart = base,
            dayEnd = base.plusHours(14),
            relationships = relationships,
            policyVersion = "p04-v2",
        )
    }

    @Test
    fun `proposes a full day within a practical time and drops nothing`() {
        for (size in listOf(10, 50, 100, 200)) {
            val input = workload(size)
            val started = System.nanoTime()
            val proposal = engine.propose(input)
            val millis = (System.nanoTime() - started) / 1_000_000

            val placed = proposal.placements.map { it.taskId }.toSet()
            val unplaced = proposal.unplaced.map { it.taskId }.toSet()
            assertEquals(size, (placed + unplaced).size)

            println("engine benchmark: $size tasks -> ${millis}ms (${proposal.placements.size} blocks)")
        }
    }

    @Test
    fun `recovery proposal stays as cheap as direct planning`() {
        val planner = DeterministicRecoveryPlanner(engine)
        for (size in listOf(50, 100, 200)) {
            val input = workload(size)
            val started = System.nanoTime()
            val proposal = runBlocking {
                planner(RecoveryRequest(RecoveryTrigger.LATE_START, input, currentRevisionId = null))
            }
            val millis = (System.nanoTime() - started) / 1_000_000

            val placed = proposal.placements.map { it.taskId }.toSet()
            val unplaced = proposal.unplaced.map { it.taskId }.toSet()
            assertEquals(size, (placed + unplaced).size)

            println("recovery benchmark: $size tasks -> ${millis}ms")
        }
    }
}
