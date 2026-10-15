package com.chrona.app.domain.task

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Field-level validation rules (TASK-002, `PHASE_02` "Invalid records return typed errors"). */
class TaskValidatorTest {

    private val now: Instant = Instant.parse("2026-10-15T08:00:00Z")

    private fun task(
        title: String = "Study for GATE",
        duration: Duration? = Duration.ofHours(2),
        flexibility: Flexibility = Flexibility.FLEXIBLE,
        fixedStart: ZonedDateTime? = null,
        fixedEnd: ZonedDateTime? = null,
        splittable: Boolean = false,
        minimumSession: Duration? = null,
        deadline: ZonedDateTime? = null,
        earliestStart: ZonedDateTime? = null,
        assumptions: List<String> = emptyList(),
    ) = Task(
        id = TaskId("t1"),
        title = title,
        estimatedDuration = duration,
        flexibility = flexibility,
        fixedStart = fixedStart,
        fixedEnd = fixedEnd,
        splittable = splittable,
        minimumSession = minimumSession,
        deadline = deadline,
        earliestStart = earliestStart,
        createdAt = now,
        updatedAt = now,
        assumptions = assumptions,
    )

    @Test
    fun `a well formed task has no problems`() {
        assertEquals(emptyList<String>(), TaskValidator.validate(task()))
    }

    @Test
    fun `a blank title is rejected`() {
        assertTrue(TaskValidator.validate(task(title = "   ")).any { it.contains("needs a title") })
    }

    @Test
    fun `an over long title is rejected`() {
        val problems = TaskValidator.validate(task(title = "x".repeat(201)))
        assertTrue(problems.any { it.contains("at most 200") })
    }

    @Test
    fun `a missing duration is allowed for an inbox item`() {
        assertEquals(emptyList<String>(), TaskValidator.validate(task(duration = null)))
    }

    @Test
    fun `a zero duration is rejected`() {
        assertTrue(TaskValidator.validate(task(duration = Duration.ZERO)).isNotEmpty())
    }

    @Test
    fun `a duration over a day is rejected`() {
        val problems = TaskValidator.validate(task(duration = Duration.ofHours(25)))
        assertTrue(problems.any { it.contains("24 hours") })
    }

    @Test
    fun `a fixed task without times is rejected`() {
        val problems = TaskValidator.validate(task(flexibility = Flexibility.FIXED))
        assertTrue(problems.any { it.contains("needs both a start and an end") })
    }

    @Test
    fun `a fixed task ending before it starts is rejected`() {
        val start = now.atZone(ZoneOffset.UTC)
        val problems = TaskValidator.validate(
            task(flexibility = Flexibility.FIXED, fixedStart = start, fixedEnd = start.minusHours(1)),
        )
        assertTrue(problems.any { it.contains("end after it starts") })
    }

    @Test
    fun `a flexible task with fixed times is rejected`() {
        val start = now.atZone(ZoneOffset.UTC)
        val problems = TaskValidator.validate(
            task(flexibility = Flexibility.FLEXIBLE, fixedStart = start, fixedEnd = start.plusHours(1)),
        )
        assertTrue(problems.any { it.contains("flexible task cannot have fixed") })
    }

    @Test
    fun `a minimum session on a non splittable task is rejected`() {
        val problems = TaskValidator.validate(
            task(splittable = false, minimumSession = Duration.ofMinutes(20)),
        )
        assertTrue(problems.any { it.contains("can be split") })
    }

    @Test
    fun `a minimum session longer than the duration is rejected`() {
        val problems = TaskValidator.validate(
            task(duration = Duration.ofMinutes(30), splittable = true, minimumSession = Duration.ofHours(2)),
        )
        assertTrue(problems.any { it.contains("longer than the estimated duration") })
    }

    @Test
    fun `a deadline in the past is rejected`() {
        val past = now.minusSeconds(60).atZone(ZoneOffset.UTC)
        assertTrue(TaskValidator.validate(task(deadline = past)).any { it.contains("already in the past") })
    }

    @Test
    fun `an earliest start after the deadline is rejected`() {
        val deadline = now.plusSeconds(3600).atZone(ZoneOffset.UTC)
        val earliest = now.plusSeconds(7200).atZone(ZoneOffset.UTC)
        val problems = TaskValidator.validate(task(deadline = deadline, earliestStart = earliest))
        assertTrue(problems.any { it.contains("cannot be scheduled") })
    }

    @Test
    fun `too many assumptions are rejected`() {
        val problems = TaskValidator.validate(task(assumptions = List(50) { "guess $it" }))
        assertTrue(problems.any { it.contains("interpretation assumptions") })
    }

    @Test
    fun `a manual fixed commitment must end after it starts`() {
        val start = ZonedDateTime.of(LocalDate.of(2026, 10, 15), java.time.LocalTime.of(10, 0), ZoneOffset.UTC)
        val bad = com.chrona.app.domain.model.CalendarEvent(
            id = com.chrona.app.domain.model.EventId("e1"),
            title = "Class",
            startAt = start,
            endAt = start.minusHours(1),
            zoneId = ZoneOffset.UTC.id,
            createdAt = now,
            updatedAt = now,
        )
        assertTrue(CalendarEventValidator.validate(bad).any { it.contains("end after it starts") })
    }

    @Test
    fun `status defaults to inbox and a new task is not planned`() {
        val fresh = task()
        assertEquals(TaskStatus.INBOX, fresh.status)
        assertEquals(Priority.IMPORTANT, fresh.priority)
    }
}