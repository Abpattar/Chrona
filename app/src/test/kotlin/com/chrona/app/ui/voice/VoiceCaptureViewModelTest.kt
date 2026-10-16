package com.chrona.app.ui.voice

import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.parse.ClarificationOption
import com.chrona.app.domain.parse.ClarificationPrompt
import com.chrona.app.domain.parse.DraftField
import com.chrona.app.domain.parse.ParseAssumption
import com.chrona.app.domain.parse.ParseFailureReason
import com.chrona.app.domain.parse.TaskIntentDraft
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.parse.TaskUnderstandingRequest
import com.chrona.app.domain.parse.TaskUnderstandingResult
import com.chrona.app.platform.speech.SpeechAvailability
import com.chrona.app.platform.speech.SpeechError
import com.chrona.app.platform.speech.SpeechEvent
import com.chrona.app.platform.speech.SpeechRecognitionAdapter
import com.chrona.app.platform.speech.SpeechRequest
import com.chrona.app.usecase.CreateTaskFromIntentUseCase
import com.chrona.app.usecase.CreateTaskUseCase
import com.chrona.app.usecase.FakeTaskRepository
import com.chrona.app.usecase.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The capture state machine (VOICE-001, TASK-001).
 *
 * The claims that matter here are the ones a user feels: nothing is stored until they confirm,
 * a question is never answered on their behalf, a denial or an unavailable recogniser leads to
 * typing rather than a dead end, and a cancelled session releases the recogniser.
 *
 * The recogniser and the parser are fakes; storage is the shared in-memory repository, so the
 * persistence assertions are real records, not mocks.
 */
class VoiceCaptureViewModelTest {

    private val repository = FakeTaskRepository { Fixtures.start }
    private val createFromIntent = CreateTaskFromIntentUseCase(
        CreateTaskUseCase(repository, Fixtures.SequentialIdGenerator(), Fixtures.FixedTimeSource()),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        speech: FakeSpeech = FakeSpeech(),
        parser: FakeParser = FakeParser(TaskUnderstandingResult.Parsed(draft())),
    ): Pair<VoiceCaptureViewModel, FakeSpeech> {
        val state = VoiceCaptureViewModel(
            speech = speech,
            parser = parser,
            createTaskFromIntent = { draft -> createFromIntent(draft) },
            timeSource = Fixtures.FixedTimeSource(),
            zoneId = ZONE,
            localeTag = "en-GB",
        )
        return state to speech
    }

    @Test
    fun `tapping the microphone checks the platform before listening`() {
        val (vm, speech) = viewModel(speech = FakeSpeech(SpeechAvailability.OnDeviceUnavailable))

        vm.start()

        assertTrue(vm.state.value is VoiceUiState.Unavailable)
        assertEquals("no recogniser may be opened when on-device recognition is absent", 0, speech.sessionCount)
    }

    @Test
    fun `a missing permission is requested, and a denial offers typing`() {
        val (vm, _) = viewModel(speech = FakeSpeech(SpeechAvailability.PermissionRequired))

        vm.start()
        assertEquals(VoiceUiState.RequestingPermission, vm.state.value)

        vm.onPermissionResult(granted = false)
        assertTrue(vm.state.value is VoiceUiState.Unavailable)
    }

    @Test
    fun `granting the permission starts one session`() {
        val (vm, speech) = viewModel(speech = FakeSpeech(SpeechAvailability.PermissionRequired))

        vm.start()
        vm.onPermissionResult(granted = true)

        assertEquals(VoiceUiState.Listening(), vm.state.value)
        assertEquals(1, speech.sessionCount)
    }

    @Test
    fun `a transcript is reviewed before anything is stored`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Ready)
        speech.send(SpeechEvent.Final("Study for GATE for two hours tomorrow"))

        val review = vm.state.value as VoiceUiState.Review
        assertEquals("Study for GATE", review.title)
        assertEquals("120", review.durationMinutes)
        assertEquals(LocalDate.of(2026, 10, 16), LocalDate.parse(review.dateText))
        assertTrue("nothing is persisted before confirmation", repository.rows.isEmpty())

        vm.confirm()

        assertEquals(VoiceUiState.Saved("Study for GATE"), vm.state.value)
        val stored = repository.rows.values.single()
        assertEquals(TaskOrigin.VOICE, stored.origin)
    }

    @Test
    fun `an outstanding question is answered by choice, never by assumption`() = runBlocking {
        val prompt = ClarificationPrompt(
            field = DraftField.TIME_OF_DAY,
            question = "Morning or afternoon?",
            options = listOf(
                ClarificationOption("Morning", draft(title = "Run", preferredMorning = true)),
                ClarificationOption("Afternoon", draft(title = "Run")),
            ),
            base = draft(title = "Run"),
        )
        val (vm, speech) = viewModel(parser = FakeParser(TaskUnderstandingResult.NeedsClarification(prompt)))

        vm.start()
        speech.send(SpeechEvent.Final("run sometime"))
        val waiting = vm.state.value as VoiceUiState.Review
        assertEquals("Morning or afternoon?", waiting.prompt?.question)
        assertTrue(repository.rows.isEmpty())

        vm.answer(prompt.options[0])

        val answered = vm.state.value as VoiceUiState.Review
        assertNull("answering clears the question", answered.prompt)
        vm.confirm()
        assertEquals(VoiceUiState.Saved("Run"), vm.state.value)
    }

    @Test
    fun `an unsupported transcript is refused without offering retry`() = runBlocking {
        val (vm, speech) = viewModel(
            parser = FakeParser(TaskUnderstandingResult.Unsupported(ParseFailureReason.NOT_A_TASK_REQUEST)),
        )

        vm.start()
        speech.send(SpeechEvent.Final("what should I do next?"))

        val failed = vm.state.value as VoiceUiState.Failed
        assertFalse("repeating the same words would fail the same way", failed.canRetry)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `a revoked permission cannot be fixed by retrying`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Failed(SpeechError.INSUFFICIENT_PERMISSIONS))

        assertFalse((vm.state.value as VoiceUiState.Failed).canRetry)
    }

    @Test
    fun `a speech timeout is retryable`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Failed(SpeechError.SPEECH_TIMEOUT))

        assertTrue((vm.state.value as VoiceUiState.Failed).canRetry)
    }

    @Test
    fun `cancelling stops the session and releases it`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Ready)
        vm.cancel()

        assertEquals(VoiceUiState.Cancelled, vm.state.value)
        assertEquals("the recogniser must be released", 1, speech.releasedCount)
    }

    @Test
    fun `corrected fields are what get stored`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Final("study for gate tomorrow"))
        vm.editTitle("Study for GATE")
        vm.editDate("2026-10-20")
        vm.confirm()

        val stored = repository.rows.values.single()
        assertEquals("Study for GATE", stored.title)
        assertEquals(LocalDate.of(2026, 10, 20), stored.requestedDate)
    }

    @Test
    fun `an unparsable duration is refused before storage`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.start()
        speech.send(SpeechEvent.Final("study for gate"))
        vm.editDurationMinutes("soon")
        vm.confirm()

        val review = vm.state.value as VoiceUiState.Review
        assertTrue(review.problems.isNotEmpty())
        assertTrue(repository.rows.isEmpty())
    }

    // ---------------------------------------------------------------- fakes

    private class FakeSpeech(
        private val availability: SpeechAvailability = SpeechAvailability.Available,
    ) : SpeechRecognitionAdapter {

        private val events = Channel<SpeechEvent>(Channel.UNLIMITED)
        var sessionCount = 0
            private set
        var releasedCount = 0
            private set

        override fun availability(): SpeechAvailability = availability

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> {
            sessionCount++
            return events.receiveAsFlow().onCompletion { releasedCount++ }
        }

        fun send(event: SpeechEvent) {
            events.trySend(event)
        }
    }

    private class FakeParser(private val result: TaskUnderstandingResult) : TaskUnderstandingProvider {
        override suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult = result
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("UTC")

        fun draft(
            title: String = "Study for GATE",
            preferredMorning: Boolean = false,
        ) = TaskIntentDraft(
            title = title,
            requestedDate = LocalDate.of(2026, 10, 16),
            estimatedDuration = Duration.ofHours(2),
            priority = Priority.IMPORTANT,
            assumptions = if (preferredMorning) {
                listOf(ParseAssumption(DraftField.TIME_OF_DAY, "Preferred in the morning."))
            } else {
                emptyList()
            },
        )
    }
}
