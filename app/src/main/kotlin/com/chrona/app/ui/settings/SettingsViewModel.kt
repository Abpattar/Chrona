package com.chrona.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Everything the Settings screen renders. Permission booleans are current, not remembered. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val notificationsAvailable: Boolean = false,
    val overlayAvailable: Boolean = false,
    val message: String? = null,
)

/**
 * State holder for Settings (CH-DATA-003, NOTIFY-001).
 *
 * It reads and writes [UserPreferences] through the repository and never touches DataStore or the
 * permission APIs directly. Each edit saves the whole object, so a change cannot half-apply.
 */
class SettingsViewModel(
    private val loadPreferences: suspend () -> Outcome<UserPreferences>,
    private val savePreferences: suspend (UserPreferences) -> Outcome<Unit>,
    private val notificationsAvailable: suspend () -> Boolean,
    private val overlayAvailable: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val preferences = when (val result = loadPreferences()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> {
                    _state.update { it.copy(isLoading = false, message = describe(result.failure)) }
                    return@launch
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    preferences = preferences,
                    notificationsAvailable = notificationsAvailable(),
                    overlayAvailable = overlayAvailable(),
                    message = null,
                )
            }
        }
    }

    fun setStyle(style: ReminderStyle) = edit { it.copy(reminderStyle = style) }

    fun setCap(cap: Int) = edit { it.copy(nonUrgentDailyCap = cap.coerceAtLeast(0)) }

    fun setQuietHours(start: LocalTime?, end: LocalTime?) =
        edit { it.copy(quietHoursStart = start, quietHoursEnd = end) }

    fun setOverlayEnabled(enabled: Boolean) = edit { it.copy(overlayEnabled = enabled) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun edit(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val updated = transform(_state.value.preferences)
            _state.update { it.copy(preferences = updated) }
            when (val result = savePreferences(updated)) {
                is Outcome.Success -> {
                    // The overlay flag and a freshly granted permission are both reflected here.
                    _state.update {
                        it.copy(
                            notificationsAvailable = notificationsAvailable(),
                            overlayAvailable = overlayAvailable(),
                            message = null,
                        )
                    }
                }

                is Outcome.Failed -> _state.update { it.copy(message = describe(result.failure)) }
            }
        }
    }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString(separator = "\n")
        Failure.NotFound -> "That item no longer exists."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not reach local storage. Try again."
        is Failure.PermissionDenied -> "Chrona needs permission for that."
        is Failure.Unsupported -> failure.reason
        is Failure.PlatformUnavailable -> failure.reason
        is Failure.RequiresUserChoice -> failure.question
        Failure.Cancelled -> "Cancelled."
        is Failure.UnknownFailure -> "Something went wrong. Try again."
    }

    class Factory(
        private val loadPreferences: suspend () -> Outcome<UserPreferences>,
        private val savePreferences: suspend (UserPreferences) -> Outcome<Unit>,
        private val notificationsAvailable: suspend () -> Boolean,
        private val overlayAvailable: suspend () -> Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
            loadPreferences, savePreferences, notificationsAvailable, overlayAvailable,
        ) as T
    }
}
