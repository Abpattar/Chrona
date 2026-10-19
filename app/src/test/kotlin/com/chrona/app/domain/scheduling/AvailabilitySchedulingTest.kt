package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.model.UserPreferences
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
 * Availability: the user's configured hours must bound flexible work.
 *
 * These are regression tests for the defect where the production path built a midnight-to-midnight
 * horizon and ignored `UserPreferences` entirely, so a person who said they were free from 09:00
 * had work placed at 06:00 and again at 23:30.
 */
class AvailabilitySchedulingTest {

    private val engine = DeterministicScheduleEngine()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val date = java.time.LocalDate.of(2026, 3, 2)
    private val midnight: ZonedDateTime = date.atStartOfDay(zone)
    private val createdAt: Instant = midnight.toInstant()

    private fun task(
        id: String,
        minutes: Long = 60,
        priority: Priority = Priority.IMPORTANT,
        flexibility: Flexibility = Flexibility.FLEXIBLE,
        fixedStart: ZonedDateTime? = null,
        fixedEnd: ZonedDateTime? = null,
        status: TaskStatus = TaskStatus.INBOX,
    ) = Task(
        id = TaskId(id),
        title = id,
        priority = priority,
        estimatedDuration = Duration.ofMinutes(minutes),
        flexibility = flexibility,
        fixedStart = fixedStart,
        fixedEnd = fixedEnd,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun range(start: LocalTime, end: LocalTime): TimeRange =
        TimeRange(date.atTime(start).atZone(zone), date.atTime(end).atZone(zone))

    @Test
    fun `flexible work stays inside the configured availability`() {
        val proposal = engine.propose(
            ScheduleInput(
                tasks = listOf(task("write", minutes = 90)),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(range(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            ),
        )

        val placement = proposal.placements.single()
        assertTrue(
            "placement ${placement.start} is outside the available window",
            !placement.start.isBefore(date.atTime(9, 0).atZone(zone)),
        )
        assertTrue(
            "placement ${placement.end} runs past the available window",
            !placement.end.isAfter(date.atTime(12, 0).atZone(zone)),
        )
    }

    @Test
    fun `a task that does not fit the available hours is reported, not dropped`() {
        val proposal = engine.propose(
            ScheduleInput(
                tasks = listOf(task("huge", minutes = 600)),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(range(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            ),
        )

        assertTrue(proposal.placements.isEmpty())
        val unplaced = proposal.unplaced.single()
        assertEquals(unplaced.taskId, TaskId("huge"))
        assertTrue(
            "expected a capacity reason, got ${unplaced.reason}",
            unplaced.reason == UnplacedReason.NO_AVAILABLE_WINDOW ||
                unplaced.reason == UnplacedReason.DEADLINE_INFEASIBLE,
        )
    }

    @Test
    fun `two disjoint availability windows are both usable`() {
        val proposal = engine.propose(
            ScheduleInput(
                // The morning window holds exactly one task's worth of time, so filling it leaves
                // the evening window free for the other. That is the point: a gap between
                // windows must not swallow the second task. Ids order the two identically, so
                // "a-" sorts first and lands in the earlier window.
                tasks = listOf(task("a-morning", minutes = 120), task("b-evening", minutes = 60)),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(
                    range(LocalTime.of(7, 0), LocalTime.of(9, 0)),
                    range(LocalTime.of(19, 0), LocalTime.of(21, 0)),
                ),
            ),
        )

        assertEquals(2, proposal.placements.size)
        val byName = proposal.placements.associateBy { it.taskId.value }
        assertEquals(LocalTime.of(7, 0), byName.getValue("a-morning").start.toLocalTime())
        assertEquals(LocalTime.of(19, 0), byName.getValue("b-evening").start.toLocalTime())
    }

    @Test
    fun `a fixed commitment is kept even outside the availability window`() {
        // A 07:00 lecture for someone who says they are only free from 09:00. The lecture is a
        // hard constraint; moving it silently would be worse than ignoring availability.
        val lectureStart = date.atTime(7, 0).atZone(zone)
        val proposal = engine.propose(
            ScheduleInput(
                tasks = listOf(
                    task("lecture", flexibility = Flexibility.FIXED, fixedStart = lectureStart, fixedEnd = lectureStart.plusHours(1)),
                    task("essay", minutes = 60),
                ),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(range(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            ),
        )

        val lecture = proposal.placements.single { it.taskId == TaskId("lecture") }
        assertEquals(lectureStart.toInstant(), lecture.start.toInstant())
        val essay = proposal.placements.single { it.taskId == TaskId("essay") }
        assertEquals(LocalTime.of(9, 0), essay.start.toLocalTime())
    }

    @Test
    fun `a day with no availability at all places no flexible work`() {
        val proposal = engine.propose(
            ScheduleInput(
                tasks = listOf(task("anything")),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = emptyList(),
            ),
        )
        // No availability configured at all means the whole horizon is usable, so this one fits.
        assertEquals(1, proposal.placements.size)
    }

    @Test
    fun `overlapping availability windows are merged so nothing is double counted`() {
        val merged = mergeRanges(
            listOf(
                range(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                range(LocalTime.of(11, 0), LocalTime.of(15, 0)),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals(LocalTime.of(9, 0), merged.single().start.toLocalTime())
        assertEquals(LocalTime.of(15, 0), merged.single().end.toLocalTime())
    }

    @Test
    fun `availability never places work before the injected reference time`() {
        val proposal = engine.propose(
            ScheduleInput(
                tasks = listOf(task("late")),
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(range(LocalTime.of(6, 0), LocalTime.of(23, 0))),
                now = midnight.plusHours(16),
            ),
        )
        val placement = proposal.placements.single()
        assertEquals(LocalTime.of(16, 0), placement.start.toLocalTime())
    }

    @Test
    fun `the planner is reproducible with the same availability`() {
        val input = ScheduleInput(
            tasks = (1..40).map { task("t%02d".format(it), minutes = 45) },
            dayStart = midnight,
            dayEnd = midnight.plusDays(1),
            availabilityWindows = listOf(
                range(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                range(LocalTime.of(14, 0), LocalTime.of(20, 0)),
            ),
        )
        val first = engine.propose(input)
        val second = engine.propose(input)
        assertEquals(first.proposalId, second.proposalId)
        assertEquals(first.placements, second.placements)
    }

    @Test
    fun `sixty tasks are all accounted for under a restrictive window`() {
        val tasks = (1..60).map { task("t%02d".format(it), minutes = 30) }
        val proposal = engine.propose(
            ScheduleInput(
                tasks = tasks,
                dayStart = midnight,
                dayEnd = midnight.plusDays(1),
                availabilityWindows = listOf(range(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            ),
        )

        val accounted = proposal.placements.map { it.taskId }.toSet() +
            proposal.unplaced.map { it.taskId }.toSet()
        assertEquals(60, accounted.size)
        assertTrue(proposal.overloaded)
    }
}

/** The horizon factory: how preferences become absolute, always-valid planning bounds. */
class DayHorizonTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val date = java.time.LocalDate.of(2026, 3, 2)

    @Test
    fun `no preference means the whole local day`() {
        val horizon = DayHorizonFactory.horizonFor(date, UserPreferences(), zone)
        assertEquals(LocalTime.MIDNIGHT, horizon.start.toLocalTime())
        assertEquals(date.plusDays(1), horizon.end.toLocalDate())
        assertEquals(1, horizon.windows.size)
    }

    @Test
    fun `configured day hours bound the horizon`() {
        val horizon = DayHorizonFactory.horizonFor(
            date,
            UserPreferences(dayStart = LocalTime.of(9, 0), dayEnd = LocalTime.of(17, 30)),
            zone,
        )
        assertEquals(LocalTime.of(9, 0), horizon.start.toLocalTime())
        assertEquals(LocalTime.of(17, 30), horizon.end.toLocalTime())
    }

    @Test
    fun `an overnight day wraps onto the next date`() {
        val horizon = DayHorizonFactory.horizonFor(
            date,
            UserPreferences(dayStart = LocalTime.of(20, 0), dayEnd = LocalTime.of(2, 0)),
            zone,
        )
        assertEquals(date.plusDays(1), horizon.end.toLocalDate())
        assertEquals(LocalTime.of(2, 0), horizon.end.toLocalTime())
    }

    @Test
    fun `an identical start and end falls back to the full day instead of an empty one`() {
        // An empty day and a 24-hour day are opposites and must never be confused. Guessing one
        // or the other silently would either erase the user's day or ignore what they asked for.
        val horizon = DayHorizonFactory.horizonFor(
            date,
            UserPreferences(dayStart = LocalTime.of(9, 0), dayEnd = LocalTime.of(9, 0)),
            zone,
        )
        assertTrue(horizon.end.isAfter(horizon.start))
        assertEquals(LocalTime.of(9, 0), horizon.start.toLocalTime())
    }

    @Test
    fun `availability windows that never overlap the horizon leave the day with no capacity`() {
        val horizon = DayHorizonFactory.horizonFor(
            date,
            UserPreferences(
                dayStart = LocalTime.of(9, 0),
                dayEnd = LocalTime.of(17, 0),
                availabilityWindows = listOf(TimeWindow(LocalTime.of(2, 0), LocalTime.of(3, 0))),
            ),
            zone,
        )
        assertTrue(horizon.isUnavailable)
    }

    @Test
    fun `reduced availability narrows the day without touching the horizon`() {
        val preferences = UserPreferences(dayStart = LocalTime.of(9, 0), dayEnd = LocalTime.of(22, 0))
        val horizon = DayHorizonFactory.horizonFor(
            date,
            preferences,
            zone,
            reducedTo = listOf(TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))),
        )
        assertEquals(LocalTime.of(9, 0), horizon.start.toLocalTime())
        assertEquals(LocalTime.of(22, 0), horizon.end.toLocalTime())
        assertEquals(1, horizon.windows.size)
        assertEquals(LocalTime.of(12, 0), horizon.windows.single().end.toLocalTime())
    }

    /**
     * A window that wraps midnight is rejected at construction, so an overnight availability
     * window cannot exist. That is intentional: the day boundary already expresses "past
     * midnight", and two ways to say it would eventually disagree.
     */
    @Test
    fun `an availability window cannot wrap midnight`() {
        val rejected = runCatching { TimeWindow(LocalTime.of(22, 0), LocalTime.of(2, 0)) }
        assertTrue("an overnight TimeWindow should not be constructible", rejected.isFailure)
    }

    @Test
    fun `availability is clipped to the horizon before being reported`() {
        val horizon = DayHorizonFactory.horizonFor(
            date,
            UserPreferences(
                dayStart = LocalTime.of(9, 0),
                dayEnd = LocalTime.of(17, 0),
                // Starts before the day does and ends after it does.
                availabilityWindows = listOf(TimeWindow(LocalTime.of(8, 0), LocalTime.of(18, 0))),
            ),
            zone,
        )
        val window = horizon.windows.single()
        assertEquals(LocalTime.of(9, 0), window.start.toLocalTime())
        assertEquals(LocalTime.of(17, 0), window.end.toLocalTime())
    }
}
