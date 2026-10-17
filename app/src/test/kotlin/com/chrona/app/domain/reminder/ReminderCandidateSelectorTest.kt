package com.chrona.app.domain.reminder

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.SchedulePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderCandidateSelectorTest {

    private val zone = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-10-15T10:00:00Z")
    private val today: LocalDate = LocalDate.parse("2026-10-15")

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.INBOX,
        requestedDate: LocalDate? = null,
        deadline: ZonedDateTime? = null,
        postponedUntil: ZonedDateTime? = null,
        project: String? = null,
    ) = Task(
        id = TaskId(id),
        title = id,
        priority = Priority.IMPORTANT,
        status = status,
        requestedDate = requestedDate,
        deadline = deadline,
        postponedUntil = postponedUntil,
        projectLabel = project,
        createdAt = now,
        updatedAt = now,
    )

    private fun placement(id: String, startAt: Instant): SchedulePlacement = SchedulePlacement(
        taskId = TaskId(id),
        start = startAt.atZone(zone),
        end = startAt.plusSeconds(1800).atZone(zone),
    )

    @Test
    fun `a task requested today is a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", requestedDate = today)), emptyList(), now, zone,
        )
        assertEquals(listOf(TaskId("a")), result.map { it.taskId })
    }

    @Test
    fun `a task requested in the future is not a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", requestedDate = today.plusDays(1))), emptyList(), now, zone,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `an overdue deadline is a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", deadline = now.minusSeconds(3600).atZone(zone))), emptyList(), now, zone,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `a placement about to start is a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a")), listOf(placement("a", now.plusSeconds(300))), now, zone,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `a placement far in the future is not a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a")), listOf(placement("a", now.plusSeconds(3600))), now, zone,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `an in-progress task is a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", status = TaskStatus.IN_PROGRESS)), emptyList(), now, zone,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `a task postponed to the future is not a candidate`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", postponedUntil = now.plusSeconds(3600).atZone(zone))), emptyList(), now, zone,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `closed tasks are never candidates`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", status = TaskStatus.COMPLETED, requestedDate = today)), emptyList(), now, zone,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the project label becomes the relation group`() {
        val result = ReminderCandidateSelector.select(
            listOf(task("a", requestedDate = today, project = "Thesis")), emptyList(), now, zone,
        )
        assertEquals("Thesis", result.single().relationGroup)
    }

    @Test
    fun `a task with no due signal is not a candidate`() {
        val result = ReminderCandidateSelector.select(listOf(task("a")), emptyList(), now, zone)
        assertTrue(result.isEmpty())
        assertNull(result.firstOrNull())
    }
}
