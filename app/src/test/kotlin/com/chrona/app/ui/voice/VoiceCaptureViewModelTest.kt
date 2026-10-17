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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The capture state machine.
 *
 * The claims that matter here are the ones a user feels: a spoken request becomes a stored task
 * without a confirmation ritual, one utterance can store several independent tasks and none of
 * them is quietly dropped, a question is never answered on the user's behalf, and a repeated
 * press cannot create the same task twice.
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
        parser: FakeParser = FakeParser(TaskUnderstandingResult.Parsed(listOf(draft()))),
    ): Pair<VoiceCaptureViewModel, FakeSpeech> {
        val state = VoiceCaptureViewModel(
            speech = speech,
            parser = parser,
            createTasksFromIntents = { drafts -> createFromIntent.createAll(drafts) },
            timeSource = Fixtures.FixedTimeSource(),
            zoneId = ZONE,
            localeTag = "en-GB",
        )
        return state to speech
    }

    @Test
    fun `pressing the microphone checks the platform before listening`() {
        val (vm, speech) = viewModel(speech = FakeSpeech(SpeechAvailability.OnDeviceUnavailable))

        vm.pressToListen()

        assertTrue(vm.state.value is VoiceUiState.Unavailable)
        assertEquals("no recogniser may be opened when on-device recognition is absent", 0, speech.sessionCount)
    }

    @Test
    fun `a missing permission is requested, and a denial offers typing`() {
        val (vm, _) = viewModel(speech = FakeSpeech(SpeechAvailability.PermissionRequired))

        vm.pressToListen()
        assertEquals(VoiceUiState.RequestingPermission, vm.state.value)

        vm.onPermissionResult(granted = false)
        assertTrue(vm.state.value is VoiceUiState.Unavailable)
    }

    @Test
    fun `granting the permission starts one session`() {
        val (vm, speech) = viewModel(speech = FakeSpeech(SpeechAvailability.PermissionRequired))

        vm.pressToListen()
        vm.onPermissionResult(granted = true)

        assertEquals(VoiceUiState.Listening(), vm.state.value)
        assertEquals(1, speech.sessionCount)
    }

    @Test
    fun `holding again while already listening does not open a second session`() {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        vm.pressToListen()
        vm.pressToListen()

        assertEquals("a held or repeated press must not duplicate the session", 1, speech.sessionCount)
    }

    @Test
    fun `a transcript is stored straight away, without a review step`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        speech.send(SpeechEvent.Ready)
        speech.send(SpeechEvent.Final("Study for GATE for two hours tomorrow"))

        val created = vm.state.value as VoiceUiState.Created
        assertEquals(listOf("Study for GATE"), created.tasks.map { it.title })
        val stored = repository.rows.values.single()
        assertEquals(TaskOrigin.VOICE, stored.origin)
        assertEquals(LocalDate.of(2026, 10, 16), stored.requestedDate)
        assertEquals(Duration.ofHours(2), stored.estimatedDuration)
    }

    @Test
    fun `one utterance can store several independent tasks`() = runBlocking {
        val (vm, speech) = viewModel(
            parser = FakeParser(
                TaskUnderstandingResult.Parsed(
                    listOf(
                        draft(title = "Function", date = LocalDate.of(2026, 10, 25)),
                        draft(title = "Start studying", date = LocalDate.of(2026, 10, 15)),
                    ),
                ),
            ),
        )

        vm.pressToListen()
        speech.send(
            SpeechEvent.Final("I have a function on October 25th, and I need to start studying in two hours"),
        )

        val created = vm.state.value as VoiceUiState.Created
        assertEquals(
            "every task in the utterance must reach the user",
            listOf("Function", "Start studying"),
            created.tasks.map { it.title },
        )
        assertEquals("both are independent records", 2, repository.rows.size)
        assertEquals(LocalDate.of(2026, 10, 25), repository.rows.values.first().requestedDate)
    }

    @Test
    fun `a task the domain rejects does not take the others with it`() = runBlocking {
        val (vm, speech) = viewModel(
            parser = FakeParser(
                TaskUnderstandingResult.Parsed(
                    listOf(draft(title = "Function"), draft(title = "   ")),
                ),
            ),
        )

        vm.pressToListen()
        speech.send(SpeechEvent.Final("a function and something with no words"))

        val created = vm.state.value as VoiceUiState.Created
        assertEquals(listOf("Function"), created.tasks.map { it.title })
        assertTrue("the rejected task must be named, not dropped silently", created.problems.isNotEmpty())
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun `releasing the microphone ends the session and the transcript is processed`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        vm.release()
        assertEquals("a release ends the session politely, keeping what was heard", 1, speech.stopCount)
        assertTrue(vm.state.value is VoiceUiState.Listening)

        speech.send(SpeechEvent.Final("Study for GATE"))

        assertEquals("Study for GATE", (vm.state.value as VoiceUiState.Created).tasks.single().title)
    }

    @Test
    fun `a session that ends without a transcript returns to the microphone`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        vm.release()
        speech.finish()

        assertEquals(VoiceUiState.Idle, vm.state.value)
        assertTrue(repository.rows.isEmpty())
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

        vm.pressToListen()
        speech.send(SpeechEvent.Final("run sometime"))
        val waiting = vm.state.value as VoiceUiState.Clarify
        assertEquals("Morning or afternoon?", waiting.question)
        assertTrue("nothing is stored while a question is open", repository.rows.isEmpty())

        vm.answer(prompt.options[0])

        assertEquals("Run", (vm.state.value as VoiceUiState.Created).tasks.single().title)
    }

    @Test
    fun `corrected fields are what get stored`() = runBlocking {
        val prompt = ClarificationPrompt(
            field = DraftField.TIME_OF_DAY,
            question = "Morning or afternoon?",
            options = listOf(ClarificationOption("Morning", draft(title = "Study"))),
            base = draft(title = "Study"),
        )
        val (vm, speech) = viewModel(parser = FakeParser(TaskUnderstandingResult.NeedsClarification(prompt)))

        vm.pressToListen()
        speech.send(SpeechEvent.Final("study sometime"))
        vm.editTitle("Study for GATE")
        vm.editDate("2026-10-20")
        vm.editDurationMinutes("90")
        vm.create()

        val stored = repository.rows.values.single()
        assertEquals("Study for GATE", stored.title)
        assertEquals(LocalDate.of(2026, 10, 20), stored.requestedDate)
        assertEquals(Duration.ofMinutes(90), stored.estimatedDuration)
    }

    @Test
    fun `an unparsable duration is refused before storage`() = runBlocking {
        val prompt = ClarificationPrompt(
            field = DraftField.DURATION,
            question = "How long?",
            options = listOf(ClarificationOption("One hour", draft(title = "Study"))),
            base = draft(title = "Study"),
        )
        val (vm, speech) = viewModel(parser = FakeParser(TaskUnderstandingResult.NeedsClarification(prompt)))

        vm.pressToListen()
        speech.send(SpeechEvent.Final("study for a while"))
        vm.editDurationMinutes("soon")
        vm.create()

        assertTrue((vm.state.value as VoiceUiState.Clarify).problems.isNotEmpty())
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `an unsupported transcript is refused without offering retry`() = runBlocking {
        val (vm, speech) = viewModel(
            parser = FakeParser(TaskUnderstandingResult.Unsupported(ParseFailureReason.NOT_A_TASK_REQUEST)),
        )

        vm.pressToListen()
        speech.send(SpeechEvent.Final("what should I do next?"))

        val failed = vm.state.value as VoiceUiState.Failed
        assertFalse("repeating the same words would fail the same way", failed.canRetry)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun `an utterance with no task in it asks for another try`() = runBlocking {
        val (vm, speech) = viewModel(parser = FakeParser(TaskUnderstandingResult.Parsed(emptyList())))

        vm.pressToListen()
        speech.send(SpeechEvent.Final("hmm"))

        assertTrue((vm.state.value as VoiceUiState.Failed).canRetry)
    }

    @Test
    fun `a revoked permission cannot be fixed by retrying`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        speech.send(SpeechEvent.Failed(SpeechError.INSUFFICIENT_PERMISSIONS))

        assertFalse((vm.state.value as VoiceUiState.Failed).canRetry)
    }

    @Test
    fun `a speech timeout is retryable`() = runBlocking {
        val (vm, speech) = viewModel()

        vm.pressToListen()
        speech.send(SpeechEvent.Failed(SpeechError.SPEECH_TIMEOUT))

        assertTrue((vm.state.value as VoiceUiState.Failed).canRetry)
    }

    @Test
    fun `dismissing the panel leaves nothing running`() = runBlocking {
        val (vm, _) = viewModel()

        vm.pressToListen()
        vm.dismiss()

        assertEquals(VoiceUiState.Idle, vm.state.value)
    }

    // ---------------------------------------------------------------- fakes

    private class FakeSpeech(
        private val availability: SpeechAvailability = SpeechAvailability.Available,
    ) : SpeechRecognitionAdapter {

        private val events = Channel<SpeechEvent>(Channel.UNLIMITED)
        var sessionCount = 0
            private set
        var stopCount = 0
            private set

        override fun availability(): SpeechAvailability = availability

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> {
            sessionCount++
            return events.receiveAsFlow()
        }

        override fun stop() {
            stopCount++
        }

        fun send(event: SpeechEvent) {
            events.trySend(event)
        }

        /** The platform ending the session without a transcript, as it does on a silent release. */
        fun finish() {
            events.close()
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
            date: LocalDate = LocalDate.of(2026, 10, 16),
        ) = TaskIntentDraft(
            title = title,
            requestedDate = date,
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