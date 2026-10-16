package com.chrona.app.domain.parse

import com.chrona.app.domain.model.TimeWindow
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bounded English vocabulary the deterministic parser understands.
 *
 * Everything here is deliberately small and explicit. Each matcher is total: it either
 * consumes a known phrase or consumes nothing, so the same text always walks the same path.
 *
 * Locale note: every formatter is [Locale.ROOT] and every comparison is against lower-cased
 * text. A device set to Turkish (dotless i) or French must not change how a transcript is
 * read, so the grammar never uses the default locale.
 */
internal object ParseGrammar {

    /** Longest transcript the parser will look at. Anything larger is refused, not truncated. */
    const val MAX_TRANSCRIPT_LENGTH: Int = 500

    /** Time-of-day windows, fixed by the bounded grammar. */
    val AFTERNOON_WINDOW: TimeWindow = TimeWindow(LocalTime.of(13, 0), LocalTime.of(17, 0))

    val NUMBER_WORDS: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    /** Cues that promise timing information the grammar cannot resolve. */
    val VAGUE_TIMING_MARKERS: Set<String> = setOf(
        "later", "soon", "someday", "sometime", "shortly", "whenever", "anytime",
    )

    /** Multi-word vague markers, checked before their single-word prefixes. */
    val VAGUE_TIMING_PHRASES: Set<String> = setOf(
        "next week", "this week", "next month", "in a bit", "later on", "some day", "some time",
    )

    /**
     * Words that only ever appear as an opener, so they are dropped from the title wherever
     * they sit.
     *
     * Deliberately small. Articles, "for" and words like "task" or "reminder" are *not* here:
     * they carry meaning inside a title ("call the bank", "study for GATE", "task the dog") and
     * are only trimmed from the edges by [TITLE_TRIM_WORDS].
     */
    val CAPTURE_FILLERS: Set<String> = setOf(
        "i", "need", "to", "want", "have", "gotta", "must", "should", "would", "like",
        "please", "remind", "me", "my",
    )

    /** Multi-word openers, consumed before single words. */
    val CAPTURE_FILLER_PHRASES: List<String> = listOf(
        "i need to", "i want to", "i have to", "i must", "i should", "i will",
        "remind me to", "don't forget to", "dont forget to", "remember to",
        "need to", "have to", "make sure to", "make sure i",
        "i've got to", "i have got to",
    )

    /** Words trimmed from the edges of the remaining title. */
    val TITLE_TRIM_WORDS: Set<String> = setOf(
        "and", "then", "to", "the", "a", "an", "of", "about", "on", "at", "in", "with",
        "for", "my", "i", "it", "is", "please", "just", "really", "some",
    )

    /** A phrase the parser read, plus the tokens it consumed. */
    data class Match<T>(val value: T, val span: Int)

    /**
     * Normalises what a recogniser actually emits.
     *
     * Speech engines return non-breaking spaces, curly quotes and en dashes. Left alone they
     * split words ("buy\u00A0milk") or hide a cue, so they are folded to their ASCII form
     * before anything is read. The user's own letters, including non-Latin script and accents,
     * are untouched.
     */
    fun normalize(text: String): String = text
        .replace(EXOTIC_SPACES, " ")
        .replace(SMART_SINGLE_QUOTES, "\'")
        .replace(SMART_DOUBLE_QUOTES, "\"")
        .replace(EN_DASHES, "-")

    private val EXOTIC_SPACES = Regex("[\u00A0\u202F\u2007\u2009\u200B]")
    private val SMART_SINGLE_QUOTES = Regex("[\u2018\u2019\u02BC]")
    private val SMART_DOUBLE_QUOTES = Regex("[\u201C\u201D\u201E\u201F]")
    private val EN_DASHES = Regex("[\u2013\u2014\u2212]")

    /** Stripped and lower-cased. Keeps the digit or word intact so "3pm." still reads. */
    fun bare(token: String): String = token
        .trim('.', ',', ';', ':', '!', '?', '"', '\'', '’', '(', ')')
        .lowercase(Locale.ROOT)

    /**
     * True when the text contains at least one Latin letter.
     *
     * The grammar is English, so a transcript with no Latin letters is refused rather than
     * turned into a task Chrona cannot interpret at all.
     */
    fun hasAsciiLetters(text: String): Boolean = text.any { it.code in 97..122 || it.code in 65..90 }

    /** "45" or "forty" -> 45. Null for anything that is not a positive quantity. */
    fun number(token: String): Int? {
        val bare = bare(token)
        if (bare.toIntOrNull() != null) return bare.toIntOrNull()!!.takeIf { it > 0 }
        if (bare == "couple" || bare == "few") return 2
        return NUMBER_WORDS[bare]?.takeIf { it > 0 }
    }

    private fun isDurationUnit(token: String): Boolean =
        token in setOf("min", "mins", "minute", "minutes", "h", "hr", "hrs", "hour", "hours")

    /**
     * A duration phrase at [index]: "for two hours", "for 45 minutes", "an hour",
     * "half an hour", "forty five minutes".
     *
     * "forty five" is the one two-token quantity in the grammar; anything else is a single
     * token, so "in three days" cannot be read as a duration.
     */
    fun durationAt(tokens: List<String>, index: Int): Match<Duration>? {
        if (index >= tokens.size) return null
        if (bare(tokens[index]) == "half") {
            var cursor = index + 1
            if (cursor < tokens.size && bare(tokens[cursor]) in setOf("a", "an")) cursor++
            val unit = tokens.getOrNull(cursor)?.let { bare(it) }
            if (unit == null || !isDurationUnit(unit)) return null
            val minutes = if (unit.startsWith("h")) 30L else 15L
            return Match(Duration.ofMinutes(minutes), cursor - index + 1)
        }

        val first = number(tokens[index]) ?: return null
        var span = 1
        var total = first
        val second = tokens.getOrNull(index + 1)?.let { number(it) }
        if (second != null && second in 1..9 && first % 10 == 0) {
            total = first + second
            span = 2
        }
        val unit = tokens.getOrNull(index + span)?.let { bare(it) }
        if (unit == null || !isDurationUnit(unit)) return null
        val minutes = if (unit.startsWith("h")) total.toLong() * 60 else total.toLong()
        val duration = Duration.ofMinutes(minutes)
        if (duration.isZero || duration.isNegative) return null
        return Match(duration, span + 1)
    }

    /** Outcome of reading a date phrase. */
    data class DateMatch(
        val date: LocalDate?,
        val span: Int,
        /** True when a concrete day was named. */
        val isExplicit: Boolean,
        /** True when a vague timing marker was found but no day could be read. */
        val isVague: Boolean = false,
    )

    private val ISO_DATE = Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$")
    private val SLASHED_DATE = Regex("^\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}$")
    private val DAY_MONTH = Regex("^(\\d{1,2})\\s+([a-z]+)\\.?$")
    private val MONTH_DAY = Regex("^([a-z]+)\\.?\\s+(\\d{1,2})(st|nd|rd|th)?$")

    /**
     * True when a single token looks like a date the user tried to give, valid or not.
     *
     * Only the one-token shapes count: a two-word "20 october" is read by [dateAt] instead,
     * and a token that merely starts with digits ("5") is not a date attempt.
     */
    fun looksLikeDate(token: String): Boolean {
        val bare = bare(token)
        return ISO_DATE.matches(bare) || SLASHED_DATE.matches(bare)
    }

    /**
     * Reads a date phrase at [index], relative to [reference].
     *
     * A bare weekday means the next occurrence that has not already passed. The caller records
     * the resolved date as an assumption, because "on Friday" said on Friday evening
     * genuinely has two readings and Chrona must say which one it chose.
     */
    fun dateAt(tokens: List<String>, index: Int, reference: LocalDate): DateMatch? {
        if (index >= tokens.size) return null
        val token = bare(tokens[index])

        val vaguePhrase = phraseSpan(tokens, index, VAGUE_TIMING_PHRASES)
        if (vaguePhrase > 0) {
            return DateMatch(null, vaguePhrase, isExplicit = false, isVague = true)
        }
        if (token in VAGUE_TIMING_MARKERS) {
            return DateMatch(null, 1, isExplicit = false, isVague = true)
        }

        when (token) {
            "today", "tonight" -> return DateMatch(reference, 1, isExplicit = true)
            "tomorrow" -> return DateMatch(reference.plusDays(1), 1, isExplicit = true)

            "day" -> {
                if (index + 2 < tokens.size && bare(tokens[index + 1]) == "after" &&
                    bare(tokens[index + 2]) == "tomorrow"
                ) {
                    return DateMatch(reference.plusDays(2), 3, isExplicit = true)
                }
            }

            "in" -> relativeOffsetAt(tokens, index + 1, reference)?.let { return it }

            "next", "this", "coming" -> {
                val weekday = tokens.getOrNull(index + 1)?.let { WEEKDAYS[bare(it)] }
                if (weekday != null) {
                    return DateMatch(
                        date = nextOccurrence(reference, weekday, strictAfterToday = token != "this"),
                        span = 2,
                        isExplicit = true,
                    )
                }
            }
        }

        WEEKDAYS[token]?.let { day ->
            return DateMatch(nextOccurrence(reference, day, strictAfterToday = false), 1, isExplicit = true)
        }

        parseAbsoluteDate(token, reference)?.let {
            return DateMatch(it, 1, isExplicit = true)
        }

        // "20 october" and "october 20" are two spoken words, so they are read as a pair.
        // Only a real month name matches, which keeps this from swallowing ordinary titles.
        val next = tokens.getOrNull(index + 1)?.let { bare(it) }
        if (next != null) {
            parseAbsoluteDate("$token $next", reference)?.let {
                return DateMatch(it, 2, isExplicit = true)
            }
        }
        return null
    }

    

    /** "in three days", "in two weeks", "in a month". */
    private fun relativeOffsetAt(tokens: List<String>, index: Int, reference: LocalDate): DateMatch? {
        if (index >= tokens.size) return null
        val first = bare(tokens[index])
        if (first == "a" || first == "an") {
            val unit = tokens.getOrNull(index + 1)?.let { bare(it) }
            val date = when (unit) {
                "day" -> reference.plusDays(1)
                "week" -> reference.plusWeeks(1)
                "month" -> reference.plusMonths(1)
                else -> null
            } ?: return null
            return DateMatch(date, 3, isExplicit = true)
        }
        val amount = number(first) ?: return null
        val unit = tokens.getOrNull(index + 1)?.let { bare(it) }
        val date = when (unit) {
            "day", "days" -> reference.plusDays(amount.toLong())
            "week", "weeks" -> reference.plusWeeks(amount.toLong())
            "month", "months" -> reference.plusMonths(amount.toLong())
            else -> null
        } ?: return null
        return DateMatch(date, 3, isExplicit = true)
    }

    /**
     * Absolute dates in ISO, D/M/Y, "20 october" and "october 20" forms.
     *
     * A day and month without a year uses [reference]'s year; the caller records that as an
     * assumption. Returns null when the token is not a readable date, which is how malformed
     * input ("2026-13-45") is detected rather than guessed at.
     */
    fun parseAbsoluteDate(token: String, reference: LocalDate): LocalDate? {
        val bare = bare(token)
        ISO_DATE.matchEntire(bare)?.let { return parseIso(bare) }
        SLASHED_DATE.matchEntire(bare)?.let { return parseSlashed(bare) }
        DAY_MONTH.matchEntire(bare)?.let { match ->
            val month = MONTHS[match.groupValues[2]] ?: return null
            return build(reference.year, month, match.groupValues[1])
        }
        MONTH_DAY.matchEntire(bare)?.let { match ->
            val month = MONTHS[match.groupValues[1]] ?: return null
            return build(reference.year, month, match.groupValues[2])
        }
        return null
    }

    private fun parseIso(value: String): LocalDate? = runCatching {
        DateTimeFormatter.ISO_LOCAL_DATE.parse(value, LocalDate::from)
    }.getOrNull()

    private fun parseSlashed(value: String): LocalDate? {
        val parts = value.split('/', '.', '-')
        if (parts.size != 3) return null
        val first = parts[0].toIntOrNull() ?: return null
        val second = parts[1].toIntOrNull() ?: return null
        val rawYear = parts[2].toIntOrNull() ?: return null
        val year = if (rawYear < 100) 2000 + rawYear else rawYear
        // D/M/Y is the only slashed order in the grammar; M/D/Y would silently swap days for
        // anyone from the US, so the parser refuses rather than guessing a locale.
        return build(year, second, first.toString())
    }

    private fun build(year: Int, month: Int, day: String): LocalDate? {
        val dayOfMonth = day.toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, dayOfMonth) }.getOrNull()
    }

    /**
     * Next [dayOfWeek] relative to [reference].
     *
     * With [strictAfterToday] the reference day is skipped, matching "next Monday" said on a
     * Monday. Without it the reference day wins, matching "on Monday" said on a Monday.
     */
    fun nextOccurrence(reference: LocalDate, dayOfWeek: DayOfWeek, strictAfterToday: Boolean): LocalDate {
        val delta = (dayOfWeek.value - reference.dayOfWeek.value + 7) % 7
        val days = if (delta == 0 && strictAfterToday) 7 else delta
        return reference.plusDays(days.toLong())
    }

    /** "3pm", "3 pm", "15:30", "noon". A bare "3" is deliberately not accepted. */
    fun timeAt(tokens: List<String>, index: Int): Match<LocalTime>? {
        if (index >= tokens.size) return null
        val token = bare(tokens[index])
        when (token) {
            "noon", "midday" -> return Match(LocalTime.NOON, 1)
            "midnight" -> return Match(LocalTime.MIDNIGHT, 1)
        }
        val next = tokens.getOrNull(index + 1)?.let { bare(it) }
        if (next == "am" || next == "pm") {
            clockTime(token, isPm = next == "pm")?.let { return Match(it, 2) }
        }
        clockTime(token, isPm = false)?.let { return Match(it, 1) }
        return null
    }

    /**
     * True for "3" — a clock number with no am/pm and no colon.
     *
     * The grammar refuses it rather than assuming 3pm, and the caller keeps the words in the
     * title with an assumption telling the user how to be explicit.
     */
    fun isAmbiguousClockNumber(token: String): Boolean {
        val bare = bare(token)
        if (bare.length !in 1..2 || !bare.all { it.isDigit() }) return false
        return bare.toInt() in 1..12
    }

    private fun clockTime(raw: String, isPm: Boolean): LocalTime? {
        val meridiem = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$").matchEntire(raw)
        if (meridiem != null) {
            val hour = meridiem.groupValues[1].toIntOrNull() ?: return null
            val minute = meridiem.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
            return assemble(hour, minute, isPm = meridiem.groupValues[3] == "pm")
        }
        val twentyFour = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(raw)
        if (twentyFour != null) {
            val hour = twentyFour.groupValues[1].toIntOrNull() ?: return null
            val minute = twentyFour.groupValues[2].toIntOrNull() ?: return null
            return assemble(hour, minute, isPm = false)
        }
        return null
    }

    private fun assemble(hour: Int, minute: Int, isPm: Boolean): LocalTime? {
        if (minute !in 0..59) return null
        val adjusted = when {
            !isPm -> hour
            hour < 12 -> hour + 12
            else -> hour
        }
        if (adjusted !in 0..23) return null
        return LocalTime.of(adjusted, minute)
    }

    /**
     * "morning", "afternoon", "evening".
     *
     * The morning starts with the user's day and the evening ends with it; both edges are
     * clamped so an unusual preference cannot produce an empty or absurd window.
     */
    fun partOfDayWindow(token: String, dayStart: LocalTime, dayEnd: LocalTime): TimeWindow? =
        when (bare(token)) {
            "morning" -> TimeWindow(
                clampTime(dayStart, LocalTime.of(6, 0), LocalTime.of(11, 0)),
                LocalTime.of(12, 0),
            )

            "afternoon" -> AFTERNOON_WINDOW

            "evening" -> TimeWindow(
                LocalTime.of(18, 0),
                clampTime(dayEnd, LocalTime.of(18, 0), LocalTime.of(23, 0)),
            )

            else -> null
        }

    private fun clampTime(value: LocalTime, min: LocalTime, max: LocalTime): LocalTime =
        when {
            value < min -> min
            value > max -> max
            else -> value
        }

    /** Deadline wording. [strictlyBefore] turns "before friday" into Thursday's end. */
    enum class DeadlineCue(val strictlyBefore: Boolean) {
        DUE(strictlyBefore = false),
        BY(strictlyBefore = false),
        BEFORE(strictlyBefore = true),
        NO_LATER_THAN(strictlyBefore = false),
    }

    fun deadlineCueAt(tokens: List<String>, index: Int): DeadlineCue? =
        when (bare(tokens[index])) {
            "due" -> DeadlineCue.DUE
            "by" -> DeadlineCue.BY
            "before" -> DeadlineCue.BEFORE
            "deadline" -> DeadlineCue.NO_LATER_THAN
            "latest" -> DeadlineCue.NO_LATER_THAN
            else -> null
        }

    /** Two-token deadline cue "no later than". */
    fun deadlinePhraseSpan(tokens: List<String>, index: Int): Int =
        if (bare(tokens[index]) == "no" && tokens.getOrNull(index + 1)?.let { bare(it) } == "later") 2 else 1

    /** Utterance openings that are commands about existing tasks, not new captures. */
    private val COMPLETION_OPENERS: List<List<String>> = listOf(
        listOf("i", "finished"),
        listOf("i", "did"),
        listOf("i", "completed"),
        listOf("i've", "finished"),
        listOf("ive", "finished"),
        listOf("finished"),
        listOf("done", "with"),
        listOf("i've", "done"),
    )

    private val RESCHEDULE_OPENERS: List<String> =
        listOf("move", "reschedule", "postpone", "push", "replan", "shift", "delay")

    private val RELATION_OPENERS: List<String> = listOf("connect", "link", "relate", "attach")

    private val CANCEL_OPENERS: List<String> = listOf("cancel", "delete", "remove")

    private val QUESTION_OPENERS: Set<String> = setOf(
        "what", "who", "when", "where", "why", "how", "which", "should", "could", "is", "are", "do",
    )

    /**
     * True when the utterance is not a task capture at all.
     *
     * Refusing these is the behaviour that matters: "I finished the assignment" must not become
     * a new task whose title is that sentence, and a question is not a capture. Each refusal
     * routes the user to the action they can still take by hand.
     */
    fun nonCaptureReason(tokens: List<String>): NonCapture? {
        if (tokens.isEmpty()) return null
        val head = tokens.take(3).map { bare(it) }
        if (tokens.any { it.contains('?') }) return NonCapture.QUESTION
        if (head.first() in QUESTION_OPENERS) return NonCapture.QUESTION
        if (COMPLETION_OPENERS.any { head.size >= it.size && head.take(it.size) == it }) {
            return NonCapture.LIFECYCLE_COMMAND
        }
        if (head.first() in RESCHEDULE_OPENERS) return NonCapture.LIFECYCLE_COMMAND
        if (head.first() in RELATION_OPENERS) return NonCapture.RELATION_COMMAND
        if (head.first() in CANCEL_OPENERS) return NonCapture.LIFECYCLE_COMMAND
        return null
    }

    enum class NonCapture { QUESTION, LIFECYCLE_COMMAND, RELATION_COMMAND }

    /** Explicit negation, e.g. "don't schedule", "never mind", "no need to". */
    fun isNegation(tokens: List<String>, index: Int): Boolean {
        val token = bare(tokens[index])
        val next = tokens.getOrNull(index + 1)?.let { bare(it) }
        return when (token) {
            "no" -> next == "need"
            "never" -> true
            "don't", "dont" -> next != "forget" && next != "worry"
            "do" -> next == "not"
            else -> false
        }
    }

    /** Length of the longest [phrases] entry that starts at [index]; 0 when none does. */
    fun phraseSpan(tokens: List<String>, index: Int, phrases: Set<String>): Int =
        phrase(tokens, index, phrases).let { if (it.isEmpty()) 0 else it.split(' ').size }

    /** The longest phrase from [phrases] that starts at [index], or an empty string. */
    fun phrase(tokens: List<String>, index: Int, phrases: Set<String>): String =
        phrases
            .filter { startsWith(tokens, index, it) }
            .maxByOrNull { it.split(' ').size }
            ?: ""

    private fun startsWith(tokens: List<String>, index: Int, phrase: String): Boolean {
        val words = phrase.split(' ')
        if (index + words.size > tokens.size) return false
        return words.indices.all { offset -> bare(tokens[index + offset]) == words[offset] }
    }
}