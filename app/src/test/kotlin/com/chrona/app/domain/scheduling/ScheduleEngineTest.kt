package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Acceptance tests for the deterministic scheduler (CH-SCHED-001..005).
 *
 * The engine must be explainable and repeatable, so each test asserts one rule and its reason.
 */
class ScheduleEngineTest {
    private val engine = DeterministicScheduleEngine()
    private val zone = ZoneId.of("UTC")
    private val base = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone)
    private val end = base.plusHours(8)
    private val createdAt: Instant = base.toInstant()

    private fun task(
        id: String,
        priority: Priority = Priority.IMPORTANT,
        duration: Duration? = Duration.ofMinutes(60),
        status: TaskStatus = TaskStatus.INBOX,
        deadline: ZonedDateTime? = null,
        earliestStart: ZonedDateTime? = null,
        preferredWindow: TimeWindow? = null,
        flexibility: Flexibility = Flexibility.FLEXIBLE,
        fixedStart: ZonedDateTime? = null,
        fixedEnd: ZonedDateTime? = null,
        splittable: Boolean = false,
        minimumSession: Duration? = null,
    ) = Task(
        id = TaskId(id),
        title = id,
        priority = priority,
        estimatedDuration = duration,
        deadline = deadline,
        earliestStart = earliestStart,
        preferredWindow = preferredWindow,
        flexibility = flexibility,
        fixedStart = fixedStart,
        fixedEnd = fixedEnd,
        splittable = splittable,
        minimumSession = minimumSession,
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
    fun `places simple task in first interval`() {
        val p = engine.propose(ScheduleInput(listOf(task("t1", duration = Duration.ofMinutes(30))), emptyList(), base, end))
        assertEquals(1, p.placements.size)
        assertEquals(base, p.placements.single().start)
    }

    @Test
    fun `respects fixed commitment`() {
        val fix = FixedCommitment("c1", "Class", TimeRange(base.plusHours(1), base.plusHours(2)))
        val p = engine.propose(
            ScheduleInput(
                listOf(task("a", Priority.CRITICAL), task("b", Priority.IMPORTANT)),
                listOf(fix), base, end, buffer = Duration.ZERO,
            ),
        )
        assertTrue(p.placements.isNotEmpty())
        assertTrue(p.placements.none { it.start < base.plusHours(2) && it.start >= base.plusHours(1) })
    }

    @Test
    fun `priority decides order among independent tasks`() {
        val day = base.plusHours(2)
        val p = engine.propose(
            ScheduleInput(
                listOf(task("low", Priority.LOW), task("critical", Priority.CRITICAL)),
                emptyList(), base, day, buffer = Duration.ZERO,
            ),
        )
        val critical = p.placements.single { it.taskId == TaskId("critical") }
        val low = p.placements.single { it.taskId == TaskId("low") }
        assertTrue(critical.start.isBefore(low.start))
    }

    @Test
    fun `fixed task is placed at its own time and never moved`() {
        val fixedStart = base.plusHours(2)
        val fixedEnd = base.plusHours(2).plusMinutes(45)
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task(
                        "fixed", flexibility = Flexibility.FIXED,
                        fixedStart = fixedStart, fixedEnd = fixedEnd,
                    ),
                ),
                emptyList(), base, end,
            ),
        )
        val placement = p.placements.single { it.taskId == TaskId("fixed") }
        assertEquals(fixedStart, placement.start)
        assertEquals(fixedEnd, placement.end)
    }

    @Test
    fun `fixed task clashing with a commitment is reported, not silently moved`() {
        val fix = FixedCommitment("c1", "Class", TimeRange(base.plusHours(1), base.plusHours(2)))
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task(
                        "fixed", flexibility = Flexibility.FIXED,
                        fixedStart = base.plusHours(1).plusMinutes(30),
                        fixedEnd = base.plusHours(2).plusMinutes(30),
                    ),
                ),
                listOf(fix), base, end,
            ),
        )
        assertTrue(p.placements.none { it.taskId == TaskId("fixed") })
        assertEquals(UnplacedReason.FIXED_CONFLICT, p.unplaced.single().reason)
    }

    @Test
    fun `hard dependency is scheduled before its dependent`() {
        val day = base.plusHours(3)
        val p = engine.propose(
            ScheduleInput(
                listOf(task("b-draw", duration = Duration.ofMinutes(60)), task("a-prep", duration = Duration.ofMinutes(60))),
                emptyList(), base, day,
                relationships = listOf(dependsOn("b-draw", "a-prep")),
                buffer = Duration.ZERO,
            ),
        )
        val prep = p.placements.single { it.taskId == TaskId("a-prep") }
        val draw = p.placements.single { it.taskId == TaskId("b-draw") }
        assertTrue(!draw.start.isBefore(prep.end))
    }

    @Test
    fun `dependency loop leaves affected work unplanned with an explanation`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("a"), task("b")),
                emptyList(), base, end,
                relationships = listOf(dependsOn("a", "b"), dependsOn("b", "a")),
            ),
        )
        assertTrue(p.placements.isEmpty())
        assertEquals(2, p.unplaced.size)
        assertTrue(p.unplaced.all { it.reason == UnplacedReason.DEPENDENCY_INCOMPLETE })
        assertTrue(p.assumptions.any { it.text.contains("loop") })
    }

    @Test
    fun `deadline that cannot be met is reported and flags overload`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("t", duration = Duration.ofHours(3), deadline = base.plusHours(1))),
                emptyList(), base, end,
            ),
        )
        assertEquals(UnplacedReason.DEADLINE_INFEASIBLE, p.unplaced.single().reason)
        assertTrue(p.overloaded)
        assertTrue(p.assumptions.any { it.text.contains("capacity") })
    }

    @Test
    fun `splittable task is split across free gaps`() {
        val commitments = listOf(
            FixedCommitment("c1", "A", TimeRange(base, base.plusHours(1))),
            FixedCommitment("c2", "B", TimeRange(base.plusHours(2), base.plusHours(3))),
        )
        val p = engine.propose(
            ScheduleInput(
                listOf(
                    task(
                        "split", duration = Duration.ofMinutes(90),
                        splittable = true, minimumSession = Duration.ofMinutes(30),
                    ),
                ),
                commitments, base, base.plusHours(4), buffer = Duration.ZERO,
            ),
        )
        assertEquals(2, p.placements.count { it.taskId == TaskId("split") })
    }

    @Test
    fun `buffer is kept clear around commitments`() {
        val fix = FixedCommitment("c1", "Class", TimeRange(base.plusHours(1), base.plusHours(2)))
        val p = engine.propose(
            ScheduleInput(
                listOf(task("t", duration = Duration.ofMinutes(60))),
                listOf(fix), base, base.plusHours(4), buffer = Duration.ofMinutes(30),
            ),
        )
        val placement = p.placements.single()
        assertTrue(!placement.start.isBefore(base.plusHours(2).plusMinutes(30)))
    }

    @Test
    fun `earliest start is honored`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("t", earliestStart = base.plusHours(2))),
                emptyList(), base, base.plusHours(4),
            ),
        )
        assertEquals(base.plusHours(2), p.placements.single().start)
    }

    @Test
    fun `preferred window is a soft hint`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("t", preferredWindow = TimeWindow(LocalTime.of(11, 0), LocalTime.of(12, 0)))),
                emptyList(), base, base.plusHours(5),
            ),
        )
        assertEquals(LocalTime.of(11, 0), p.placements.single().start.toLocalTime())
    }

    @Test
    fun `missing duration assumes a documented default`() {
        val p = engine.propose(ScheduleInput(listOf(task("t", duration = null)), emptyList(), base, end))
        assertEquals(1, p.placements.size)
        assertTrue(p.assumptions.any { it.text.contains("30 minutes") })
    }

    @Test
    fun `invalid duration is refused, never placed`() {
        val p = engine.propose(
            ScheduleInput(listOf(task("t", duration = Duration.ZERO)), emptyList(), base, end),
        )
        assertTrue(p.placements.isEmpty())
        assertEquals(UnplacedReason.INVALID_DURATION, p.unplaced.single().reason)
    }

    @Test
    fun `same input yields the same proposal id`() {
        val tasks = listOf(task("a"), task("b", Priority.LOW))
        val first = engine.propose(ScheduleInput(tasks, emptyList(), base, end))
        val second = engine.propose(ScheduleInput(tasks, emptyList(), base, end))
        assertEquals(first.proposalId, second.proposalId)
    }

    @Test
    fun `movement against the previous plan is explained`() {
        val previous = listOf(SchedulePlacement(TaskId("t"), base.minusHours(1), base, 0))
        val p = engine.propose(
            ScheduleInput(listOf(task("t")), emptyList(), base, end, previousPlacements = previous),
        )
        assertNotNull(p.changes.firstOrNull { it.taskId == TaskId("t") })
        assertTrue(p.changes.any { it.explanation.contains("Moved") })
    }

    @Test
    fun `completed tasks are never scheduled`() {
        val p = engine.propose(
            ScheduleInput(
                listOf(task("done", status = TaskStatus.COMPLETED), task("open")),
                emptyList(), base, end,
            ),
        )
        assertTrue(p.placements.none { it.taskId == TaskId("done") })
        assertEquals(1, p.placements.size)
        assertFalse(p.unplaced.any { it.taskId == TaskId("done") })
    }

    @Test
    fun `late wake-up pushes scheduling to now, never the past`() {
        val now = base.plusHours(3)
        val p = engine.propose(
            ScheduleInput(listOf(task("t")), emptyList(), base, end, now = now),
        )
        assertEquals(now, p.placements.single().start)
    }

    @Test
    fun `an incoming task is reported as newly planned`() {
        val previous = listOf(SchedulePlacement(TaskId("a-task"), base, base.plusHours(1), 0))
        val p = engine.propose(
            ScheduleInput(
                listOf(task("a-task"), task("b-task")),
                emptyList(), base, base.plusHours(3),
                previousPlacements = previous, buffer = Duration.ZERO,
            ),
        )
        val incoming = p.changes.single { it.taskId == TaskId("b-task") }
        assertTrue(incoming.explanation.contains("Newly planned"))
    }

    @Test
    fun `a task that no longer fits is reported, never silently dropped`() {
        val previous = listOf(SchedulePlacement(TaskId("t"), base, base.plusHours(1), 0))
        val full = FixedCommitment("c", "All day", TimeRange(base, end))
        val p = engine.propose(
            ScheduleInput(
                listOf(task("t")), listOf(full), base, end,
                previousPlacements = previous, buffer = Duration.ZERO,
            ),
        )
        assertTrue(p.unplaced.any { it.taskId == TaskId("t") })
        assertTrue(p.changes.any { it.taskId == TaskId("t") && it.explanation.contains("Could not be placed") })
    }

    @Test
    fun `a DST spring-forward day keeps placements inside the real day`() {
        val ny = ZoneId.of("America/New_York")
        val dayStart = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny)
        val dayEnd = dayStart.plusDays(1)
        // Spans the 02:00→03:00 gap, so the free-time math must use real instants.
        val morning = FixedCommitment(
            "c", "Sleep",
            TimeRange(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, ny), ZonedDateTime.of(2026, 3, 8, 4, 0, 0, 0, ny)),
        )
        val p = engine.propose(
            ScheduleInput(listOf(task("t")), listOf(morning), dayStart, dayEnd, buffer = Duration.ZERO),
        )
        val placement = p.placements.single()
        assertFalse(placement.start.isBefore(dayStart))
        assertFalse(placement.end.isAfter(dayEnd))
    }
}
