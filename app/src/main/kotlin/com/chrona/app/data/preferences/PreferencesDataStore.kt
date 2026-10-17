package com.chrona.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chrona.app.data.repository.PreferencesRepository
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.domain.model.VoiceRecognitionMode
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime

/**
 * The one DataStore for settings (DATA_MODEL_AND_INTERFACES.md §UserPreferences).
 *
 * Preferences are a handful of scalar values, so DataStore fits better than a Room table: no
 * schema migration for each new toggle, and writes are already atomic and crash-safe.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chrona_settings")

/**
 * DataStore-backed [PreferencesRepository].
 *
 * Reads and writes are total: a missing key falls back to the domain default, and an unreadable
 * value (e.g. a hand-edited file) is ignored rather than crashing the app. Enums are stored by
 * name and times as `HH:mm`, so a future enum reorder cannot silently reinterpret stored data.
 */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override suspend fun load(): Outcome<UserPreferences> = try {
        val p = dataStore.data.first()
        Outcome.Success(
            UserPreferences(
                dayStart = p[KEY_DAY_START]?.let(::parseTime),
                dayEnd = p[KEY_DAY_END]?.let(::parseTime),
                reminderStyle = p[KEY_REMINDER_STYLE]?.let(::parseReminderStyle)
                    ?: ReminderStyle.QUIET,
                nonUrgentDailyCap = p[KEY_NON_URGENT_CAP] ?: UserPreferences.DEFAULT_NON_URGENT_DAILY_CAP,
                quietHoursStart = p[KEY_QUIET_START]?.let(::parseTime),
                quietHoursEnd = p[KEY_QUIET_END]?.let(::parseTime),
                overlayEnabled = p[KEY_OVERLAY_ENABLED] ?: false,
                voiceRecognitionMode = p[KEY_VOICE_MODE]?.let(::parseVoiceMode)
                    ?: VoiceRecognitionMode.ON_DEVICE_ONLY,
                reducedMotion = p[KEY_REDUCED_MOTION] ?: false,
                personalizationEnabled = p[KEY_PERSONALIZATION] ?: false,
                updatedAt = p[KEY_UPDATED_AT]?.let(Instant::ofEpochMilli),
            ),
        )
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Outcome.Failed(Failure.StorageFailure(error.message ?: "Settings are unavailable."))
    }

    override suspend fun save(preferences: UserPreferences): Outcome<Unit> = try {
        dataStore.edit { p ->
            writeNullable(p, KEY_DAY_START, preferences.dayStart?.toString())
            writeNullable(p, KEY_DAY_END, preferences.dayEnd?.toString())
            p[KEY_REMINDER_STYLE] = preferences.reminderStyle.name
            p[KEY_NON_URGENT_CAP] = preferences.nonUrgentDailyCap
            writeNullable(p, KEY_QUIET_START, preferences.quietHoursStart?.toString())
            writeNullable(p, KEY_QUIET_END, preferences.quietHoursEnd?.toString())
            p[KEY_OVERLAY_ENABLED] = preferences.overlayEnabled
            p[KEY_VOICE_MODE] = preferences.voiceRecognitionMode.name
            p[KEY_REDUCED_MOTION] = preferences.reducedMotion
            p[KEY_PERSONALIZATION] = preferences.personalizationEnabled
            writeNullable(p, KEY_UPDATED_AT, preferences.updatedAt?.toEpochMilli())
        }
        Outcome.Success(Unit)
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Outcome.Failed(Failure.StorageFailure(error.message ?: "Settings could not be saved."))
    }

    private fun writeNullable(
        p: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) p.remove(key) else p[key] = value
    }

    private fun writeNullable(
        p: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<Long>,
        value: Long?,
    ) {
        if (value == null) p.remove(key) else p[key] = value
    }

    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()

    private fun parseReminderStyle(raw: String): ReminderStyle? =
        runCatching { ReminderStyle.valueOf(raw) }.getOrNull()

    private fun parseVoiceMode(raw: String): VoiceRecognitionMode? =
        runCatching { VoiceRecognitionMode.valueOf(raw) }.getOrNull()

    private companion object {
        val KEY_DAY_START = stringPreferencesKey("day_start")
        val KEY_DAY_END = stringPreferencesKey("day_end")
        val KEY_REMINDER_STYLE = stringPreferencesKey("reminder_style")
        val KEY_NON_URGENT_CAP = intPreferencesKey("non_urgent_daily_cap")
        val KEY_QUIET_START = stringPreferencesKey("quiet_hours_start")
        val KEY_QUIET_END = stringPreferencesKey("quiet_hours_end")
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_VOICE_MODE = stringPreferencesKey("voice_recognition_mode")
        val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val KEY_PERSONALIZATION = booleanPreferencesKey("personalization_enabled")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at_epoch_millis")
    }
}
