package com.chrona.app.domain.parse

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.random.Random

/**
 * The parser's whole public behaviour, fixture by fixture (`PHASE_03_VOICE_AND_INTENT_PARSER.md`
 * §Tests).
 *
 * The table is the specification: each row is a sentence someone might actually say, and the
 * assertion is what Chrona is allowed to do with it. The refusal rows matter as much as the
 * capture rows — a parser that quietly invents a task is the failure mode this file exists to
 * prevent.
 */
class DeterministicTaskIntentParserTest {

    private val parser = DeterministicTaskIntentParser()

    @After
    fun restoreLocale() {
        Locale.setDefault(Locale.ROOT)
    }

    // ------------------------------------------------------------------ refusals

    @Test
    fun `an empty transcript is refused, not turned into a blank task`() {
        assertUnsupported("", ParseFailureReason.EMPTY_INPUT)
        assertUnsupported("   \t \n ", ParseFailureReason.EMPTY_INPUT)
    }

    @Test
    fun `an over-long transcript is refused instead of truncated`() {
        val long = "study for gate ".repeat(60)
        assertTrue(long.length > 500)
        assertUnsupported(long, ParseFailureReason.INPUT_TOO_LONG)
    }

    @Test
    fun `a question is not a capture`() {
        assertUnsupported("What should I do next?", ParseFailureReason.NOT_A_TASK_REQUEST)
        assertUnsupported("what time is it", ParseFailureReason.NOT_A_TASK_REQUEST)
    }

    @Test
    fun `a lifecycle command is refused and never becomes a new task`() {
        // The dangerous case: parsed as captures, the task list would fill up with notes
        // titled "I finished the assignment".
        assertUnsupported("I finished the assignment", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("finished the report", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("done with the grocery run", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("move my workout later", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("postpone the laundry", ParseFailureReason.UNSUPPORTED_COMMAND)
    }

    @Test
    fun `a relation command is refused because relation editing is not built yet`() {
        assertUnsupported("connect this project to my showcase", ParseFailureReason.UNSUPPORTED_COMMAND)
    }

    @Test
    fun `a transcript with no Latin letters is refused rather than guessed at`() {
        assertUnsupported("изучить главу", ParseFailureReason.UNSUPPORTED_LANGUAGE)
        assertUnsupported("日本語のタスク", ParseFailureReason.UNSUPPORTED_LANGUAGE)
    }

    @Test
    fun `an explicit negation is refused, but don't-forget is a capture`() {
        assertUnsupported("don't schedule the dentist tomorrow", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("never mind", ParseFailureReason.UNSUPPORTED_COMMAND)
        assertUnsupported("no need to buy milk", ParseFailureReason.UNSUPPORTED_COMMAND)

        assertEquals("Water the plants", parsed("don't forget to water the plants").title)
    }

    @Test
    fun `a malformed date is refused rather than guessed`() {
        assertUnsupported("call mum on 2026-13-45", ParseFailureReason.MALFORMED_DATE)
        assertUnsupported("submit the form on 32/13/2026", ParseFailureReason.MALFORMED_DATE)
    }

    @Test
    fun `a transcript with only timing words has no title to save`() {
        assertUnsupported("tomorrow at 3pm for two hours", ParseFailureReason.NO_TITLE_REMAINING)
    }

    @Test
    fun `a duration no task could hold is refused`() {
        assertUnsupported("study for gate for thirty hours", ParseFailureReason.IMPLAUSIBLE_DURATION)
    }

    // ------------------------------------------------------------------ captures

    @Test
    fun `the worked example from the phase prompt parses end to end`() {
        val draft = parsed("Study for GATE for two hours tomorrow")

        assertEquals("Study for GATE", draft.title)
        assertEquals(Duration.ofHours(2), draft.estimatedDuration)
        assertEquals(LocalDate.of(2026, 10, 16), draft.requestedDate)
        assertNull("a spoken day is not a deadline", draft.deadline)
        assertNull("the parser does not choose a slot", draft.preferredWindow)
        assertTrue(draft.assumptions.any { it.field == DraftField.REQUESTED_DATE })
    }

    @Test
    fun `a deadline is the end of the named day, not a time of day`() {
        val due = parsed("finish assignment by friday")
        assertEquals(ZonedDateTime.of(2026, 10, 16, 23, 59, 59, 999_999_999, ZONE), due.deadline)
        assertTrue(due.assumptions.any { it.text.contains("deadline is the latest acceptable time") })

        val strict = parsed("finish assignment before friday")
        assertEquals(ZonedDateTime.of(2026, 10, 16, 0, 0, 0, 0, ZONE), strict.deadline)
        assertTrue(strict.assumptions.any { it.text.contains("end of the day before") })
    }

    @Test
    fun `an absolute date is read in ISO, slashed and written forms`() {
        assertEquals(LocalDate.of(2026, 10, 20), parsed("buy milk on 2026-10-20").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 20), parsed("buy milk on 20/10/2026").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 20), parsed("buy milk on 20 october").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 20), parsed("buy milk on october 20").requestedDate)
    }

    @Test
    fun `relative dates are resolved against the reference date`() {
        assertEquals(LocalDate.of(2026, 10, 15), parsed("water the plants today").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 16), parsed("water the plants tomorrow").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 17), parsed("water the plants in two days").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 17), parsed("water the plants the day after tomorrow").requestedDate)
        assertEquals(LocalDate.of(2026, 10, 29), parsed("water the plants in two weeks").requestedDate)
    }

    @Test
    fun `a bare weekday means the next occurrence and the choice is stated`() {
        // The reference day is Thursday 15 October 2026.
        val friday = parsed("submit the form on friday")
        assertEquals(LocalDate.of(2026, 10, 16), friday.requestedDate)
        assertTrue(friday.assumptions.any { it.text == "Read the spoken day as 2026-10-16." })

        assertEquals(LocalDate.of(2026, 10, 16), parsed("submit the form on next friday").requestedDate)
        // "next monday" said on a Thursday is the Monday of the following week.
        assertEquals(LocalDate.of(2026, 10, 19), parsed("submit the form on next monday").requestedDate)
        // "this thursday" said on a Thursday means today.
        assertEquals(LocalDate.of(2026, 10, 15), parsed("submit the form on this thursday").requestedDate)
    }

    @Test
    fun `a bare clock number is never resolved to morning or afternoon`() {
        val draft = parsed("gym at 3")
        assertEquals("Gym at 3", draft.title)
        assertNull("an ambiguous clock number must not become a time", draft.preferredWindow)
        assertTrue(draft.assumptions.any { it.field == DraftField.TIME_OF_DAY })
    }

    @Test
    fun `an explicit time plus a duration becomes a soft window`() {
        val draft = parsed("gym at 3pm for 45 minutes")
        assertEquals("Gym", draft.title)
        assertEquals(Duration.ofMinutes(45), draft.estimatedDuration)
        assertEquals(LocalTime.of(15, 0), draft.preferredWindow?.start)
        assertEquals(LocalTime.of(15, 45), draft.preferredWindow?.end)
        assertNull("flexibility is only stated when the user states it", draft.flexibility)
        assertNull("a flexible task is never pinned", draft.fixedRange)
    }

    @Test
    fun `an explicit range is read as a fixed block`() {
        val draft = parsed("dentist appointment from 3pm to 4pm tomorrow")
        assertEquals("Dentist", draft.title)
        assertEquals(Flexibility.FIXED, draft.flexibility)
        assertEquals(LocalDate.of(2026, 10, 16), draft.requestedDate)
        assertEquals(LocalTime.of(15, 0), draft.fixedRange?.start?.toLocalTime())
        assertEquals(LocalTime.of(16, 0), draft.fixedRange?.end?.toLocalTime())
        assertTrue(draft.assumptions.any { it.text.contains("Chrona will not move it") })
    }

    @Test
    fun `part of day becomes a preference anchored to the user's day hours`() {
        val draft = parsed("study french in the morning", preferences = ParsePreferences(dayStart = LocalTime.of(10, 0)))
        assertEquals("Study french", draft.title)
        assertEquals(LocalTime.of(10, 0), draft.preferredWindow?.start)
        assertEquals(LocalTime.of(12, 0), draft.preferredWindow?.end)
    }

    @Test
    fun `tonight is today, in the evening`() {
        val draft = parsed("call the landlord tonight")
        assertEquals(LocalDate.of(2026, 10, 15), draft.requestedDate)
        assertEquals(LocalTime.of(18, 0), draft.preferredWindow?.start)
    }

    @Test
    fun `priority words are read explicitly`() {
        assertEquals(Priority.CRITICAL, parsed("urgent dentist visit tomorrow").priority)
        assertEquals(Priority.IMPORTANT, parsed("very important book the flight").priority)
        assertEquals(Priority.LOW, parsed("low priority water the plants").priority)
        assertNull("silence is not a priority claim", parsed("water the plants tomorrow").priority)
    }

    @Test
    fun `splitting is read as a capability with a minimum session`() {
        val draft = parsed("read chapter 4 in parts at least 30 minutes at a time")
        assertEquals(true, draft.splittable)
        assertEquals(Duration.ofMinutes(30), draft.minimumSession)
    }

    @Test
    fun `splitting without a minimum session keeps the minimum unset`() {
        val draft = parsed("write the report in parts")
        assertEquals(true, draft.splittable)
        assertNull(draft.minimumSession)
    }

    @Test
    fun `because becomes the detail`() {
        val draft = parsed("call the bank because the card was declined")
        assertEquals("Call the bank", draft.title)
        assertEquals("the card was declined", draft.detail)
    }

    @Test
    fun `an explicit dependency is only ever suggested`() {
        val draft = parsed("review the report after finishing the notes tomorrow")
        assertEquals("Review the report", draft.title)
        assertEquals(LocalDate.of(2026, 10, 16), draft.requestedDate)
        val relation = draft.proposedRelations.single()
        assertEquals("the notes", relation.targetText)
        assertEquals(RelationCreatedBy.SUGGESTED, relation.createdBy)
        assertTrue(draft.assumptions.any { it.field == DraftField.RELATION })
    }

    @Test
    fun `an after that names no dependency stays ordinary text`() {
        // "after lunch" is a time of day. Treating it as a prerequisite would invent a
        // constraint the user never agreed to.
        val draft = parsed("call the bank after lunch")
        assertEquals("Call the bank after lunch", draft.title)
        assertTrue(draft.proposedRelations.isEmpty())
    }

    // ------------------------------------------------------------------ clarification

    @Test
    fun `a vague timing marker asks one question instead of guessing`() {
        val prompt = clarify("call the dentist sometime")
        assertEquals(DraftField.REQUESTED_DATE, prompt.field)
        assertEquals(listOf("Today", "Tomorrow", "No date — keep it in the inbox"), prompt.options.map { it.label })
        assertNull("the questioned field starts empty", prompt.base.requestedDate)
        assertEquals(LocalDate.of(2026, 10, 15), prompt.options[0].patch.requestedDate)
        assertEquals(LocalDate.of(2026, 10, 16), prompt.options[1].patch.requestedDate)
    }

    @Test
    fun `a start time with no end asks how long the block is`() {
        val prompt = clarify("gym at 3pm")
        assertEquals(DraftField.DURATION, prompt.field)
        assertEquals(listOf("30 min", "1 h", "2 h"), prompt.options.map { it.label })
        assertNull(prompt.base.estimatedDuration)
        assertEquals(Duration.ofHours(2), prompt.options.last().patch.estimatedDuration)
        assertEquals(LocalTime.of(17, 0), prompt.options.last().patch.preferredWindow?.end)
    }

    @Test
    fun `two different values for one field ask which one is meant`() {
        val deadlines = clarify("hand in the report by friday and also by tuesday")
        assertEquals(DraftField.DEADLINE, deadlines.field)
        assertEquals(2, deadlines.options.size)
        assertNull(deadlines.base.deadline)

        val priorities = clarify("urgent low priority laundry")
        assertEquals(DraftField.PRIORITY, priorities.field)
        assertEquals(listOf(Priority.CRITICAL, Priority.LOW), priorities.options.map { it.patch.priority })

        val dates = clarify("gym tomorrow then monday")
        assertEquals(DraftField.REQUESTED_DATE, dates.field)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19)),
            dates.options.map { it.patch.requestedDate },
        )

        val durations = clarify("read for two hours and then for thirty minutes")
        assertEquals(DraftField.DURATION, durations.field)
        assertEquals(
            listOf(Duration.ofHours(2), Duration.ofMinutes(30)),
            durations.options.map { it.patch.estimatedDuration },
        )
    }

    @Test
    fun `at most one question is ever asked`() {
        listOf(
            "call the dentist sometime at 3pm by friday and by tuesday",
            "gym at 3pm sometime tomorrow then monday",
        ).forEach { text ->
            val result = understand(text)
            if (result is TaskUnderstandingResult.NeedsClarification) {
                assertTrue(
                    "one bounded question expected for \"$text\"",
                    result.prompt.options.size in 2..3,
                )
            }
        }
    }

    // ------------------------------------------------------------------ Unicode and locale

    @Test
    fun `accents, emoji and non-Latin words in a title are preserved`() {
        val draft = parsed("café with Zoë and Ravi tomorrow")
        assertEquals("Café with Zoë and Ravi", draft.title)
        assertEquals(LocalDate.of(2026, 10, 16), draft.requestedDate)

        val emoji = parsed("buy milk tomorrow \uD83E\uDED2")
        assertTrue(emoji.title.contains("\uD83E\uDED2"))
    }

    @Test
    fun `non-breaking spaces and curly punctuation are normalised, not hidden`() {
        val nonBreaking = parsed("buy milk tomorrow")
        assertEquals("Buy milk", nonBreaking.title)
        assertEquals(LocalDate.of(2026, 10, 16), nonBreaking.requestedDate)

        val quoted = parsed("remind me \u201Cbuy milk\u201D tomorrow")
        assertEquals("Buy milk", quoted.title)
        assertEquals(LocalDate.of(2026, 10, 16), quoted.requestedDate)
    }

    @Test
    fun `the device language does not change how a transcript is read`() {
        val expected = parsed("study for gate tomorrow")

        Locale.setDefault(Locale.forLanguageTag("tr")) // dotless i
        try {
            assertEquals(expected, parsed("study for gate tomorrow"))
            assertEquals(LocalDate.of(2026, 10, 20), parsed("buy milk on 20 october").requestedDate)
        } finally {
            Locale.setDefault(Locale.ROOT)
        }
    }

    // ------------------------------------------------------------------ determinism and contracts

    @Test
    fun `the same input always produces the same result`() {
        val text = "study for gate for two hours tomorrow before friday urgent"
        val first = understand(text)
        val second = runBlocking { DeterministicTaskIntentParser().understand(request(text)) }
        assertEquals(first, second)
        assertEquals(first, understand(text))
    }

    @Test
    fun `a different reference date changes only the resolved dates`() {
        val nextWeek = ZonedDateTime.of(2026, 10, 22, 9, 0, 0, 0, ZONE)
        val result = runBlocking { parser.understand(request("water the plants on monday", nextWeek)) }
        val draft = (result as TaskUnderstandingResult.Parsed).draft
        assertEquals(LocalDate.of(2026, 10, 26), draft.requestedDate)
    }

    @Test
    fun `every parsed draft satisfies the canonical task rules`() {
        listOf(
            "Study for GATE for two hours tomorrow",
            "finish assignment before friday",
            "dentist appointment from 3pm to 4pm tomorrow",
            "read chapter 4 in parts at least 30 minutes at a time",
            "gym at 3pm for 45 minutes",
            "café with Zoë and Ravi tomorrow",
            "write the report in parts because the deadline moved",
        ).forEach { text ->
            val draft = parsed(text)
            val problems = TaskIntentDraftValidator.validate(draft, CREATED_AT)
            assertTrue("\"$text\" produced $problems", problems.isEmpty())
        }
    }

    @Test
    fun `a draft never carries more assumptions than a task may store`() {
        val noisy = "urgent study for gate for two hours tomorrow morning at 3pm to 4pm " +
            "after finishing the notes read in parts"
        assertTrue(parsed(noisy).assumptions.size <= MAX_ASSUMPTIONS)
    }

    /**
     * Fuzz: no input may crash the parser, escape one of the three contract results, or produce
     * a draft the canonical rules would reject.
     */
    @Test
    fun `arbitrary input always lands in a contract result`() {
        val alphabet = listOf(
            "study", "gate", "tomorrow", "friday", "at", "3pm", "for", "two", "hours", "before",
            "by", "urgent", "sometime", "after", "finishing", "once", "done", "in", "parts",
            "2026-10-20", "20", "october", "half", "an", "night", "?", "!", "the", "to", "i",
            "don't", "こんにちは", "—", "3", "because", "split",
        )
        val random = Random(seed = 20261015)

        repeat(400) { attempt ->
            val words = (1..random.nextInt(1, 12)).joinToString(" ") {
                alphabet[random.nextInt(alphabet.size)]
            }
            val result = understand(words)
            when (result) {
                is TaskUnderstandingResult.Parsed -> {
                    assertTrue("\"$words\" produced an empty title", result.draft.title.isNotBlank())
                    val problems = TaskIntentDraftValidator.validate(result.draft, CREATED_AT)
                    assertTrue("\"$words\" produced an invalid draft: $problems", problems.isEmpty())
                    assertTrue(result.draft.confidence in 0f..1f)
                    assertTrue(result.draft.assumptions.size <= MAX_ASSUMPTIONS)
                }

                is TaskUnderstandingResult.NeedsClarification -> {
                    assertTrue(result.prompt.question.isNotBlank())
                    assertTrue(result.prompt.options.isNotEmpty())
                    // An option must offer a draft the rules accept, otherwise the user would be
                    // offered a choice that cannot be saved.
                    result.prompt.options.forEach { option ->
                        val problems = TaskIntentDraftValidator.validate(option.patch, CREATED_AT)
                        assertTrue("option \"${option.label}\" of attempt $attempt: $problems", problems.isEmpty())
                    }
                }

                is TaskUnderstandingResult.Unsupported -> assertNotNull(result.reason.message)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun request(
        text: String,
        reference: ZonedDateTime = REFERENCE,
        preferences: ParsePreferences = ParsePreferences(),
    ) = TaskUnderstandingRequest(
        text = text,
        referenceTime = reference,
        zoneId = ZONE,
        preferences = preferences,
    )

    private fun understand(
        text: String,
        reference: ZonedDateTime = REFERENCE,
        preferences: ParsePreferences = ParsePreferences(),
    ): TaskUnderstandingResult = runBlocking {
        DeterministicTaskIntentParser().understand(request(text, reference, preferences))
    }

    private fun parsed(text: String, preferences: ParsePreferences = ParsePreferences()): TaskIntentDraft {
        val result = understand(text, preferences = preferences)
        assertTrue("\"$text\" was not parsed: $result", result is TaskUnderstandingResult.Parsed)
        return (result as TaskUnderstandingResult.Parsed).draft
    }

    private fun clarify(text: String): ClarificationPrompt {
        val result = understand(text)
        assertTrue("\"$text\" did not ask a question: $result", result is TaskUnderstandingResult.NeedsClarification)
        return (result as TaskUnderstandingResult.NeedsClarification).prompt
    }

    private fun assertUnsupported(text: String, expected: ParseFailureReason) {
        val result = understand(text)
        assertTrue("\"$text\" should have been refused but was $result", result is TaskUnderstandingResult.Unsupported)
        assertSame(expected, (result as TaskUnderstandingResult.Unsupported).reason)
    }

    private companion object {
        /** The assumption cap the parser promises, mirrored so a change cannot pass silently. */
        const val MAX_ASSUMPTIONS = 10

        val ZONE: ZoneId = ZoneId.of("UTC")

        /** Thursday 15 October 2026, 09:00 UTC — the reference the whole table is built on. */
        val REFERENCE: ZonedDateTime = ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZONE)
        val CREATED_AT: Instant = REFERENCE.toInstant()
    }
}
