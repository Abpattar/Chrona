package com.chrona.app.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Reminder personality. QUIET is the default at first launch (MASTER_REQUIREMENTS §3). */
enum class ReminderStyle { QUIET, BALANCED, MORE_GUIDANCE }

/**
 * A local-clock quiet-hours window (NOTIFY-001).
 *
 * Unlike [TimeWindow] this may wrap past midnight (`start > end`), because quiet hours
 * commonly do: 21:00–07:00 is one span, not an error. `start == end` is rejected rather than
 * guessed: an empty window and a 24-hour window are opposites and must never be confused.
 */
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start != end) { "Quiet hours start and end must differ" }
    }

    /** True when the local [time] falls inside the window, wrapping midnight if needed. */
    fun contains(time: LocalTime): Boolean =
        if (start < end) time >= start && time < end else time >= start || time < end

    /**
     * The next instant the window closes, given a time already inside it.
     *
     * Deterministic: only depends on [now]'s date, time and zone.
     */
    fun nextEndAfter(now: ZonedDateTime): ZonedDateTime {
        val endAt = now.toLocalDate().atTime(end).atZone(now.zone)
        return if (now.toLocalTime() < end) endAt else endAt.plusDays(1)
    }
}

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

    /** The configured quiet-hours window, or null when the user has not set one. */
    val quietHours: QuietHours?
        get() = if (quietHoursStart != null && quietHoursEnd != null) {
            QuietHours(quietHoursStart, quietHoursEnd)
        } else {
            null
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