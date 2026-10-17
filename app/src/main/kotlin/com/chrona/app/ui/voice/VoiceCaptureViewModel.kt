package com.chrona.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.parse.ClarificationOption
import com.chrona.app.domain.parse.ClarificationPrompt
import com.chrona.app.domain.parse.DraftField
import com.chrona.app.domain.parse.ParseAssumption
import com.chrona.app.domain.parse.ParsePreferences
import com.chrona.app.domain.parse.TaskIntentDraft
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.parse.TaskUnderstandingRequest
import com.chrona.app.domain.parse.TaskUnderstandingResult
import com.chrona.app.platform.speech.SpeechAvailability
import com.chrona.app.platform.speech.SpeechError
import com.chrona.app.platform.speech.SpeechEvent
import com.chrona.app.platform.speech.SpeechRequest
import com.chrona.app.platform.speech.SpeechRecognitionAdapter
import com.chrona.app.usecase.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every state the capture flow can be in.
 *
 * The permission and platform states are named, not folded into a generic error, because each one
 * has a different honest answer: [RequestingPermission] asks, [Unavailable] and [Failed] point at
 * typing, and only [Clarify] can reach storage.
 */
sealed interface VoiceUiState {

    /** No capture in progress. Reached by dismissing the panel or after a confirmation. */
    data object Idle : VoiceUiState

    /** Waiting for the user's answer to the system permission dialog. */
    data object RequestingPermission : VoiceUiState

    /** The recogniser is armed. [partialText] is interim feedback only. */
    data class Listening(val partialText: String? = null) : VoiceUiState

    /**
     * The transcript is understood and the tasks are being written.
     *
     * Its own state, not a blank screen: cloud understanding is a network round trip, and the
     * user has to be able to see that the whole utterance is being worked on.
     */
    data class Interpreting(val transcript: String) : VoiceUiState

    /**
     * One field in the utterance is genuinely ambiguous, so one task waits for a decision.
     *
     * Nothing else blocks: this state exists for the question the parser could not answer on the
     * user's behalf, never as a confirmation step before every task is stored.
     */
    data class Clarify(
        val transcript: String,
        val draft: TaskIntentDraft,
        val question: String,
        val options: List<ClarificationOption>,
        val title: String,
        val durationMinutes: String,
        val dateText: String,
        val problems: List<String> = emptyList(),
    ) : VoiceUiState {

        /** True when the text currently in the fields is enough to create the task. */
        val canCreate: Boolean get() = title.isNotBlank() && problems.isEmpty()
    }

    /**
     * What was stored, one entry per task.
     *
     * A single utterance can produce several tasks, and every one of them appears here, because
     * an extra task that was created quietly is a task the user cannot find later.
     */
    data class Created(
        val tasks: List<Task>,
        /** Drafts that could not be stored, in plain language. Empty on a clean capture. */
        val problems: List<String> = emptyList(),
    ) : VoiceUiState

    /**
     * This phone cannot listen, or the user declined.
     *
     * Not a dead end: typing remains available.
     */
    data class Unavailable(val message: String) : VoiceUiState

    /** A session failed. [canRetry] is false when retrying would fail the same way. */
    data class Failed(val message: String, val canRetry: Boolean = true) : VoiceUiState
}

/**
 * Drives one explicit press-to-speak capture.
 *
 * It owns the state machine and nothing else: no scheduling rules, no storage rules, no
 * recogniser. Every collaborator is injected, so the whole flow is exercised in JVM tests with a
 * fake adapter and a fixed clock.
 *
 * The flow is voice-first and low-friction: hold the microphone, speak, release, and the tasks
 * are stored. Two rules are structural rather than documented:
 *
 * - **Nothing is asked twice.** A transcript is stored straight away; the only screen between
 *   speech and storage is [VoiceUiState.Clarify], reached only when the parser genuinely cannot
 *   choose between two readings of a field.
 * - **Nothing is dropped.** Every draft the parser returns becomes its own record, and every
 *   record is reported back in [VoiceUiState.Created].
 */
class VoiceCaptureViewModel(
    private val speech: SpeechRecognitionAdapter,
    private val parser: TaskUnderstandingProvider,
    private val createTasksFromIntents: suspend (List<TaskIntentDraft>) -> List<Outcome<Task>>,
    private val timeSource: TimeSource,
    private val zoneId: ZoneId,
    private val localeTag: String,
    private val parsePreferences: ParsePreferences = ParsePreferences(),
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var session: Job? = null

    /**
     * Opened when the microphone is pressed.
     *
     * Re-entrant by construction: a press that arrives while a session is already running, or
     * while the previous transcript is still being stored, is ignored rather than starting a
     * second one. That is what stops a held or repeated press from creating duplicate tasks.
     */
    fun pressToListen() {
        if (_state.value is VoiceUiState.Listening || _state.value is VoiceUiState.RequestingPermission) return
        if (_state.value is VoiceUiState.Interpreting) return
        session?.cancel()
        session = null
        when (speech.availability()) {
            SpeechAvailability.Available -> listen()
            SpeechAvailability.PermissionRequired -> _state.value = VoiceUiState.RequestingPermission
            SpeechAvailability.OnDeviceUnavailable -> _state.value = VoiceUiState.Unavailable(
                "This phone has no on-device speech recognition. Typing works exactly the same way.",
            )
        }
    }

    /**
     * Opened when the microphone is released.
     *
     * The recogniser is asked to stop rather than cancelled, so the platform still delivers the
     * transcript it has assembled so far. If it has heard nothing, the session ends with nothing
     * and the microphone simply returns to idle — that is not an error worth a message.
     */
    fun release() {
        if (_state.value !is VoiceUiState.Listening) return
        speech.stop()
    }

    /**
     * Called with the system permission answer.
     *
     * A denial is a normal outcome, not an error: typing is offered and nothing else changes.
     */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            listen()
        } else {
            _state.value = VoiceUiState.Unavailable(
                "Chrona cannot listen without the microphone permission. Type the task instead.",
            )
        }
    }

    /** Returns to the microphone without a pending decision, cancelling any live session. */
    fun dismiss() {
        session?.cancel()
        session = null
        _state.value = VoiceUiState.Idle
    }

    /** Tries the same request again after a recoverable failure. */
    fun retry() = pressToListen()

    /**
     * Accepts one offered answer. An option carries the whole draft it stands for, so choosing
     * one stores that exact interpretation rather than a re-parse of the same words.
     */
    fun answer(option: ClarificationOption) {
        if (_state.value !is VoiceUiState.Clarify) return
        viewModelScope.launch { store(listOf(option.patch)) }
    }

    /** Records a corrected field while a question is outstanding. */
    fun editTitle(value: String) = edit { it.copy(title = value) }

    fun editDurationMinutes(value: String) = edit { it.copy(durationMinutes = value) }

    fun editDate(value: String) = edit { it.copy(dateText = value) }

    private fun edit(change: (VoiceUiState.Clarify) -> VoiceUiState.Clarify) {
        _state.update { current -> if (current is VoiceUiState.Clarify) change(current) else current }
    }

    /**
     * Creates the task behind an outstanding question, from the fields as they now read.
     *
     * The draft is rebuilt from those fields, so what is stored is what is on screen. A field the
     * user corrected replaces the parser's assumption about it instead of contradicting it.
     */
    fun create() {
        val current = _state.value as? VoiceUiState.Clarify ?: return
        if (!current.canCreate) return

        val duration = current.durationMinutes.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        if (current.durationMinutes.isNotBlank() && duration == null) {
            _state.value = current.copy(problems = listOf("Minutes must be a whole number."))
            return
        }
        val date = current.dateText.trim().takeIf { it.isNotEmpty() }?.let { text ->
            runCatching { LocalDate.parse(text) }.getOrNull()
        }
        if (current.dateText.isNotBlank() && date == null) {
            _state.value = current.copy(problems = listOf("Use the date format YYYY-MM-DD."))
            return
        }

        val durationChanged = duration != current.draft.estimatedDuration?.toMinutes()
        val dateChanged = date != current.draft.requestedDate
        val assumptions = buildList {
            addAll(
                current.draft.assumptions.filterNot { assumption ->
                    (durationChanged && assumption.field == DraftField.DURATION) ||
                        (dateChanged && assumption.field == DraftField.REQUESTED_DATE)
                },
            )
            if (durationChanged) {
                add(ParseAssumption(DraftField.DURATION, "You set the time to ${duration ?: "none"}."))
            }
            if (dateChanged) {
                add(ParseAssumption(DraftField.REQUESTED_DATE, "You set the day to ${date ?: "none"}."))
            }
        }

        viewModelScope.launch {
            store(
                listOf(
                    current.draft.copy(
                        title = current.title.trim(),
                        estimatedDuration = duration?.let { java.time.Duration.ofMinutes(it) },
                        requestedDate = date,
                        assumptions = assumptions,
                    ),
                ),
            )
        }
    }

    private fun listen() {
        _state.value = VoiceUiState.Listening()
        session = viewModelScope.launch {
            speech.listen(SpeechRequest(localeTag = localeTag)).collect { event ->
                when (event) {
                    SpeechEvent.Ready -> if (_state.value is VoiceUiState.Listening) {
                        _state.value = VoiceUiState.Listening()
                    }

                    is SpeechEvent.Partial -> if (_state.value is VoiceUiState.Listening) {
                        _state.value = VoiceUiState.Listening(event.text)
                    }

                    is SpeechEvent.Final -> {
                        // Stop collecting before understanding, so a second final result cannot
                        // start a second interpretation of the same utterance.
                        session?.cancel()
                        session = null
                        viewModelScope.launch { interpret(event.text) }
                    }

                    is SpeechEvent.Failed -> {
                        session = null
                        _state.value = VoiceUiState.Failed(
                            message = event.reason.guidance,
                            canRetry = event.reason != SpeechError.INSUFFICIENT_PERMISSIONS,
                        )
                    }
                }
            }
            // The session ended without a transcript: a release too early, or a recogniser that
            // simply stopped. Nothing was heard, so there is nothing to report.
            if (_state.value is VoiceUiState.Listening) _state.value = VoiceUiState.Idle
        }
    }

    /** Understands a transcript and stores what it found. */
    private suspend fun interpret(transcript: String) {
        _state.value = VoiceUiState.Interpreting(transcript)
        val reference = timeSource.now().atZone(zoneId)
        val request = TaskUnderstandingRequest(
            text = transcript,
            referenceTime = reference,
            zoneId = zoneId,
            preferences = parsePreferences,
        )
        when (val result = parser.understand(request)) {
            is TaskUnderstandingResult.Parsed -> store(result.drafts)

            is TaskUnderstandingResult.NeedsClarification -> _state.value =
                result.prompt.toClarify(transcript)

            is TaskUnderstandingResult.Unsupported -> _state.value = VoiceUiState.Failed(
                message = result.reason.message,
                canRetry = false,
            )
        }
    }

    /**
     * Writes the drafts and reports exactly what happened.
     *
     * Every draft is attempted, so one draft the domain rejects cannot swallow the others, and
     * nothing is stored behind the user's back: the result names each task that now exists.
     */
    private suspend fun store(drafts: List<TaskIntentDraft>) {
        if (drafts.isEmpty()) {
            _state.value = VoiceUiState.Failed(
                message = "Chrona did not hear a task in that. Hold the microphone and try again.",
                canRetry = true,
            )
            return
        }
        val results = createTasksFromIntents(drafts)
        val created = results.filterIsInstance<Outcome.Success<Task>>().map { it.value }
        val problems = results.filterIsInstance<Outcome.Failed>()
            .flatMap { describe(it.failure) }
        _state.value = if (created.isEmpty()) {
            VoiceUiState.Failed(
                message = problems.firstOrNull() ?: "Chrona could not save that. Try again.",
                canRetry = true,
            )
        } else {
            VoiceUiState.Created(created, problems)
        }
    }

    private fun ClarificationPrompt.toClarify(transcript: String): VoiceUiState.Clarify =
        VoiceUiState.Clarify(
            transcript = transcript,
            draft = base,
            question = question,
            options = options,
            title = base.title,
            durationMinutes = base.estimatedDuration?.toMinutes()?.toString().orEmpty(),
            dateText = base.requestedDate?.toString().orEmpty(),
        )

    /**
     * Explicit collaborators, mirroring `TodayViewModel.Factory`, so the state machine can be
     * built in tests with a fake adapter and a fake parser.
     */
    class Factory(
        private val speech: SpeechRecognitionAdapter,
        private val parser: TaskUnderstandingProvider,
        private val createTasksFromIntents: suspend (List<TaskIntentDraft>) -> List<Outcome<Task>>,
        private val timeSource: TimeSource,
        private val zoneId: ZoneId,
        private val localeTag: String,
        private val parsePreferences: ParsePreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VoiceCaptureViewModel(
            speech = speech,
            parser = parser,
            createTasksFromIntents = createTasksFromIntents,
            timeSource = timeSource,
            zoneId = zoneId,
            localeTag = localeTag,
            parsePreferences = parsePreferences,
        ) as T
    }

    private fun describe(failure: Failure): List<String> = when (failure) {
        is Failure.Validation -> failure.reasons
        Failure.NotFound -> listOf("That task no longer exists.")
        is Failure.Conflict -> listOf(failure.message)
        is Failure.StorageFailure -> listOf("Chrona could not save that. Try again.")
        is Failure.PermissionDenied -> listOf("Chrona needs permission for that.")
        is Failure.Unsupported -> listOf(failure.reason)
        is Failure.PlatformUnavailable -> listOf(failure.reason)
        is Failure.RequiresUserChoice -> listOf(failure.question)
        Failure.Cancelled -> listOf("Cancelled.")
        is Failure.UnknownFailure -> listOf("Something went wrong. Try again.")
    }
}