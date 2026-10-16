package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEngineTest {
    private val engine = DeterministicScheduleEngine()
    private val zone = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val end = base.plusHours(8)

    @Test
    fun `places simple task in first interval`() {
        val now = base.toInstant()
        val t = Task(id = TaskId("t1"), title = "Read", priority = Priority.IMPORTANT, status = TaskStatus.INBOX, estimatedDuration = Duration.ofMinutes(30), createdAt = now, updatedAt = now)
        val input = ScheduleInput(listOf(t), emptyList(), base, end)
        val p = engine.propose(input)
        assertEquals(1, p.placements.size)
    }

    @Test
    fun `respects fixed commitment`() {
        val now = base.toInstant()
        val fix = FixedCommitment("c1", "Class", TimeRange(base.plusHours(1), base.plusHours(2)))
        val t1 = Task(id = TaskId("a"), title = "A", priority = Priority.CRITICAL, status = TaskStatus.INBOX, estimatedDuration = Duration.ofMinutes(60), createdAt = now, updatedAt = now)
        val t2 = Task(id = TaskId("b"), title = "B", priority = Priority.IMPORTANT, status = TaskStatus.INBOX, estimatedDuration = Duration.ofMinutes(60), createdAt = now, updatedAt = now)
        val input = ScheduleInput(listOf(t1,t2), listOf(fix), base, end, buffer = Duration.ofMinutes(0))
        val p = engine.propose(input)
        assertTrue(p.placements.size >= 1)
        assertTrue(p.placements.all { !it.start.isBefore(base) && it.end.isBefore(end) })
    }
}
