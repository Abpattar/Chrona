package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Multi-day planning.
 *
 * The planner must plan *further ahead than one day* without becoming a second scheduler: every
 * placement still comes from the single-day engine, and the reconciliation invariant — placed plus
 * reported equals the whole pool — must hold across the horizon.
 */
class MultiDaySchedulePlannerTest {

    private val planner = DeterministicMultiDayPlanner(DeterministicScheduleEngine())
    private val zone: ZoneId = ZoneId.of("UTC")
    private val start: LocalDate = LocalDate.of(2026, 4, 6)
    private val createdAt: Instant = start.atStartOfDay(zone).toInstant()

    private fun day(offset: Int): PlanningDay {
        val date = start.plusDays(offset.toLong())
        return PlanningDay(
            date = date,
            start = date.atTime(9, 0).atZone(zone),
            end = date.atTime(17, 0).atZone(zone),
        )
    }

    private fun days(count: Int = 5): List<PlanningDay> = (0 until count).map(::day)

    private fun task(
        id: String,
        minutes: Long = 60,
        priority: Priority = Priority.IMPORTANT,
        requestedOffset: Long? = null,
        deadlineOffset: Long? = null,
        deadlineTime: LocalTime = LocalTime.of(17, 0),
        flexibility: Flexibility = Flexibility.FLEXIBLE,
        fixedStart: ZonedDateTime? = null,
        fixedEnd: ZonedDateTime? = null,
        status: TaskStatus = TaskStatus.INBOX,
    ) = Task(
        id = TaskId(id),
        title = id,
        priority = priority,
        estimatedDuration = Duration.ofMinutes(minutes),
        requestedDate = requestedOffset?.let { start.plusDays(it) },
        deadline = deadlineOffset?.let { start.plusDays(it).atTime(deadlineTime).atZone(zone) },
        flexibility = flexibility,
        fixedStart = fixedStart,
        fixedEnd = fixedEnd,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `work with no room today is placed on a later day rather than dropped`() {
        // One 8-hour day of capacity cannot take three 6-hour tasks. A single-day engine would
        // report two as unplaceable; the whole point of the horizon is that tomorrow exists.
        // Two 8-hour days hold exactly two 6-hour tasks; the third has to wait for day 3.
        val outcome = planner.plan(
            MultiDayInput(
                days = days(3),
                tasks = listOf(task("a", minutes = 360), task("b", minutes = 360), task("c", minutes = 360)),
            ),
        )
        val plan = (outcome as Outcome.Success).value

        assertEquals(3, plan.placedTaskIds.size)
        assertTrue("every task should have a slot across the horizon", plan.unplaced.isEmpty())
        val dates = plan.placements.map { it.start.toLocalDate() }.toSet()
        // The first day cannot hold two 6-hour tasks, so the work must spread rather than the
        // overflow being reported as lost.
        assertEquals("work should spill into later days", 3, dates.size)
    }

    @Test
    fun `a task requested for a future day keeps that day`() {
        val outcome = planner.plan(
            MultiDayInput(
                days = days(5),
                tasks = listOf(task("lecture", requestedOffset = 3)),
            ),
        )
        val plan = (outcome as Outcome.Success).value
        assertEquals(start.plusDays(3), plan.placements.single().start.toLocalDate())
    }

    @Test
    fun `a fixed event stays on its own date while other days are replanned`() {
        // The days run to 23:00 so an evening party is inside the horizon at all; a fixed block
        // outside the horizon is a different case, and is reported rather than moved.
        val partyDate = start.plusDays(4)
        val lateDays = days(5).map { it.copy(end = it.end.withHour(23)) }
        val party = task(
            "party",
            minutes = 180,
            flexibility = Flexibility.FIXED,
            fixedStart = partyDate.atTime(19, 0).atZone(zone),
            fixedEnd = partyDate.atTime(22, 0).atZone(zone),
        )
        val outcome = planner.plan(
            MultiDayInput(
                days = lateDays,
                tasks = listOf(party) + (1..20).map { task("filler%02d".format(it), minutes = 90) },
            ),
        )
        val plan = (outcome as Outcome.Success).value

        val partyPlacement = plan.placements.single { it.taskId == TaskId("party") }
        assertEquals(partyDate, partyPlacement.start.toLocalDate())
        assertEquals(LocalTime.of(19, 0), partyPlacement.start.toLocalTime())
    }

    @Test
    fun `placed and unplaced together account for every eligible task`() {
        val tasks = (1..80).map { task("t%02d".format(it), minutes = 45) }
        val plan = (planner.plan(MultiDayInput(days = days(3), tasks = tasks)) as Outcome.Success).value

        assertTrue(plan.isReconciled(tasks.map { it.id }.toSet()))
        assertEquals(80, plan.placedTaskIds.size + plan.unplacedTaskIds.size)
    }

    @Test
    fun `a task placed on a later day is not also reported as unplaceable`() {
        // This is the bug a day-by-day loop invites: recording "no room" on day 1 and never
        // clearing it, so a task that is genuinely scheduled on day 2 still shows up as dropped.
        val plan = (planner.plan(
            MultiDayInput(
                days = days(2),
                tasks = listOf(task("a", minutes = 420), task("b", minutes = 60), task("c", minutes = 60)),
            ),
        ) as Outcome.Success).value

        assertTrue(plan.placedTaskIds.intersect(plan.unplacedTaskIds).isEmpty())
    }

    @Test
    fun `a dependency is planned after its prerequisite even across days`() {
        val prerequisite = task("research", minutes = 300)
        val dependent = task(
            "write-up",
            minutes = 60,
            deadlineOffset = 4,
        )
        val relationship = TaskRelationship(
            id = RelationId("r"),
            sourceTaskId = dependent.id,
            targetTaskId = prerequisite.id,
            type = RelationType.DEPENDS_ON,
            confirmedAt = createdAt,
            createdAt = createdAt,
        )
        val plan = (planner.plan(
            MultiDayInput(
                days = days(5),
                tasks = listOf(dependent, prerequisite),
                relationships = listOf(relationship),
            ),
        ) as Outcome.Success).value

        val pre = plan.placements.single { it.taskId == prerequisite.id }
        val post = plan.placements.single { it.taskId == dependent.id }
        assertTrue(
            "the dependent must not start before its prerequisite finishes",
            !post.start.isBefore(pre.end),
        )
    }

    @Test
    fun `overlapping days are refused rather than double counted`() {
        val outcome = planner.plan(
            MultiDayInput(
                days = listOf(day(0), day(0)),
                tasks = listOf(task("a")),
            ),
        )
        assertTrue(outcome is Outcome.Failed)
        assertTrue((outcome as Outcome.Failed).failure is Failure.Validation)
    }

    @Test
    fun `an empty horizon is refused with a reason`() {
        val outcome = planner.plan(MultiDayInput(days = emptyList(), tasks = listOf(task("a"))))
        assertTrue(outcome is Outcome.Failed)
        assertNotNull((outcome as Outcome.Failed).failure)
    }

    @Test
    fun `completed work is never planned again`() {
        val plan = (planner.plan(
            MultiDayInput(
                days = days(3),
                tasks = listOf(task("done", status = TaskStatus.COMPLETED), task("todo")),
            ),
        ) as Outcome.Success).value

        assertEquals(setOf(TaskId("todo")), plan.placedTaskIds)
        assertTrue(plan.unplaced.none { it.taskId == TaskId("done") })
    }

    @Test
    fun `movement is reported against the previous plan`() {
        val previous = listOf(
            SchedulePlacement(TaskId("a"), start.atTime(9, 0).atZone(zone), start.atTime(10, 0).atZone(zone)),
        )
        val plan = (planner.plan(
            MultiDayInput(
                days = days(2),
                // "0-" sorts before "a", so the bulky task claims 09:00 and `a` is displaced.
                tasks = listOf(task("a"), task("0-bulky", minutes = 300)),
                previousPlacements = previous,
            ),
        ) as Outcome.Success).value

        val change = plan.changes.firstOrNull { it.taskId == TaskId("a") }
        assertNotNull("expected the displaced task to be reported as moved", change)
        assertTrue(change!!.fromStart != null)
        assertTrue(change.toStart != null)
    }

    @Test
    fun `sixty tasks across ten days all get accounted for`() {
        val tasks = (1..60).map { task("t%03d".format(it), minutes = 60) }
        val plan = (planner.plan(MultiDayInput(days = days(10), tasks = tasks)) as Outcome.Success).value

        assertTrue(plan.isReconciled(tasks.map { it.id }.toSet()))
        assertEquals(60, plan.days.sumOf { it.placements.size })
    }

    @Test
    fun `availability that leaves a day empty only affects that day`() {
        val closedDay = day(1).copy(windows = emptyList(), isAvailable = false)
        val plan = (planner.plan(
            MultiDayInput(
                days = listOf(day(0), closedDay, day(2)),
                tasks = (1..6).map { task("t%d".format(it), minutes = 120) },
            ),
        ) as Outcome.Success).value

        val onClosedDay = plan.placements.filter { it.start.toLocalDate() == closedDay.date }
        assertTrue("an empty day must not receive placements", onClosedDay.isEmpty())
        assertTrue("other days should still take work", plan.placedTaskIds.isNotEmpty())
    }

    @Test
    fun `the same input always produces the same plan`() {
        val input = MultiDayInput(
            days = days(5),
            tasks = (1..40).map { task("t%02d".format(it), minutes = 50) },
        )
        val first = (planner.plan(input) as Outcome.Success).value
        val second = (planner.plan(input) as Outcome.Success).value
        assertEquals(first.placements, second.placements)
        assertEquals(first.unplaced, second.unplaced)
    }

    @Test
    fun `placements never overlap each other`() {
        val plan = (planner.plan(
            MultiDayInput(
                days = days(3),
                tasks = (1..25).map { task("t%02d".format(it), minutes = 90) },
            ),
        ) as Outcome.Success).value

        val sorted = plan.placements.sortedBy { it.start }
        sorted.zipWithNext().forEach { (a, b) ->
            assertFalse(
                "${a.taskId.value} overlaps ${b.taskId.value}",
                a.start.isBefore(b.end) && b.start.isBefore(a.end),
            )
        }
    }

    @Test
    fun `no placement falls outside the days it was given`() {
        val window = days(4)
        val plan = (planner.plan(
            MultiDayInput(
                days = window,
                tasks = (1..30).map { task("t%02d".format(it), minutes = 120) },
            ),
        ) as Outcome.Success).value

        val allowed = window.flatMap { listOf(it.date to it.start, it.date to it.end) }.toSet()
        plan.placements.forEach { placement ->
            assertTrue(
                "${placement.taskId.value} at ${placement.start} is outside the horizon",
                allowed.any { (date, bound) -> placement.start.toLocalDate() == date && !placement.end.isAfter(bound) },
            )
        }
    }

    @Test
    fun `fixed commitments the engine cannot keep are reported as conflicts`() {
        val at9 = start.atTime(9, 0).atZone(zone)
        val clash = listOf(
            task("first", flexibility = Flexibility.FIXED, fixedStart = at9, fixedEnd = at9.plusHours(1)),
            task("second", flexibility = Flexibility.FIXED, fixedStart = at9.plusMinutes(30), fixedEnd = at9.plusMinutes(90)),
        )
        val plan = (planner.plan(MultiDayInput(days = days(1), tasks = clash)) as Outcome.Success).value

        // Both cannot keep their time; exactly one is kept and the other is reported.
        assertEquals(1, plan.placedTaskIds.size)
        assertEquals(1, plan.unplacedTaskIds.size)
        assertTrue(plan.unplaced.single().reason == UnplacedReason.FIXED_CONFLICT)
    }

    @Test
    fun `an explicit commitment also reserves its time`() {
        val at9 = start.atTime(9, 0).atZone(zone)
        val plan = (planner.plan(
            MultiDayInput(
                days = days(1),
                tasks = listOf(task("a", minutes = 120)),
                fixedCommitments = listOf(
                    FixedCommitment("c1", "Lecture", TimeRange(at9, at9.plusHours(3))),
                ),
            ),
        ) as Outcome.Success).value

        val placement = plan.placements.single()
        assertFalse("flexible work was placed over a fixed commitment", placement.start.isBefore(at9.plusHours(3)))
    }
}
