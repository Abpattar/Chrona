package com.chrona.app.domain.parse

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.parse.ParseGrammar.DeadlineCue
import com.chrona.app.domain.task.MAX_ESTIMATED_DURATION
import com.chrona.app.domain.parse.ParseGrammar.Match
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one parser Chrona ships: a deterministic, offline, English grammar.
 *
 * The guarantees required by `SHARED_CONTRACT.md` §"Task intent parsing contract" and
 * MASTER_REQUIREMENTS §3 are structural, not aspirational:
 *
 * - **Deterministic.** The same text, reference time, zone and preferences always produce the
 *   same result. Nothing here reads a clock, a locale, a database or a random source.
 * - **Interpreting, not scheduling.** It produces a draft; it never picks a slot, never writes
 *   anything, and never turns vague language into a confirmed dependency.
 * - **No fabricated facts.** A phrase the grammar cannot read either stays in the title with an
 *   assumption, or is refused with a reason. A bare "3" is never silently 15:00.
 * - **Voice-optional.** Typed entry never goes through this class.
 */
class DeterministicTaskIntentParser(
    private val maxAssumptions: Int = MAX_ASSUMPTIONS,
) : TaskUnderstandingProvider {

    override suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult {
        val text = request.text
        if (text.isBlank()) return TaskUnderstandingResult.Unsupported(ParseFailureReason.EMPTY_INPUT)
        if (text.length > ParseGrammar.MAX_TRANSCRIPT_LENGTH) {
            return TaskUnderstandingResult.Unsupported(ParseFailureReason.INPUT_TOO_LONG)
        }
        if (!ParseGrammar.hasAsciiLetters(text)) {
            return TaskUnderstandingResult.Unsupported(ParseFailureReason.UNSUPPORTED_LANGUAGE)
        }

        val tokens = ParseGrammar.normalize(text).trim().split(WHITESPACE)
        when (ParseGrammar.nonCaptureReason(tokens)) {
            ParseGrammar.NonCapture.QUESTION ->
                return TaskUnderstandingResult.Unsupported(ParseFailureReason.NOT_A_TASK_REQUEST)

            ParseGrammar.NonCapture.LIFECYCLE_COMMAND,
            ParseGrammar.NonCapture.RELATION_COMMAND,
            -> return TaskUnderstandingResult.Unsupported(ParseFailureReason.UNSUPPORTED_COMMAND)

            null -> Unit
        }

        val scan = Scan(request)
        scan.read(tokens)
        if (scan.refusedAsCommand) return TaskUnderstandingResult.Unsupported(ParseFailureReason.UNSUPPORTED_COMMAND)
        if (scan.malformedDate) return TaskUnderstandingResult.Unsupported(ParseFailureReason.MALFORMED_DATE)

        // A duration the domain would reject must never reach review as if it were an
        // interpretation the user could simply confirm.
        scan.duration?.let { duration ->
            if (duration > MAX_ESTIMATED_DURATION) {
                return TaskUnderstandingResult.Unsupported(ParseFailureReason.IMPLAUSIBLE_DURATION)
            }
        }

        val title = buildTitle(scan.titleTokens)
        if (title.isEmpty()) return TaskUnderstandingResult.Unsupported(ParseFailureReason.NO_TITLE_REMAINING)

        clarificationFor(scan, title)?.let { return TaskUnderstandingResult.NeedsClarification(it) }
        // One utterance, one task: this grammar reads a single clause and never invents a second
        // task out of it. Task decomposition is not implemented here (RISKS_AND_OPEN_ITEMS.md), so
        // the list carries exactly one draft and the caller still has to handle more than one.
        return TaskUnderstandingResult.Parsed(listOf(scan.buildDraft(title)))
    }

    /**
     * One left-to-right pass over the transcript.
     *
     * Each value the pass finds keeps an alternative when a second, different value turns up.
     * Two values for one field is the only situation where the parser cannot choose for the
     * user, and it asks instead.
     */
    private inner class Scan(private val request: TaskUnderstandingRequest) {

        val titleTokens = mutableListOf<String>()
        val assumptions = mutableListOf<ParseAssumption>()
        val relations = mutableListOf<RelationDraft>()

        var refusedAsCommand = false
        var malformedDate = false
        var detail: String? = null

        var duration: Duration? = null
        var durationAlternative: Duration? = null

        var requestedDate: LocalDate? = null
        var dateAlternative: LocalDate? = null
        var dateIsVague = false

        var deadline: ZonedDateTime? = null
        var deadlineAlternative: ZonedDateTime? = null

        var priority: Priority? = null
        var priorityAlternative: Priority? = null

        var fixedRange: TimeRange? = null
        var fixedAlternative: TimeRange? = null
        var heardFixedWord = false

        /** Part-of-day window, e.g. "in the afternoon". A preference, never a deadline. */
        var partOfDay: TimeWindow? = null

        /** A bare "at 3pm". With a duration it becomes a window; without one it stays a question. */
        var heardTimeStart: LocalTime? = null
        var preferredWindow: TimeWindow? = null

        var flexibility: Flexibility? = null
        var splittable = false
        var minimumSession: Duration? = null
        var ambiguousClock = false

        val referenceDate: LocalDate get() = request.referenceTime.toLocalDate()

        fun read(tokens: List<String>) {
            var index = 0
            while (index < tokens.size && !refusedAsCommand) {
                index = step(tokens, index)
            }
            resolveWindows()
        }

        /** Consumes one position and returns the next one. */
        @Suppress("CyclomaticComplexMethod")
        private fun step(tokens: List<String>, index: Int): Int {
            val fillerSpan = ParseGrammar.phraseSpan(tokens, index, CAPTURE_FILLER_PHRASES)
            if (fillerSpan > 0) return index + fillerSpan
            if (ParseGrammar.bare(tokens[index]) in CAPTURE_FILLERS) return index + 1
            if (ParseGrammar.isNegation(tokens, index)) {
                refusedAsCommand = true
                return index
            }

            if (ParseGrammar.deadlinePhraseSpan(tokens, index) > 1) {
                return readDeadline(tokens, index + 2, DeadlineCue.NO_LATER_THAN)
            }
            ParseGrammar.deadlineCueAt(tokens, index)?.let { cue ->
                return readDeadline(tokens, index + 1, cue)
            }

            readFixedRange(tokens, index)?.let { (range, span) ->
                val previous = fixedRange
                if (previous != null && previous != range) fixedAlternative = previous
                fixedRange = range
                heardFixedWord = true
                assumptions.add(fixedAssumption(range))
                return index + span
            }

            readTime(tokens, index)?.let { span -> return index + span }

            ParseGrammar.partOfDayWindow(
                token = tokens[index],
                dayStart = request.preferences.dayStart,
                dayEnd = request.preferences.dayEnd,
            )?.let { window ->
                partOfDay = window
                assumptions.add(windowAssumption(window))
                return index + 1
            }

            ParseGrammar.dateAt(tokens, index, referenceDate)?.let { match ->
                match.date?.let { recordDate(it) }
                if (match.date == null && match.isVague) dateIsVague = true
                if (ParseGrammar.bare(tokens[index]) == "tonight" && partOfDay == null) {
                    partOfDay = eveningWindow()
                }
                return index + match.span
            }

            if (ParseGrammar.looksLikeDate(tokens[index])) {
                // A token shaped like a date that is not one. Refuse instead of guessing, so
                // "2026-13-45" never becomes a task that silently claims a date.
                malformedDate = true
            }

            readDuration(tokens, index)?.let { span -> return index + span }

            readPriority(tokens, index)?.let { match ->
                val previous = priority
                if (previous != null && previous != match.value) priorityAlternative = previous
                priority = match.value
                assumptions.add(
                    ParseAssumption(DraftField.PRIORITY, "Priority read as ${match.value.label()}."),
                )
                return index + match.span
            }

            readSplit(tokens, index)?.let { span -> return index + span }

            readRelation(tokens, index)?.let { (targetText, span) ->
                relations.add(
                    RelationDraft(
                        type = RelationType.DEPENDS_ON,
                        targetText = targetText,
                        explanation = "Suggested from the way this was said. It does nothing until " +
                            "you confirm it in the relation editor.",
                    ),
                )
                assumptions.add(
                    ParseAssumption(
                        DraftField.RELATION,
                        "Suggested dependency on “$targetText”. It is not a constraint until you " +
                            "confirm it.",
                    ),
                )
                return index + span
            }

            if (ParseGrammar.bare(tokens[index]) == "because") {
                detail = tokens.drop(index + 1).joinToString(" ").ifBlank { null }
                return tokens.size
            }

            if (ParseGrammar.isAmbiguousClockNumber(tokens[index])) {
                ambiguousClock = true
                assumptions.add(
                    ParseAssumption(
                        DraftField.TIME_OF_DAY,
                        "“${tokens[index]}” was kept as text because a bare clock number is " +
                            "ambiguous. Say “3 pm” or “15:00” to pin it.",
                    ),
                )
            }

            val word = ParseGrammar.bare(tokens[index])
            if (word in FIXED_WORDS) {
                heardFixedWord = true
                return index + 1
            }
            if (word in FLEXIBLE_WORDS) {
                flexibility = Flexibility.FLEXIBLE
                assumptions.add(
                    ParseAssumption(
                        DraftField.FLEXIBILITY,
                        "Read as flexible, so Chrona chooses a time that fits the day.",
                    ),
                )
                return index + 1
            }

            titleTokens.add(tokens[index])
            return index + 1
        }

        private fun readDeadline(tokens: List<String>, index: Int, cue: DeadlineCue): Int {
            val match = ParseGrammar.dateAt(tokens, index, referenceDate)
            if (match?.date == null) return index + 1
            val zone = request.zoneId
            val date = match.date
            val instant = if (cue.strictlyBefore) date.atStartOfDay(zone) else endOfDay(date, zone)
            val previous = deadline
            if (previous != null && previous != instant) deadlineAlternative = previous
            deadline = instant
            assumptions.add(
                ParseAssumption(
                    DraftField.DEADLINE,
                    if (cue.strictlyBefore) {
                        "Read “before ${ParseGrammar.bare(tokens[index])}” as ${DATE.format(date)}, " +
                            "so it must be finished by the end of the day before."
                    } else {
                        "Due date set to the end of ${DATE.format(date)}. A deadline is the latest " +
                            "acceptable time, not a plan."
                    },
                ),
            )
            return index + match.span
        }

        private fun readFixedRange(tokens: List<String>, index: Int): Pair<TimeRange, Int>? {
            // "from 3 to 5", "between 3 and 5" and "3 to 5" all name the same thing, so the
            // opening word is optional and the range is only accepted when a separator is there.
            var cursor = index
            if (ParseGrammar.bare(tokens[cursor]) in RANGE_OPENERS) cursor++
            val start = ParseGrammar.timeAt(tokens, cursor) ?: return null
            cursor += start.span
            val afterStart = tokens.getOrNull(cursor)?.let { ParseGrammar.bare(it) }
            if (afterStart !in RANGE_SEPARATORS) return null
            val end = ParseGrammar.timeAt(tokens, cursor + 1) ?: return null
            if (end.value <= start.value) return null

            val date = requestedDate ?: referenceDate
            if (requestedDate == null) {
                assumptions.add(
                    ParseAssumption(
                        DraftField.FLEXIBILITY,
                        "No day was named, so the fixed block was read as ${DATE.format(date)}.",
                    ),
                )
            }
            val range = TimeRange(
                start = ZonedDateTime.of(date, start.value, request.zoneId),
                end = ZonedDateTime.of(date, end.value, request.zoneId),
            )
            return range to (cursor + 1 + end.span - index)
        }

        private fun readTime(tokens: List<String>, index: Int): Int? {
            if (ParseGrammar.bare(tokens[index]) in RANGE_OPENERS) return null
            val prefixed = ParseGrammar.bare(tokens[index]) == "at"
            val timeIndex = if (prefixed) index + 1 else index
            val time = ParseGrammar.timeAt(tokens, timeIndex) ?: return null
            val next = tokens.getOrNull(timeIndex + time.span)?.let { ParseGrammar.bare(it) }
            if (next in RANGE_SEPARATORS) return null // belongs to a fixed range, handled above
            heardTimeStart = time.value
            return timeIndex + time.span - index
        }

        private fun readDuration(tokens: List<String>, index: Int): Int? {
            val match = ParseGrammar.durationAt(tokens, index) ?: return null
            val previous = duration
            if (previous != null && previous != match.value) durationAlternative = previous
            duration = match.value
            return match.span
        }

        private fun readPriority(tokens: List<String>, index: Int): Match<Priority>? {
            val word = ParseGrammar.bare(tokens[index])
            val next = tokens.getOrNull(index + 1)?.let { ParseGrammar.bare(it) }
            return when (word) {
                "urgent", "urgently", "asap", "critical", "crucial", "immediately" ->
                    Match(Priority.CRITICAL, 1)

                "important" -> Match(Priority.IMPORTANT, 1)
                "high", "top", "normal", "medium" ->
                    if (next == "priority") Match(Priority.IMPORTANT, 2) else null

                "low", "minor" -> if (next == "priority") Match(Priority.LOW, 2) else Match(Priority.LOW, 1)
                "very", "really" -> when (next) {
                    "important" -> Match(Priority.IMPORTANT, 2)
                    "urgent" -> Match(Priority.CRITICAL, 2)
                    else -> null
                }

                else -> null
            }
        }

        private fun readSplit(tokens: List<String>, index: Int): Int? {
            val minimumSpan = ParseGrammar.phraseSpan(tokens, index, MINIMUM_SESSION_PHRASES)
            if (minimumSpan > 0) {
                val match = ParseGrammar.durationAt(tokens, index + minimumSpan) ?: return null
                val trailing = ParseGrammar.phraseSpan(tokens, index + minimumSpan + match.span, REPEAT_PHRASES)
                splittable = true
                minimumSession = match.value
                assumptions.add(
                    ParseAssumption(
                        DraftField.DURATION,
                        "Splitting is allowed, in sessions of at least ${formatDuration(match.value)}.",
                    ),
                )
                return minimumSpan + match.span + trailing
            }
            val splitSpan = ParseGrammar.phraseSpan(tokens, index, SPLIT_PHRASES)
            if (splitSpan > 0) {
                splittable = true
                assumptions.add(
                    ParseAssumption(
                        DraftField.DURATION,
                        "Splitting is allowed. No minimum session was mentioned.",
                    ),
                )
                return splitSpan
            }
            return null
        }

        /**
         * Reads an explicit dependency cue: "after finishing the notes", "once the draft is
         * done", "depends on the data".
         *
         * Only unambiguous shapes are accepted. "after lunch" is a time, not a dependency, so a
         * bare "after" is left as ordinary text.
         */
        private fun readRelation(tokens: List<String>, index: Int): Pair<String, Int>? {
            val cue = ParseGrammar.bare(tokens[index])
            val next = tokens.getOrNull(index + 1)?.let { ParseGrammar.bare(it) }
            val start = when (cue) {
                "depends" -> if (next == "on") index + 2 else index + 1
                "waiting" -> if (next in setOf("on", "for")) index + 2 else return null
                "once" -> index + 1
                "after" -> if (next in RELATION_VERBS) index + 2 else return null
                else -> return null
            }
            if (start >= tokens.size) return null

            var end = start
            var sawTerminator = false
            while (end < tokens.size) {
                if (isRelationTerminator(tokens, end)) {
                    end += 2
                    sawTerminator = true
                    break
                }
                end++
            }
            if (!sawTerminator) end = tokens.size
            while (end > start && isCueToken(tokens[end - 1], referenceDate)) end--
            if (end <= start) return null

            val target = tokens.subList(start, end)
                .dropWhile { ParseGrammar.bare(it) in CAPTURE_FILLERS }
                .dropLastWhile { ParseGrammar.bare(it) in CAPTURE_FILLERS }
                .joinToString(" ")
                .trim()
            if (target.isEmpty()) return null
            return target to (end - index)
        }

        private fun isRelationTerminator(tokens: List<String>, index: Int): Boolean {
            val word = ParseGrammar.bare(tokens[index])
            if (word != "is" && word != "are") return false
            val next = tokens.getOrNull(index + 1)?.let { ParseGrammar.bare(it) }
            return next in RELATION_TERMINATORS
        }

        /** Turns the collected cues into the windows the domain model can store. */
        private fun resolveWindows() {
            if (heardFixedWord && fixedRange != null) flexibility = Flexibility.FIXED
            val start = heardTimeStart
            val length = duration
            preferredWindow = when {
                fixedRange != null -> null
                start == null -> partOfDay
                length == null -> null // unresolved: becomes one clarification, never a guess
                else -> windowFor(start, length)
            }
            if (start != null && length != null && windowFor(start, length) == null) {
                assumptions.add(
                    ParseAssumption(
                        DraftField.TIME_OF_DAY,
                        "That start time plus the duration runs past midnight, so no preferred " +
                            "time was set.",
                    ),
                )
            }
        }

        private fun windowFor(start: LocalTime, length: Duration): TimeWindow? {
            val end = start.plusMinutes(length.toMinutes())
            if (end <= start) return null
            return TimeWindow(start, end)
        }

        private fun eveningWindow(): TimeWindow = requireNotNull(
            ParseGrammar.partOfDayWindow("evening", request.preferences.dayStart, request.preferences.dayEnd),
        )

        private fun fixedAssumption(range: TimeRange) = ParseAssumption(
            DraftField.FLEXIBILITY,
            "Fixed block ${DATE.format(range.start.toLocalDate())} " +
                "${formatTime(range.start.toLocalTime())}-${formatTime(range.end.toLocalTime())}. " +
                "Chrona will not move it.",
        )

        private fun windowAssumption(window: TimeWindow) = ParseAssumption(
            DraftField.TIME_OF_DAY,
            "Preferred time ${formatTime(window.start)}-${formatTime(window.end)} is a soft " +
                "preference, not a deadline.",
        )

        private fun recordDate(date: LocalDate) {
            val previous = requestedDate
            if (previous != null && previous != date) dateAlternative = previous
            requestedDate = date
            assumptions.add(
                ParseAssumption(DraftField.REQUESTED_DATE, "Read the spoken day as ${DATE.format(date)}."),
            )
        }

        fun buildDraft(title: String): TaskIntentDraft {
            val all = assumptions.toMutableList()
            if (duration == null) {
                all.add(
                    ParseAssumption(DraftField.DURATION, "No duration was heard. Add one before Chrona plans it."),
                )
            }
            if (requestedDate == null && deadline == null) {
                all.add(
                    ParseAssumption(
                        DraftField.REQUESTED_DATE,
                        "No date was heard. The task stays in the inbox until it is scheduled.",
                    ),
                )
            }
            if (priority == null) {
                all.add(
                    ParseAssumption(
                        DraftField.PRIORITY,
                        "No priority was said, so Chrona will treat it as Important.",
                    ),
                )
            }
            if (ambiguousClock) {
                all.add(
                    ParseAssumption(
                        DraftField.TIME_OF_DAY,
                        "A bare clock number stayed in the title because Chrona will not guess " +
                            "morning or afternoon.",
                    ),
                )
            }

            return TaskIntentDraft(
                title = title,
                detail = detail,
                requestedDate = requestedDate,
                deadline = deadline,
                estimatedDuration = duration,
                preferredWindow = preferredWindow,
                priority = priority,
                flexibility = flexibility,
                splittable = splittable,
                minimumSession = minimumSession,
                fixedRange = fixedRange,
                proposedRelations = relations,
                assumptions = all.take(maxAssumptions),
                confidence = confidenceFor(title),
            )
        }

        /**
         * The draft a clarification option offers, built from this same scan.
         *
         * Options are produced by transforming the scanned draft rather than by re-parsing the
         * answer, so accepting an option can never yield a different interpretation than the
         * one that was offered.
         */
        fun patch(transform: (TaskIntentDraft) -> TaskIntentDraft): TaskIntentDraft =
            transform(buildDraft(buildTitle(titleTokens)))

        fun option(label: String, transform: (TaskIntentDraft) -> TaskIntentDraft): ClarificationOption =
            ClarificationOption(label, patch(transform))

        /**
         * A structural score in 0..1: how much of the utterance the grammar understood.
         *
         * It is not a probability of correctness and it is never mixed with recognizer
         * confidence, which the platform does not always report on device. Only the review
         * screen reads it, to decide whether to draw attention to the assumptions list.
         */
        private fun confidenceFor(title: String): Float {
            var score = 0.5f
            score += if (title.split(' ').count { it.isNotBlank() } >= 2) 0.2f else -0.2f
            if (duration != null) score += 0.1f
            if (requestedDate != null || deadline != null) score += 0.1f
            if (priority != null) score += 0.05f
            if (fixedRange != null) score += 0.1f
            if (ambiguousClock) score -= 0.15f
            if (assumptions.size >= 4) score -= 0.1f
            return score.coerceIn(0f, 1f)
        }
    }

    /**
     * The single clarification this parser may return, in a fixed order of consequence: two
     * deadlines, a start time with no end, two durations, two priorities, two different days,
     * then a vague timing marker.
     *
     * Everything else travels to review as an assumption. A question the user cannot answer
     * from its options would be worse than a reviewable draft.
     */
    private fun clarificationFor(scan: Scan, title: String): ClarificationPrompt? {
        scan.deadlineAlternative?.let { other ->
            val spoken = requireNotNull(scan.deadline)
            return ClarificationPrompt(
                field = DraftField.DEADLINE,
                question = "Two due dates were heard for “$title”. Which one is the deadline?",
                options = listOf(
                    scan.option(formatDeadline(other)) { it.copy(deadline = other) },
                    scan.option(formatDeadline(spoken)) { it.copy(deadline = spoken) },
                ),
                base = scan.patch { it.copy(deadline = null) },
            )
        }

        val start = scan.heardTimeStart
        if (start != null && scan.duration == null && scan.fixedRange == null) {
            val options = listOf(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2))
            return ClarificationPrompt(
                field = DraftField.DURATION,
                question = "“$title” starts at ${formatTime(start)} but no end was heard. How long is it?",
                options = options.map { duration ->
                    scan.option(formatDuration(duration)) { draft ->
                        draft.copy(
                            estimatedDuration = duration,
                            preferredWindow = windowStartingAt(start, duration),
                        )
                    }
                },
                base = scan.patch { it.copy(estimatedDuration = null, preferredWindow = null) },
            )
        }

        scan.durationAlternative?.let { other ->
            val spoken = requireNotNull(scan.duration)
            return ClarificationPrompt(
                field = DraftField.DURATION,
                question = "Two durations were heard for “$title”. How long does it take?",
                options = listOf(other, spoken).map { duration ->
                    scan.option(formatDuration(duration)) { it.copy(estimatedDuration = duration) }
                },
                base = scan.patch { it.copy(estimatedDuration = null) },
            )
        }

        scan.priorityAlternative?.let { other ->
            val spoken = requireNotNull(scan.priority)
            return ClarificationPrompt(
                field = DraftField.PRIORITY,
                question = "Two priorities were heard for “$title”. Which one is right?",
                options = listOf(other, spoken).map { priority ->
                    scan.option(priority.label()) { it.copy(priority = priority) }
                },
                base = scan.patch { it.copy(priority = null) },
            )
        }

        scan.dateAlternative?.let { other ->
            val spoken = requireNotNull(scan.requestedDate)
            return ClarificationPrompt(
                field = DraftField.REQUESTED_DATE,
                question = "Two different days were heard for “$title”. Which day did you mean?",
                options = listOf(other, spoken).map { date ->
                    scan.option(DATE.format(date)) { it.copy(requestedDate = date) }
                },
                base = scan.patch { it.copy(requestedDate = null) },
            )
        }

        if (scan.dateIsVague && scan.requestedDate == null) {
            val today = scan.referenceDate
            val tomorrow = today.plusDays(1)
            return ClarificationPrompt(
                field = DraftField.REQUESTED_DATE,
                question = "When should “$title” happen?",
                options = listOf(
                    scan.option("Today") { draft ->
                        draft.copy(
                            requestedDate = today,
                            assumptions = draft.assumptions + chosen(DATE.format(today)),
                        )
                    },
                    scan.option("Tomorrow") { draft ->
                        draft.copy(
                            requestedDate = tomorrow,
                            assumptions = draft.assumptions + chosen(DATE.format(tomorrow)),
                        )
                    },
                    scan.option("No date — keep it in the inbox") { draft ->
                        draft.copy(
                            requestedDate = null,
                            assumptions = draft.assumptions + ParseAssumption(
                                DraftField.REQUESTED_DATE,
                                "No date was chosen, so the task stays in the inbox until you schedule it.",
                            ),
                        )
                    },
                ),
                base = scan.patch { it.copy(requestedDate = null) },
            )
        }
        return null
    }

    // ---------------------------------------------------------------- helpers

    private fun chosen(date: String) = ParseAssumption(DraftField.REQUESTED_DATE, "Chosen: $date.")

    private fun windowStartingAt(start: LocalTime, duration: Duration): TimeWindow? {
        val end = start.plusMinutes(duration.toMinutes())
        if (end <= start) return null
        return TimeWindow(start, end)
    }

    /** A trailing token the scanner would otherwise read as timing information. */
    private fun isCueToken(token: String, referenceDate: LocalDate): Boolean {
        val bare = ParseGrammar.bare(token)
        if (bare in CAPTURE_FILLERS || bare in TITLE_TRIM_WORDS) return true
        val single = listOf(token)
        if (ParseGrammar.dateAt(single, 0, referenceDate) != null) return true
        if (ParseGrammar.durationAt(single, 0) != null) return true
        if (ParseGrammar.timeAt(single, 0) != null) return true
        if (ParseGrammar.deadlineCueAt(single, 0) != null) return true
        if (ParseGrammar.isAmbiguousClockNumber(token)) return true
        return bare in setOf("for", "on", "at", "in", "by", "before", "until", "till", "after")
    }

    private fun buildTitle(tokens: List<String>): String {
        // Speech punctuation is an artefact of the recogniser, not of the task: a title keeps
        // its words and loses the quotes and full stops that surrounded them.
        val trimmed = tokens.map { it.trim(*TITLE_EDGE_PUNCTUATION) }.toMutableList()
        while (trimmed.isNotEmpty() && ParseGrammar.bare(trimmed.first()) in TITLE_TRIM_WORDS) {
            trimmed.removeAt(0)
        }
        while (trimmed.isNotEmpty() && ParseGrammar.bare(trimmed.last()) in TITLE_TRIM_WORDS) {
            trimmed.removeAt(trimmed.lastIndex)
        }
        val joined = trimmed.joinToString(" ").replace(WHITESPACE, " ").trim()
        if (joined.isEmpty()) return ""
        // Capitalise the first character only: the rest is the user's own wording, including
        // acronyms and non-Latin script.
        return joined.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * End of a local day, computed as the next day's start minus one nanosecond so a daylight
     * saving change cannot push a deadline into the following morning.
     */
    private fun endOfDay(date: LocalDate, zone: ZoneId): ZonedDateTime =
        date.plusDays(1).atStartOfDay(zone).minusNanos(1)

    private fun formatTime(time: LocalTime): String = TIME.format(time)

    private fun formatDuration(duration: Duration): String {
        val minutes = duration.toMinutes()
        return if (minutes % 60 == 0L) "${minutes / 60} h" else "$minutes min"
    }

    private fun formatDeadline(instant: ZonedDateTime): String = DEADLINE_LABEL.format(instant)

    private fun Priority.label(): String = when (this) {
        Priority.LOW -> "Low"
        Priority.IMPORTANT -> "Important"
        Priority.CRITICAL -> "Very important"
    }

    private companion object {
        private const val MAX_ASSUMPTIONS = 10

        private val WHITESPACE = Regex("\\s+")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
        private val DEADLINE_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

        private val CAPTURE_FILLERS = ParseGrammar.CAPTURE_FILLERS
        private val CAPTURE_FILLER_PHRASES = ParseGrammar.CAPTURE_FILLER_PHRASES.toSet()
        private val TITLE_TRIM_WORDS = ParseGrammar.TITLE_TRIM_WORDS

        private val TITLE_EDGE_PUNCTUATION = charArrayOf(
            '.', ',', ';', ':', '!', '?', '"', '\'', '\u2019', '(', ')', '-',
        )

        private val RANGE_OPENERS = setOf("from", "between")
        private val RANGE_SEPARATORS = setOf("to", "until", "till", "-", "and")
        private val RELATION_VERBS = setOf("finishing", "finish", "i", "we", "they")
        private val RELATION_TERMINATORS = setOf("done", "finished", "complete", "over")

        private val FIXED_WORDS = setOf(
            "fixed", "appointment", "booked", "reservation", "class", "lecture", "meeting",
        )
        private val FLEXIBLE_WORDS = setOf("sometime", "whenever", "anytime", "flexible")

        private val SPLIT_PHRASES = setOf(
            "in parts", "in pieces", "in chunks", "in sessions", "bit by bit", "split", "splitting",
            "across sessions", "over sessions", "part by part",
        )
        private val MINIMUM_SESSION_PHRASES = setOf(
            "at least", "at minimum", "chunks of", "in chunks of", "sessions of",
        )
        private val REPEAT_PHRASES = setOf("at a time", "each time", "per session", "per sitting")
    }
}