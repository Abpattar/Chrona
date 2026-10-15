package com.chrona.app.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Reminder personality. QUIET is the default at first launch (MASTER_REQUIREMENTS §3). */
enum class ReminderStyle { QUIET, BALANCED, MORE_GUIDANCE }

/**
 * Local user preferences (DATA_MODEL_AND_INTERFACES.md §UserPreferences).
 *
 * Stored separately from the relational task graph. Note what is deliberately absent:
 * no account, no sync, no server identifiers.
 */
data class UserPreferences(
    val dayStart: LocalTime? = null,
    val dayEnd: LocalTime? = null,
    /** User-confirmed availability; the scheduler only places inside these windows. */
    val availabilityWindows: List<TimeWindow> = emptyList(),
    val reminderStyle: ReminderStyle = ReminderStyle.QUIET,
    /**
     * Editable research hypothesis, not a universal constant. Defaults to two per local
     * day and must stay user-configurable (NOTIFY-001, AT-021).
     */
    val nonUrgentDailyCap: Int = DEFAULT_NON_URGENT_DAILY_CAP,
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,
    /** Opt-in only; no overlay permission is requested before the user enables it. */
    val overlayEnabled: Boolean = false,
    /** Default refuses any recognizer that may send audio off-device. */
    val voiceRecognitionMode: VoiceRecognitionMode = VoiceRecognitionMode.ON_DEVICE_ONLY,
    val reducedMotion: Boolean = false,
    /** Deferred until the core loop is measured. Never used for inference in MVP. */
    val personalizationEnabled: Boolean = false,
    val updatedAt: Instant? = null,
) {
    init {
        require(nonUrgentDailyCap >= 0) { "nonUrgentDailyCap must not be negative" }
    }

    companion object {
        const val DEFAULT_NON_URGENT_DAILY_CAP: Int = 2
    }
}

/** VOICE-002: the MVP default refuses any recognizer that may transmit audio off-device. */
enum class VoiceRecognitionMode {
    /** Only `createOnDeviceSpeechRecognizer`, and only when the platform reports support. */
    ON_DEVICE_ONLY,

    /**
     * Explicitly offered by the user after plain disclosure that the system recognizer may
     * process audio off-device. Never selected silently by Chrona.
     */
    DISCLOSED_SYSTEM_RECOGNIZER,
}