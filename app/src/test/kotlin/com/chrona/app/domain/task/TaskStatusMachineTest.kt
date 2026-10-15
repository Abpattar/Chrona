package com.chrona.app.domain.task

import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Status transitions (TASK-002, CH-DOM-004).
 *
 * The important negative cases are here on purpose: a completed task must not be quietly
 * reactivated, and there is no transition at all that a card dismissal could map onto.
 */
class TaskStatusMachineTest {

    private val createdAt: Instant = Instant.parse("2026-10-15T08:00:00Z")
    private val now: Instant = Instant.parse("2026-10-15T09:00:00Z")

    private fun task(status: TaskStatus) = Task(
        id = TaskId("t1"),
        title = "Study for GATE",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `starting an inbox task moves it to in progress`() {
        val result = TaskStatusMachine.transition(task(TaskStatus.INBOX), TaskStatus.IN_PROGRESS, now)
        val changed = result as TaskStatusMachine.Result.Changed
        assertEquals(TaskStatus.IN_PROGRESS, changed.task.status)
    }

    @Test
    fun `completing a task stamps the completion time`() {
        val result = TaskStatusMachine.transition(task(TaskStatus.PLANNED), TaskStatus.COMPLETED, now)
        val changed = result as TaskStatusMachine.Result.Changed
        assertEquals(TaskStatus.COMPLETED, changed.task.status)
        assertEquals(now, changed.task.completedAt)
    }

    @Test
    fun `completing an already completed task is unchanged and writes nothing`() {
        val result = TaskStatusMachine.transition(task(TaskStatus.COMPLETED), TaskStatus.COMPLETED, now)
        assertTrue(result is TaskStatusMachine.Result.Unchanged)
    }

    @Test
    fun `a completed task cannot be silently reactivated`() {
        val result = TaskStatusMachine.transition(task(TaskStatus.COMPLETED), TaskStatus.IN_PROGRESS, now)
        val rejected = result as TaskStatusMachine.Result.Rejected
        assertTrue(rejected.reason.contains("already completed"))
    }

    @Test
    fun `an archived task must be restored before it can change status`() {
        val result = TaskStatusMachine.transition(task(TaskStatus.ARCHIVED), TaskStatus.IN_PROGRESS, now)
        assertTrue((result as TaskStatusMachine.Result.Rejected).reason.contains("archived"))
    }

    @Test
    fun `completion postponement skip and archive are distinct transitions`() {
        val allowed = TaskStatusMachine.allowedTransitions(TaskStatus.PLANNED)
        assertTrue(TaskStatus.COMPLETED in allowed)
        assertTrue(TaskStatus.POSTPONED in allowed)
        assertTrue(TaskStatus.SKIPPED in allowed)
        assertTrue(TaskStatus.ARCHIVED in allowed)
        // None of these collapse into each other.
        assertEquals(4, listOf(TaskStatus.COMPLETED, TaskStatus.POSTPONED, TaskStatus.SKIPPED, TaskStatus.ARCHIVED).toSet().intersect(allowed).size)
    }

    @Test
    fun `postponing clears any completion timestamp`() {
        val completed = task(TaskStatus.COMPLETED).copy(completedAt = createdAt)
        val reopened = TaskStatusMachine.transition(completed, TaskStatus.ARCHIVED, now)
        assertNull((reopened as TaskStatusMachine.Result.Changed).task.completedAt)
    }

    @Test
    fun `moving back to the list clears a completion timestamp`() {
        val skipped = task(TaskStatus.SKIPPED).copy(completedAt = createdAt)
        val result = TaskStatusMachine.transition(skipped, TaskStatus.INBOX, now)
        assertNull((result as TaskStatusMachine.Result.Changed).task.completedAt)
        assertEquals(TaskStatus.INBOX, result.task.status)
    }

    @Test
    fun `there is no transition representing a card dismissal`() {
        // Dismissal is presentation state only. Nothing in the machine models it, which is the
        // structural guarantee behind CARD-003.
        val allTargets = TaskStatus.entries.toSet()
        val reachable = TaskStatus.entries.flatMap { TaskStatusMachine.allowedTransitions(it) }.toSet()
        assertEquals(allTargets, reachable)
        assertTrue(TaskStatus.entries.none { it.name.contains("DISMISS") })
    }

    @Test
    fun `active and open classifications exclude closed work`() {
        assertTrue(TaskStatus.INBOX.isActive)
        assertTrue(TaskStatus.POSTPONED.isActive)
        assertTrue(!TaskStatus.COMPLETED.isActive)
        assertTrue(!TaskStatus.ARCHIVED.isActive)
        assertTrue(TaskStatus.POSTPONED.isOpen)
        assertTrue(!TaskStatus.SKIPPED.isOpen)
    }
}