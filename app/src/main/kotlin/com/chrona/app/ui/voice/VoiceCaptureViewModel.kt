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
 * Every state the capture flow can be in (DATA_MODEL_AND_INTERFACES.md §"Voice states are
 * explicit").
 *
 * The permission and platform states are named, not folded into a generic error, because each
 * one has a different honest answer: [RequestingPermission] asks, [Unavailable] and [Failed]
 * point at typing, and only [Review] can reach storage.
 */
sealed interface VoiceUiState {

    /** No capture sheet. Reached by closing it or by saving. */
    data object Closed : VoiceUiState

    /** The sheet is open and nothing has started. The microphone button is visible. */
    data object Idle : VoiceUiState

    /** Waiting for the user's answer to the system permission dialog. */
    data object RequestingPermission : VoiceUiState

    /** The recognizer is armed. [partialText] is interim feedback only. */
    data class Listening(val partialText: String? = null) : VoiceUiState

    /**
     * The transcript was understood and is waiting for confirmation.
     *
     * [prompt] is non-null while one question is unanswered; answering it replaces [draft] with
     * the chosen option and clears the prompt.
     */
    data class Review(
        val transcript: String,
        val draft: TaskIntentDraft,
        val prompt: ClarificationPrompt?,
        val title: String,
        val durationMinutes: String,
        val dateText: String,
        val problems: List<String> = emptyList(),
    ) : VoiceUiState {

        /** True when saving should be offered for the text currently in the fields. */
        val canSave: Boolean get() = title.isNotBlank() && problems.isEmpty()
    }

    /**
     * This phone cannot listen, or the user declined.
     *
     * Not a dead end: the same screen still opens the typed editor.
     */
    data class Unavailable(val message: String) : VoiceUiState

    /** A session failed. [canRetry] is false when retrying would fail the same way. */
    data class Failed(val message: String, val canRetry: Boolean = true) : VoiceUiState

    data object Cancelled : VoiceUiState

    data class Saved(val title: String) : VoiceUiState
}

/**
 * Drives one explicit tap-to-speak capture (CH-PLAT-001, CH-PARSE-001).
 *
 * It owns the state machine and nothing else: no scheduling rules, no storage rules, no
 * recogniser. Every collaborator is injected, so the whole flow is exercised in JVM tests with
 * a fake adapter and a fixed clock.
 *
 * Tapping the microphone is the only way in (MODULE_DEPENDENCY_MAP rule 8); nothing here starts
 * listening on its own, and leaving the screen or clearing the ViewModel cancels the session,
 * which releases the platform recogniser.
 */
class VoiceCaptureViewModel(
    private val speech: SpeechRecognitionAdapter,
    private val parser: TaskUnderstandingProvider,
    private val createTaskFromIntent: suspend (TaskIntentDraft) -> Outcome<Task>,
    private val timeSource: TimeSource,
    private val zoneId: ZoneId,
    private val localeTag: String,
    private val parsePreferences: ParsePreferences = ParsePreferences(),
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Closed)
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var session: Job? = null

    /** Opens the capture sheet. The only entry point into the voice flow. */
    fun open() {
        _state.value = VoiceUiState.Idle
    }

    /** Called by the microphone button. Checks the platform before touching the recogniser. */
    fun start() {
        when (speech.availability()) {
            SpeechAvailability.Available -> listen()
            SpeechAvailability.PermissionRequired -> _state.value = VoiceUiState.RequestingPermission
            SpeechAvailability.OnDeviceUnavailable -> _state.value = VoiceUiState.Unavailable(
                "This phone has no on-device speech recognition. Typing works exactly the same way.",
            )
        }
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

    /** Stops listening and returns to the idle state. The recogniser is released. */
    fun cancel() {
        session?.cancel()
        session = null
        _state.value = VoiceUiState.Cancelled
    }

    /** Tries the same request again after a recoverable failure. */
    fun retry() = start()

    /** Back to the start of the sheet, keeping the typed fallback available. */
    fun reset() {
        session?.cancel()
        session = null
        _state.value = VoiceUiState.Idle
    }

    /** Closes the sheet. Any live session is cancelled, which releases the recogniser. */
    fun close() {
        session?.cancel()
        session = null
        _state.value = VoiceUiState.Closed
    }

    /** Accepts one offered answer. The patch replaces the draft outright. */
    fun answer(option: ClarificationOption) {
        val current = _state.value as? VoiceUiState.Review ?: return
        _state.value = current.withDraft(option.patch, prompt = null, problems = emptyList())
    }

    /** Records a corrected field from the review screen. */
    fun editTitle(value: String) = edit { it.copy(title = value) }

    fun editDurationMinutes(value: String) = edit { it.copy(durationMinutes = value) }

    fun editDate(value: String) = edit { it.copy(dateText = value) }

    private fun edit(change: (VoiceUiState.Review) -> VoiceUiState.Review) {
        _state.update { current -> if (current is VoiceUiState.Review) change(current) else current }
    }

    /**
     * Saves the reviewed draft.
     *
     * The draft is rebuilt from the fields the user can correct, so what is stored is what is
     * on screen. A validation failure keeps the sheet open with the reasons as plain text
     * instead of discarding the transcript.
     */
    fun confirm() {
        val current = _state.value as? VoiceUiState.Review ?: return
        if (!current.canSave) return

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

        // A corrected field replaces the parser's assumption about it rather than contradicting
        // it, so what the task records is exactly what the user agreed to.
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

        val draft = current.draft.copy(
            title = current.title.trim(),
            estimatedDuration = duration?.let { java.time.Duration.ofMinutes(it) },
            requestedDate = date,
            assumptions = assumptions,
        )

        viewModelScope.launch {
            when (val result = createTaskFromIntent(draft)) {
                is Outcome.Success -> _state.value = VoiceUiState.Saved(result.value.title)
                is Outcome.Failed -> _state.value = current.copy(problems = describe(result.failure))
            }
        }
    }

    private fun listen() {
        session?.cancel()
        _state.value = VoiceUiState.Listening()
        session = viewModelScope.launch {
            speech.listen(SpeechRequest(localeTag = localeTag)).collect { event ->
                when (event) {
                    SpeechEvent.Ready -> _state.value = VoiceUiState.Listening()
                    is SpeechEvent.Partial -> _state.value = VoiceUiState.Listening(event.text)
                    is SpeechEvent.Final -> interpret(event.text)
                    is SpeechEvent.Failed -> _state.value = VoiceUiState.Failed(
                        message = event.reason.guidance,
                        canRetry = event.reason != SpeechError.INSUFFICIENT_PERMISSIONS,
                    )
                }
            }
        }
    }

    /** Parses a transcript and moves to review, clarification or a plain-language refusal. */
    private suspend fun interpret(transcript: String) {
        _state.value = VoiceUiState.Listening(transcript)
        val reference = timeSource.now().atZone(zoneId)
        val request = TaskUnderstandingRequest(
            text = transcript,
            referenceTime = reference,
            zoneId = zoneId,
            preferences = parsePreferences,
        )
        when (val result = parser.understand(request)) {
            is TaskUnderstandingResult.Parsed ->
                _state.value = result.draft.toReview(transcript, prompt = null)

            is TaskUnderstandingResult.NeedsClarification -> _state.value =
                result.prompt.base.toReview(transcript, result.prompt)

            is TaskUnderstandingResult.Unsupported -> _state.value = VoiceUiState.Failed(
                message = result.reason.message,
                canRetry = false,
            )
        }
    }

    private fun VoiceUiState.Review.withDraft(
        draft: TaskIntentDraft,
        prompt: ClarificationPrompt?,
        problems: List<String>,
    ): VoiceUiState.Review = draft.toReview(transcript, prompt, problems)

    private fun TaskIntentDraft.toReview(
        transcript: String,
        prompt: ClarificationPrompt?,
        problems: List<String> = emptyList(),
    ): VoiceUiState.Review = VoiceUiState.Review(
        transcript = transcript,
        draft = this,
        prompt = prompt,
        title = title,
        durationMinutes = estimatedDuration?.toMinutes()?.toString().orEmpty(),
        dateText = requestedDate?.toString().orEmpty(),
        problems = problems,
    )

    /**
     * Explicit collaborators, mirroring `TodayViewModel.Factory`, so the state machine can be
     * built in tests with a fake adapter and a fake parser.
     */
    class Factory(
        private val speech: SpeechRecognitionAdapter,
        private val parser: TaskUnderstandingProvider,
        private val createTaskFromIntent: suspend (TaskIntentDraft) -> Outcome<Task>,
        private val timeSource: TimeSource,
        private val zoneId: ZoneId,
        private val localeTag: String,
        private val parsePreferences: ParsePreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VoiceCaptureViewModel(
            speech = speech,
            parser = parser,
            createTaskFromIntent = createTaskFromIntent,
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