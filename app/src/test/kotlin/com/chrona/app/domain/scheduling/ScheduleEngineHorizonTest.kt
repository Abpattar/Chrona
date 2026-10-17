package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regression tests for the two scheduling-correctness defects found in the P04 policy:
 * requested dates were ignored, and a priority-first order could miss a feasible deadline.
 *
 * Each test states the invariant it protects so a future change that re-breaks one is obvious.
 */
class ScheduleEngineHorizonTest {
    private val engine = DeterministicScheduleEngine()
    private val zone = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone)
    private val day = base.toLocalDate()
    private val createdAt: Instant = base.toInstant()

    private fun task(
        id: String,
        priority: Priority = Priority.IMPORTANT,
        duration: Duration? = Duration.ofMinutes(60),
        status: TaskStatus = TaskStatus.INBOX,
        requestedDate: LocalDate? = null,
        deadline: ZonedDateTime? = null,
    ) = Task(
        id = TaskId(id),
        title = id,
        priority = priority,
        estimatedDuration = duration,
        requestedDate = requestedDate,
        deadline = deadline,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun dependsOn(dependent: String, prerequisite: String) = TaskRelationship(
        id = RelationId("r-$dependent-$prerequisite"),
        sourceTaskId = TaskId(dependent),
        targetTaskId = TaskId(prerequisite),
        type = RelationType.DEPENDS_ON,
        confirmedAt = createdAt,
        createdAt = createdAt,
    )

    @Test
    fun `a task requested for a later day is left for that day, not planned today`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("tomorrow", requestedDate = day.plusDays(1))),
                emptyList(), base, base.plusHours(4),
            ),
        )
        assertTrue("nothing should be placed today", p.placements.isEmpty())
        assertEquals(UnplacedReason.OUTSIDE_HORIZON, p.unplaced.single().reason)
        assertFalse("a future task is not a capacity failure", p.overloaded)
        assertTrue(p.assumptions.any { it.text.contains("another day") })
    }

    @Test
    fun `a task requested for today is planned normally`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("today", requestedDate = day)),
                emptyList(), base, base.plusHours(4),
            ),
        )
        assertEquals(1, p.placements.size)
        assertTrue(p.unplaced.isEmpty())
    }

    @Test
    fun `an overdue open task is carried forward with a stated assumption`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("overdue", requestedDate = day.minusDays(1))),
                emptyList(), base, base.plusHours(4),
            ),
        )
        assertEquals(1, p.placements.size)
        assertTrue(p.assumptions.any { it.text.contains("still open") })
    }

    @Test
    fun `deadline-aware order avoids a feasible deadline miss`() {
        // Horizon 08:00-12:00 (240m). B needs 60m and must finish by 09:00, so B runs first.
        // The old priority-first order placed CRITICAL A first and then rejected B although
        // B-then-A fits exactly. Both must now be placed.
        val fourHours = base.plusHours(4)
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task("A", priority = Priority.CRITICAL, duration = Duration.ofMinutes(180), deadline = fourHours),
                    task("B", priority = Priority.IMPORTANT, duration = Duration.ofMinutes(60), deadline = base.plusHours(1)),
                ),
                emptyList(), base, fourHours, buffer = Duration.ZERO,
            ),
        )
        val b = p.placements.singleOrNull { it.taskId == TaskId("B") }
        val a = p.placements.singleOrNull { it.taskId == TaskId("A") }
        assertEquals("B must not be rejected when a feasible order exists", null, p.unplaced.firstOrNull { it.taskId == TaskId("B") })
        assertTrue("both tasks fit", b != null && a != null)
        assertTrue("B meets its 09:00 deadline", !b!!.end.isAfter(base.plusHours(1)))
        assertTrue("A meets its 12:00 deadline", !a!!.end.isAfter(fourHours))
    }

    @Test
    fun `a task for another day does not make today look overloaded`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task("today", duration = Duration.ofMinutes(30)),
                    task("future1", requestedDate = day.plusDays(3)),
                    task("future2", requestedDate = day.plusDays(9)),
                ),
                emptyList(), base, base.plusHours(2),
            ),
        )
        assertEquals(1, p.placements.size)
        assertEquals(2, p.unplaced.count { it.reason == UnplacedReason.OUTSIDE_HORIZON })
        assertFalse(p.overloaded)
    }

    @Test
    fun `a dependent whose prerequisite is deferred is reported, not guessed`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task("draw", requestedDate = day.plusDays(1)),
                    task("prep"),
                ),
                emptyList(), base, base.plusHours(4),
                relationships = listOf(dependsOn("prep", "draw")),
            ),
        )
        assertTrue(p.placements.isEmpty())
        assertTrue(p.unplaced.any { it.taskId == TaskId("prep") && it.reason == UnplacedReason.DEPENDENCY_INCOMPLETE })
        assertTrue(p.unplaced.any { it.taskId == TaskId("draw") && it.reason == UnplacedReason.OUTSIDE_HORIZON })
    }

    @Test
    fun `every eligible task appears exactly once as placed or unplaced`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task("a", requestedDate = day.plusDays(1)),
                    task("b", duration = Duration.ofMinutes(60)),
                    task("c", duration = Duration.ofMinutes(60), deadline = base.plusHours(2)),
                    task("done", status = TaskStatus.COMPLETED),
                ),
                emptyList(), base, base.plusHours(3), buffer = Duration.ZERO,
            ),
        )
        val placedIds = p.placements.map { it.taskId }.toSet()
        val unplacedIds = p.unplaced.map { it.taskId }
        val represented = placedIds + unplacedIds.toSet()
        assertEquals(setOf(TaskId("a"), TaskId("b"), TaskId("c")), represented)
        assertEquals("no task appears twice in unplaced", unplacedIds.size, unplacedIds.toSet().size)
        assertTrue("a completed task is neither placed nor unplaced", TaskId("done") !in represented)
        assertTrue("placed and unplaced are disjoint", (placedIds intersect unplacedIds.toSet()).isEmpty())
    }
}
