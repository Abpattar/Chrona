package com.chrona.app.platform.speech

import kotlinx.coroutines.flow.Flow

/**
 * The speech boundary (CH-PLAT-001).
 *
 * This interface is the only place in Chrona that knows the Android speech APIs exist. It is a
 * provider-neutral boundary on purpose: a future provider must arrive behind the same contract
 * with an approved change, not as a new dependency inside the UI.
 *
 * One invariant is structural rather than documented: an implementation that honours
 * [SpeechAvailability] cannot reach an off-device recognizer, because the only availability
 * Chrona asks about is on-device availability (VOICE-002, ADR-006).
 */
interface SpeechRecognitionAdapter {

    /**
     * Whether Chrona may listen right now.
     *
     * Anything other than [SpeechAvailability.Available] means typing is the answer. There is
     * no error to recover from, because no listening was ever started.
     */
    fun availability(): SpeechAvailability

    /**
     * One listening session, as a cold flow.
     *
     * Collecting starts the session; cancelling the collector, or the ViewModel being cleared,
     * stops it and releases the platform recognizer. Collecting again starts a fresh session,
     * so a user can speak, cancel and speak again.
     */
    fun listen(request: SpeechRequest): Flow<SpeechEvent>

    /**
     * Ends the running session at the user's request, keeping what has been heard.
     *
     * Distinct from cancelling the collector, which discards the session: a press-and-hold
     * interaction needs the transcript the recogniser has already assembled, so it must be asked
     * to finish rather than cancelled. A no-op when no session is running.
     */
    fun stop()
}

/** What the platform reports before Chrona asks to listen. */
sealed interface SpeechAvailability {

    /** On-device recognition exists and the microphone permission is granted. */
    data object Available : SpeechAvailability

    /**
     * This device has no on-device recognizer.
     *
     * Not an error: the user types instead. Chrona must not fall back to a recognizer that may
     * send audio to a server.
     */
    data object OnDeviceUnavailable : SpeechAvailability

    /** On-device recognition exists, but the microphone permission has not been granted yet. */
    data object PermissionRequired : SpeechAvailability
}

/** What to listen for. Nothing here selects a provider. */
data class SpeechRequest(
    val localeTag: String,
    val maxDurationMillis: Long = DEFAULT_MAX_DURATION_MILLIS,
) {
    companion object {
        const val DEFAULT_MAX_DURATION_MILLIS: Long = 10_000L
    }
}

/** What the recogniser reported during one session. */
sealed interface SpeechEvent {

    /** The recognizer is armed and the user can start speaking. */
    data object Ready : SpeechEvent

    /** Interim words, for live feedback only. Never stored, never treated as a result. */
    data class Partial(val text: String) : SpeechEvent

    /**
     * The final transcript for this session.
     *
     * [confidenceScores] is null whenever the platform does not report per-word scores, which is
     * common for on-device recognition. Chrona does not substitute its own number for it and
     * does not combine the two: the score shown to the user comes from the parser, and it is a
     * structural measure, not a probability.
     */
    data class Final(val text: String, val confidenceScores: List<Float>? = null) : SpeechEvent

    /** The session stopped without a usable transcript. */
    data class Failed(val reason: SpeechError) : SpeechEvent
}

/** Platform failure reasons, kept coarse on purpose: the user needs "try again", not a code. */
enum class SpeechError {
    /** Nothing was recognised. Common with background noise or an accidental tap. */
    NO_MATCH,

    /** No speech was detected in time. */
    SPEECH_TIMEOUT,

    /** The permission was revoked between the check and the session. */
    INSUFFICIENT_PERMISSIONS,

    /** Another session is still finishing. Retry shortly. */
    RECOGNIZER_BUSY,

    /** A client-side error, including a recognizer used in the wrong state. */
    CLIENT,

    LANGUAGE_UNAVAILABLE,

    UNKNOWN,
    ;

    /** Plain-language guidance for the capture screen. */
    val guidance: String
        get() = when (this) {
            NO_MATCH -> "Chrona did not catch that. Tap the microphone and try again."
            SPEECH_TIMEOUT -> "No speech was heard. Tap the microphone and try again."
            INSUFFICIENT_PERMISSIONS -> "Chrona needs the microphone permission to listen."
            RECOGNIZER_BUSY -> "The microphone is still busy. Try again in a moment."
            CLIENT -> "Listening stopped unexpectedly. Tap the microphone and try again."
            LANGUAGE_UNAVAILABLE -> "This device cannot listen in that language. Type the task instead."
            UNKNOWN -> "Listening failed. Type the task instead."
        }
}