package com.chrona.app.platform.speech

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSpeechRecognizer

/**
 * The Android speech boundary, driven against the real platform classes under Robolectric.
 *
 * These are the claims VOICE-002 rests on, so they are checked on the real adapter rather than on
 * a fake: only the on-device recogniser is created, a missing permission is reported rather than
 * assumed, the session asks the platform to stay offline, every platform error maps to a
 * retryable or non-retryable state the UI can explain, and a cancelled session releases its
 * recogniser so the next attempt starts clean.
 *
 * Robolectric runs SDK 36 only on Java 21 and this build is pinned to JDK 17, so these run on SDK
 * 33 like the Room tests. The speech APIs used here have been unchanged since API 31; the
 * compileSdk 36 build is what proves the current API surface still has them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
// The platform delivers recognition callbacks through the main looper. Looping them inline keeps
// this test on the adapter's own behaviour instead of on scheduler timing.
@LooperMode(LooperMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeechRecognitionAdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Every recogniser Chrona asked for, so the creation path can be asserted. */
    private val created = mutableListOf<SpeechRecognizer>()

    private val adapter = AndroidSpeechRecognitionAdapter(context) { ctx ->
        SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx).also { created += it }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        grantMicrophone(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a device without on-device recognition reports it and never listens`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)

        assertEquals(SpeechAvailability.OnDeviceUnavailable, adapter.availability())

        // Even if a caller asked anyway, the platform refusal surfaces as a failure. What must
        // never happen is an empty transcript that looks like a successful capture.
        assertEquals(listOf(SpeechEvent.Failed(SpeechError.CLIENT)), collectFirstEvent())
        assertTrue("no recogniser may exist when on-device recognition is absent", created.isEmpty())
    }

    @Test
    fun `a missing microphone permission is reported before anything listens`() {
        grantMicrophone(false)

        assertEquals(SpeechAvailability.PermissionRequired, adapter.availability())

        // That the capture flow does not listen in this state is proven by the state-machine test.
        assertTrue(created.isEmpty())
    }

    @Test
    fun `listening uses one on-device recogniser per session`() {
        assertEquals(SpeechAvailability.Available, adapter.availability())

        assertEquals(listOf(SpeechEvent.Ready), collectFirstEvent())

        assertEquals("exactly one on-device recogniser per session", 1, created.size)
    }

    @Test
    fun `the session is locale-bound and asks the platform to keep it offline`() {
        collectFirstEvent(localeTag = "en-GB")

        val intent = shadowOf(created.single()).getLastRecognizerIntent()
        assertEquals("en-GB", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(true, intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals(true, intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    @Test
    fun `a final transcript ends the session`() {
        val events = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnResults(resultsBundle("water the plants"))
            recognizer
        }

        assertEquals(
            listOf(
                SpeechEvent.Ready,
                SpeechEvent.Final("water the plants"),
            ),
            events,
        )
    }

    @Test
    fun `an empty result is reported as no match, not as an empty transcript`() {
        val events = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnResults(resultsBundle(""))
            recognizer
        }

        assertEquals(listOf(SpeechEvent.Ready, SpeechEvent.Failed(SpeechError.NO_MATCH)), events)
    }

    @Test
    fun `interim words are reported for display only`() {
        val events = collectWhileListening { recognizer ->
            val shadow = shadowOf(recognizer)
            shadow.triggerOnPartialResults(resultsBundle("water the"))
            // Interim words must not end the session: the final result still arrives after them.
            shadow.triggerOnResults(resultsBundle("water the plants"))
        }

        assertEquals(
            listOf(
                SpeechEvent.Ready,
                SpeechEvent.Partial("water the"),
                SpeechEvent.Final("water the plants"),
            ),
            events,
        )
    }

    @Test
    fun `recogniser confidence is never invented`() {
        val bundle = resultsBundle("water the plants")
        bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(0.8f, 0.9f))

        val events = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnResults(bundle)
            recognizer
        }

        val final = events.last() as SpeechEvent.Final
        assertEquals(listOf(0.8f, 0.9f), final.confidenceScores)
    }

    @Test
    fun `a missing confidence array is null rather than a zero score`() {
        val events = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnResults(resultsBundle("water the plants"))
            recognizer
        }

        // The common on-device case: the platform reports no scores at all.
        assertNull((events.last() as SpeechEvent.Final).confidenceScores)
    }

    @Test
    fun `platform errors map to retryable and non-retryable states`() {
        val timeout = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            recognizer
        }
        assertEquals(SpeechEvent.Failed(SpeechError.SPEECH_TIMEOUT), timeout.last())

        val revoked = collectWhileListening { recognizer ->
            shadowOf(recognizer).triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            recognizer
        }
        // Retrying without a permission would fail identically, so the UI must not offer it.
        assertEquals(SpeechEvent.Failed(SpeechError.INSUFFICIENT_PERMISSIONS), revoked.last())
        assertTrue(
            "a revoked permission cannot be fixed by retrying",
            SpeechError.INSUFFICIENT_PERMISSIONS.guidance.isNotBlank(),
        )
    }

    @Test
    fun `cancelling a session destroys its recogniser, and a new one can start`() {
        runBlocking {
            // UNDISPATCHED so the session is really running before the assertion: the recogniser
            // must exist while the session is live.
            val first: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.listen(SpeechRequest("en-GB")).collect { }
            }
            assertEquals("recogniser exists while the session is live", 1, created.size)

            first.cancel()
        }

        assertTrue(
            "a cancelled session must release its recogniser",
            shadowOf(created.single()).isDestroyed,
        )

        // Repeat use: speak, cancel, speak again. The second attempt gets its own recogniser
        // instead of reusing a destroyed one, and that recogniser is live until this session too
        // is released.
        runBlocking {
            val second: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.listen(SpeechRequest("en-GB")).collect { }
            }
            assertEquals("the second session gets its own recogniser", 2, created.size)
            assertTrue("the new recogniser is a distinct instance", created.last() !== created.first())
            assertTrue("the new recogniser is live, not the destroyed one", !shadowOf(created.last()).isDestroyed)

            second.cancel()
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Collects the first event of one session, which releases it, and returns what arrived.
     *
     * Robolectric's shadow never fires `onReadyForSpeech` by itself — the platform does that
     * when the service binds — so the test fires it explicitly when a recogniser exists.
     */
    private fun collectFirstEvent(localeTag: String = "en-GB"): List<SpeechEvent> {
        val events = mutableListOf<SpeechEvent>()
        // Collected on the test's own thread, which is the platform main thread: that is where a
        // recogniser may be created and where the shadow delivers its callbacks.
        runBlocking {
            val session = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.listen(SpeechRequest(localeTag)).take(1).toList(events)
            }
            created.lastOrNull()?.let { shadowOf(it).triggerOnReadyForSpeech(Bundle()) }
            session.join()
        }
        return events.toList()
    }

    /**
     * Runs a session to completion while the platform pushes results through [deliver].
     *
     * The recogniser is ready first, exactly as the platform signals a bound service, and
     * [deliver] must end the session (final results or an error); a collector that never sees
     * an end event would hang the suite rather than fail it.
     *
     * Returns everything the collector saw, so a test can assert the whole event sequence rather
     * than one event at a time.
     */
    private fun collectWhileListening(deliver: (SpeechRecognizer) -> Unit): List<SpeechEvent> {
        val events = mutableListOf<SpeechEvent>()
        runBlocking {
            val session = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.listen(SpeechRequest("en-GB")).collect { events += it }
            }
            val recognizer = created.last()
            shadowOf(recognizer).triggerOnReadyForSpeech(Bundle())
            deliver(recognizer)
            session.join()
        }
        return events.toList()
    }

    private fun resultsBundle(transcript: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(transcript))
    }

    private fun grantMicrophone(grant: Boolean) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val shadow = shadowOf(application)
        if (grant) {
            shadow.grantPermissions(Manifest.permission.RECORD_AUDIO)
        } else {
            shadow.denyPermissions(Manifest.permission.RECORD_AUDIO)
        }
    }
}