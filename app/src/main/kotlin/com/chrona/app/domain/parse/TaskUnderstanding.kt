package com.chrona.app.domain.parse

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Parser input and output, exactly as `SHARED_CONTRACT.md` §"Task intent parsing contract"
 * defines them.
 *
 * Two rules from that contract are enforced by the shape of these types rather than by
 * comments alone:
 *
 * - The parser interprets; it never places a task on the calendar. There is no field here for
 *   "the slot this task should occupy".
 * - The parser cannot confirm a dependency. [RelationDraft.createdBy] is always
 *   [RelationCreatedBy.SUGGESTED], so a suggested edge stays inactive until the user confirms
 *   it in the relation editor (MASTER_REQUIREMENTS §7).
 */

/**
 * Configuration that changes how a phrase is read.
 *
 * Only values that genuinely alter an interpretation belong here. Anything fixed by the
 * bounded grammar (morning starts at 09:00, and so on) is a constant in the parser instead,
 * so it can be documented and changed in one place.
 */
data class ParsePreferences(
    /** First hour of the user's day. Anchors the "morning" window. */
    val dayStart: LocalTime = LocalTime.of(8, 0),
    /** Last hour of the user's day. Anchors the "evening" window. */
    val dayEnd: LocalTime = LocalTime.of(22, 0),
    /**
     * Below this structural score Chrona asks one focused question instead of guessing.
     *
     * The score is a heuristic indicator, not a calibrated probability, so this threshold is a
     * product decision rather than a derived constant.
     */
    val confidenceThreshold: Float = 0.6f,
)

/** Everything the parser is allowed to know. No global clock, no Android, no I/O. */
data class TaskUnderstandingRequest(
    val text: String,
    val referenceTime: ZonedDateTime,
    val zoneId: ZoneId,
    val preferences: ParsePreferences,
)

/** The draft a user reviews and corrects before anything is persisted. */
data class TaskIntentDraft(
    val title: String,
    val detail: String? = null,
    val requestedDate: LocalDate? = null,
    val deadline: ZonedDateTime? = null,
    val estimatedDuration: Duration? = null,
    /** Soft preference. Not a deadline, and missing it is never a conflict. */
    val preferredWindow: TimeWindow? = null,
    val priority: Priority? = null,
    val flexibility: Flexibility? = null,
    val splittable: Boolean? = null,
    val minimumSession: Duration? = null,
    /**
     * Absolute interval for an explicitly fixed block, e.g. "dentist 3 to 4 tomorrow".
     *
     * Added in P03: `SHARED_CONTRACT.md` listed [flexibility] without an instant, which cannot
     * express a fixed commitment even though the parser grammar must read one.
     */
    val fixedRange: TimeRange? = null,
    val proposedRelations: List<RelationDraft> = emptyList(),
    val assumptions: List<ParseAssumption> = emptyList(),
    /** Structural score in 0..1. Heuristic, never a calibrated probability. */
    val confidence: Float = 0f,
)

/**
 * A relation the utterance hinted at.
 *
 * [targetText] is the spoken reference, not a [com.chrona.app.domain.model.TaskId]: the parser
 * does not read the task list, so resolving "the notes" to a task is the relation editor's job
 * in P05. Until then the edge is a suggestion.
 */
data class RelationDraft(
    val type: RelationType,
    val targetText: String,
    val label: String? = null,
    val explanation: String? = null,
    /**
     * Always [RelationCreatedBy.SUGGESTED]. The enum keeps the field so P05 can construct a
     * confirmed edge from user input; the parser cannot produce one.
     */
    val createdBy: RelationCreatedBy = RelationCreatedBy.SUGGESTED,
)

/** The draft field a clarification or assumption is about. */
enum class DraftField {
    TITLE,
    REQUESTED_DATE,
    DEADLINE,
    DURATION,
    TIME_OF_DAY,
    PRIORITY,
    FLEXIBILITY,
    RELATION,
}

/**
 * One interpretation the parser made that the user has not confirmed.
 *
 * [text] is the sentence shown during review and persisted on the task. It must stay a short
 * human-readable explanation: no model traces, no confidence percentages, no raw transcript.
 */
data class ParseAssumption(val field: DraftField, val text: String)

/**
 * One answer to a clarification question.
 *
 * An option carries the whole alternative [patch] rather than a string to be re-parsed, so
 * answering can never produce a different interpretation than the one being offered.
 */
data class ClarificationOption(val label: String, val patch: TaskIntentDraft)

/**
 * A single focused question.
 *
 * The parser never returns more than one, and never an open-ended one: if the honest answer
 * cannot be one of [options], the UI offers the typed editor instead.
 *
 * [base] is the draft with the questioned field still empty. It is what the user sees if they
 * choose to correct the fields by hand rather than answer, so no option is applied on their
 * behalf.
 */
data class ClarificationPrompt(
    val field: DraftField,
    val question: String,
    val options: List<ClarificationOption>,
    val base: TaskIntentDraft,
)

/**
 * Why a transcript cannot become a draft.
 *
 * A closed set on purpose: an unbounded "reason string" would let the parser invent its own
 * categories, and the UI needs to know when to offer typing rather than guessing.
 */
enum class ParseFailureReason(val message: String) {
    EMPTY_INPUT("Chrona did not catch any words."),

    INPUT_TOO_LONG("That was too long to read as a single task. Try a shorter sentence."),

    UNSUPPORTED_LANGUAGE(
        "Chrona's voice grammar understands English only. Type the task instead.",
    ),

    NOT_A_TASK_REQUEST("That sounded like a question, not something to add."),

    UNSUPPORTED_COMMAND(
        "Chrona does not change existing tasks from a voice command. Open the task and use its buttons.",
    ),

    NO_TITLE_REMAINING("Only times and dates were heard. Add what the task actually is."),

    MALFORMED_DATE("Chrona could not read that date. Type it instead."),

    IMPLAUSIBLE_DURATION("That duration cannot be right. Type the task instead."),
}

/** The three outcomes the contract allows. Anything else is a bug. */
sealed interface TaskUnderstandingResult {
    data class Parsed(val draft: TaskIntentDraft) : TaskUnderstandingResult

    data class NeedsClarification(val prompt: ClarificationPrompt) : TaskUnderstandingResult

    data class Unsupported(val reason: ParseFailureReason) : TaskUnderstandingResult
}

/**
 * Provider-neutral understanding boundary (CH-PARSE-001).
 *
 * Chrona ships exactly one implementation, [DeterministicTaskIntentParser]. The interface
 * exists so the parser is a replaceable test seam and so a future provider cannot be wired in
 * without an approved change — not as an invitation to add one now.
 */
interface TaskUnderstandingProvider {
    suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult
}