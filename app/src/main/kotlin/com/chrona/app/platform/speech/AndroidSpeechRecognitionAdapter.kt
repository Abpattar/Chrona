package com.chrona.app.platform.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * On-device speech capture for Android (VOICE-001, VOICE-002).
 *
 * Two rules are enforced by construction:
 *
 * 1. **No off-device fallback.** The only recogniser this class creates is
 *    `createOnDeviceSpeechRecognizer`, and the only availability it consults is
 *    `isOnDeviceRecognitionAvailable`. There is no branch that reaches
 *    `createSpeechRecognizer`, so a device without on-device recognition simply types.
 * 2. **Nothing is retained.** Audio is never buffered or written by Chrona; the recogniser is
 *    handed to the platform and the transcript is returned to the caller. Releasing the session
 *    calls `destroy()` (DATA_MODEL_AND_INTERFACES.md §"Voice states are explicit").
 *
 * [createOnDeviceRecognizer] is a constructor parameter purely so tests can observe which
 * creation path is taken; production always uses the platform factory.
 */
class AndroidSpeechRecognitionAdapter(
    context: Context,
    private val createOnDeviceRecognizer: (Context) -> SpeechRecognizer = { context ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            // Unreachable in production: availability() reports OnDeviceUnavailable below
            // API 31, so listening never starts. Thrown rather than faked, and caught by
            // listen() as a platform failure if this invariant is ever broken.
            throw IllegalStateException("On-device speech recognition requires API 31+")
        }
    },
) : SpeechRecognitionAdapter {

    private val appContext: Context = context.applicationContext

    /**
     * The recogniser of the running session, so [stop] can end it politely.
     *
     * Written only from the main thread, which is the thread `listen` runs on and the thread
     * Compose calls this from, so no synchronisation is needed.
     */
    private var activeRecognizer: SpeechRecognizer? = null

    override fun availability(): SpeechAvailability = when {
        !onDeviceRecognitionAvailable() -> SpeechAvailability.OnDeviceUnavailable
        !hasMicrophonePermission() -> SpeechAvailability.PermissionRequired
        else -> SpeechAvailability.Available
    }

    // The whole session runs on the main thread, which is what the platform recogniser
    // requires. Doing it here rather than at each call site means creation, listening and
    // release can never land on different threads.
    override fun listen(request: SpeechRequest): Flow<SpeechEvent> = callbackFlow {
        val recognizer = try {
            createOnDeviceRecognizer(appContext)
        } catch (error: RuntimeException) {
            // A recogniser can refuse to be created when the service is missing or bound by
            // another session. That is a platform failure, not a crash the user should see.
            trySend(SpeechEvent.Failed(SpeechError.CLIENT))
            close()
            return@callbackFlow
        }

        var released = false
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.bestTranscript()?.takeIf { it.isNotBlank() }?.let {
                    trySend(SpeechEvent.Partial(it))
                }
            }

            override fun onResults(results: Bundle?) {
                val transcript = results?.bestTranscript().orEmpty()
                if (transcript.isBlank()) {
                    trySend(SpeechEvent.Failed(SpeechError.NO_MATCH))
                } else {
                    trySend(SpeechEvent.Final(transcript, results?.confidenceScores()))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(error.toSpeechError()))
                close()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onEndOfSpeech() = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            // Added to the platform interface after this code was written: segmented-session
            // results. Chrona waits for the single final result instead, so these are ignored.
            override fun onSegmentResults(segmentResults: Bundle) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            // Asks the platform to keep the session local. The recogniser itself is already the
            // on-device one; this only removes any ambiguity for OEM implementations.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MILLIS)
        }

        recognizer.setRecognitionListener(listener)
        activeRecognizer = recognizer
        recognizer.startListening(intent)

        awaitClose {
            if (released) return@awaitClose
            released = true
            activeRecognizer = null
            // Cancel first so a live session stops recording, then destroy to release the
            // platform service. Both are guarded: the session may already have ended, and a
            // recogniser must never be destroyed twice.
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }.flowOn(Dispatchers.Main.immediate)

    /**
     * Ends the running session without throwing away its transcript.
     *
     * `stopListening` asks the platform for the result it has so far, which is what a
     * press-and-hold release needs; cancelling the flow instead would discard it.
     */
    override fun stop() {
        val recognizer = activeRecognizer ?: return
        runCatching { recognizer.stopListening() }
    }

    private fun onDeviceRecognitionAvailable(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        } else {
            // On-device recognition was added in API 31; older devices type instead (ADR-006).
            false
        }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun Bundle.bestTranscript(): String =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }
            .orEmpty()

    /**
     * Per-word scores, when the platform reports them.
     *
     * Many on-device recognisers omit them, and an empty array is not a zero-confidence result:
     * both cases become null so callers never mistake "unavailable" for "no confidence".
     */
    private fun Bundle.confidenceScores(): List<Float>? =
        getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.toList()?.takeIf { it.isNotEmpty() }

    private fun Int.toSpeechError(): SpeechError = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.INSUFFICIENT_PERMISSIONS
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.LANGUAGE_UNAVAILABLE
        SpeechRecognizer.ERROR_CLIENT -> SpeechError.CLIENT
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_NETWORK ->
            // Not expected from an on-device recognizer. Reported plainly rather than hidden:
            // if a device ever routes audio to a server, Chrona must not pretend otherwise.
            SpeechError.CLIENT

        else -> SpeechError.UNKNOWN
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val SILENCE_MILLIS = 1_500L
    }
}