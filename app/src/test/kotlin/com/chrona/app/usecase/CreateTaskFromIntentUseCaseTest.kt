package com.chrona.app.usecase

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.parse.DeterministicTaskIntentParser
import com.chrona.app.domain.parse.DraftField
import com.chrona.app.domain.parse.ParseAssumption
import com.chrona.app.domain.parse.ParsePreferences
import com.chrona.app.domain.parse.TaskIntentDraft
import com.chrona.app.domain.parse.TaskIntentDraftValidator
import com.chrona.app.domain.parse.TaskUnderstandingRequest
import com.chrona.app.domain.parse.TaskUnderstandingResult
import com.chrona.app.domain.parse.toTaskCandidate
import com.chrona.app.domain.task.TaskValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The voice path from transcript to stored task.
 *
 * The point of these tests is that voice is not a second, laxer entry point: the draft travels
 * through the same validator and the same repository as typing, and only `origin` and the
 * assumption sentences differ.
 */
class CreateTaskFromIntentUseCaseTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val createTask = CreateTaskUseCase(
        repository,
        Fixtures.SequentialIdGenerator(),
        Fixtures.FixedTimeSource(),
    )
    private val createFromIntent = CreateTaskFromIntentUseCase(createTask)
    private val parser = DeterministicTaskIntentParser()

    @Test
    fun `a spoken task becomes the same kind of record as a typed one`() = runTest {
        val draft = draftFor("Study for GATE for two hours tomorrow")

        val task = (createFromIntent(draft) as Outcome.Success).value

        assertEquals("Study for GATE", task.title)
        assertEquals(TaskOrigin.VOICE, task.origin)
        assertEquals(TaskStatus.INBOX, task.status)
        assertEquals(Duration.ofHours(2), task.estimatedDuration)
        assertEquals(LocalDate.of(2026, 10, 16), task.requestedDate)
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun `the assumption sentences are stored with the task, not the transcript`() = runTest {
        val task = (createFromIntent(draftFor("study for gate tomorrow")) as Outcome.Success).value

        assertTrue(task.assumptions.any { it.contains("2026-10-16") })
        assertFalse(
            "the transcript itself must never be persisted",
            task.assumptions.any { it.contains("study for gate tomorrow") },
        )
        assertFalse(
            "raw audio or transcript storage has no field at all",
            task.assumptions.any { it.contains("audio", ignoreCase = true) },
        )
    }

    @Test
    fun `a fixed spoken block keeps its start and end`() = runTest {
        val draft = draftFor("dentist appointment from 3pm to 4pm tomorrow")

        val task = (createFromIntent(draft) as Outcome.Success).value

        assertEquals(Flexibility.FIXED, task.flexibility)
        assertEquals(LocalTime.of(15, 0), task.fixedStart?.toLocalTime())
        assertEquals(LocalTime.of(16, 0), task.fixedEnd?.toLocalTime())
        assertEquals(TimeRange(requireNotNull(task.fixedStart), requireNotNull(task.fixedEnd)), task.fixedRange)
    }

    @Test
    fun `an unstated priority becomes the conservative default`() = runTest {
        val task = (createFromIntent(draftFor("water the plants tomorrow")) as Outcome.Success).value
        assertEquals(Priority.IMPORTANT, task.priority)
    }

    @Test
    fun `a spoken priority is kept`() = runTest {
        val task = (createFromIntent(draftFor("urgent dentist visit tomorrow")) as Outcome.Success).value
        assertEquals(Priority.CRITICAL, task.priority)
    }

    @Test
    fun `a draft the domain rules reject is not persisted`() = runTest {
        // A deadline that has already passed is a canonical rule, not a parser rule, so the
        // typed path would refuse the same task.
        val draft = TaskIntentDraft(
            title = "Hand in the report",
            deadline = ZonedDateTime.of(2026, 10, 1, 18, 0, 0, 0, ZONE),
        )

        val result = createFromIntent(draft)

        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `a fixed draft with no end time is refused by the canonical rules`() = runTest {
        val draft = TaskIntentDraft(title = "Dentist", flexibility = Flexibility.FIXED)

        val result = createFromIntent(draft)

        assertTrue((result as Outcome.Failed).failure is Failure.Validation)
    }

    @Test
    fun `the same transcript and reference time always store the same task`() = runTest {
        val first = (createFromIntent(draftFor("submit the form by friday")) as Outcome.Success).value
        val second = (createFromIntent(draftFor("submit the form by friday")) as Outcome.Success).value

        assertEquals(first.title, second.title)
        assertEquals(first.deadline, second.deadline)
        assertEquals(first.assumptions, second.assumptions)
        assertTrue("two confirmations must be two distinct tasks", first.id != second.id)
    }

    /** Parses with the production parser, so the draft is the one a user would really see. */
    private suspend fun draftFor(text: String): TaskIntentDraft {
        val result = parser.understand(
            TaskUnderstandingRequest(
                text = text,
                referenceTime = ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZONE),
                zoneId = ZONE,
                preferences = ParsePreferences(),
            ),
        )
        assertTrue("\"$text\" did not parse: $result", result is TaskUnderstandingResult.Parsed)
        return (result as TaskUnderstandingResult.Parsed).draft
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("UTC")
    }
}

/**
 * The draft validation bridge itself.
 *
 * It exists so a draft is checked by [TaskValidator] rather than by a second copy of the rules.
 */
class TaskIntentDraftValidatorTest {

    @Test
    fun `a draft projected onto a task keeps every parsed field`() {
        val draft = TaskIntentDraft(
            title = "  Study for GATE  ",
            detail = "past papers",
            priority = Priority.CRITICAL,
            estimatedDuration = Duration.ofHours(2),
            deadline = ZonedDateTime.of(2026, 10, 16, 17, 0, 0, 0, ZONE),
            requestedDate = LocalDate.of(2026, 10, 16),
            preferredWindow = TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
            splittable = true,
            minimumSession = Duration.ofMinutes(30),
            assumptions = listOf(ParseAssumption(DraftField.DURATION, "Splitting is allowed.")),
        )

        val candidate = draft.toTaskCandidate(CREATED_AT)

        assertEquals("Study for GATE", candidate.title.trim())
        assertEquals(Priority.CRITICAL, candidate.priority)
        assertEquals(Duration.ofHours(2), candidate.estimatedDuration)
        assertEquals(LocalDate.of(2026, 10, 16), candidate.requestedDate)
        assertEquals(Duration.ofMinutes(30), candidate.minimumSession)
        assertEquals(listOf("Splitting is allowed."), candidate.assumptions)
    }

    @Test
    fun `validation failures come back as the canonical sentences`() {
        val draft = TaskIntentDraft(title = "", estimatedDuration = Duration.ZERO, splittable = true)

        val problems = TaskIntentDraftValidator.validate(draft, CREATED_AT)

        assertEquals(TaskValidator.validate(draft.toTaskCandidate(CREATED_AT)), problems)
        assertTrue(problems.any { it.contains("title") })
        assertTrue(problems.any { it.contains("greater than zero") })
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("UTC")
        val CREATED_AT = ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZONE).toInstant()
    }
}

private typealias TimeRange = com.chrona.app.domain.model.TimeRange