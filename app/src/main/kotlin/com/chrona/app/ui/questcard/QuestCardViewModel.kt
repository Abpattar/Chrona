package com.chrona.app.ui.questcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import com.chrona.app.usecase.ReminderEvaluation
import com.chrona.app.usecase.reminderCardId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the in-app Quest Card surface renders. One card at a time keeps the decision clear. */
data class QuestCardUiState(
    val isLoading: Boolean = true,
    val card: ReminderCard? = null,
    val message: String? = null,
)

/**
 * State holder for the in-app Quest Card surface (CH-UI-001).
 *
 * It reuses the same evaluation and the same action use case as the background reconciler, so a
 * card acted on in-app behaves exactly like one acted on in a notification. Showing a card here
 * is recorded as `SHOWN` on the in-app surface, which — because the card id is shared — never
 * inflates the non-urgent daily cap.
 */
class QuestCardViewModel(
    private val evaluate: suspend (notificationAvailable: Boolean, overlayAvailable: Boolean) -> Outcome<ReminderEvaluation>,
    private val recordSurfaced: suspend (ReminderCard, ReminderSurfaceType) -> Outcome<Unit>,
    private val applyAction: suspend (TaskId, TaskAction, ReminderSurfaceType, String?) -> Outcome<Task?>,
    private val notificationsAvailable: suspend () -> Boolean,
    private val overlayAvailable: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestCardUiState())
    val state: StateFlow<QuestCardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val evaluation = when (
                val result = evaluate(notificationsAvailable(), overlayAvailable())
            ) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> {
                    _state.update { it.copy(isLoading = false, message = describe(result.failure)) }
                    return@launch
                }
            }
            val decision = evaluation.actionable.firstOrNull()
            if (decision == null) {
                _state.update { it.copy(isLoading = false, card = null, message = null) }
                return@launch
            }
            val card = ReminderCard(
                cardId = reminderCardId(decision),
                decision = decision,
                tasks = evaluation.tasksFor(decision),
            )
            recordSurfaced(card, ReminderSurfaceType.IN_APP)
            _state.update { it.copy(isLoading = false, card = card, message = null) }
        }
    }

    fun act(action: TaskAction) {
        val card = _state.value.card ?: return
        val primary = card.tasks.firstOrNull() ?: return
        viewModelScope.launch {
            when (val result = applyAction(primary.id, action, ReminderSurfaceType.IN_APP, card.cardId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(message = successMessage(action)) }
                    refresh()
                }

                is Outcome.Failed -> _state.update { it.copy(message = describe(result.failure)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun successMessage(action: TaskAction): String = when (action) {
        TaskAction.START -> "Started."
        TaskAction.DONE -> "Done."
        // LATER with no replacement time is an explicit unscheduled outcome, never a silent move.
        TaskAction.LATER -> "Postponed. It is unscheduled until you pick a time."
        TaskAction.DISMISS -> "Dismissed. The task is untouched."
    }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString(separator = "\n")
        Failure.NotFound -> "That task no longer exists."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not read local storage. Try again."
        is Failure.PermissionDenied -> "Chrona needs permission for that."
        is Failure.Unsupported -> failure.reason
        is Failure.PlatformUnavailable -> failure.reason
        is Failure.RequiresUserChoice -> failure.question
        Failure.Cancelled -> "Cancelled."
        is Failure.UnknownFailure -> "Something went wrong. Try again."
    }

    class Factory(
        private val evaluate: suspend (Boolean, Boolean) -> Outcome<ReminderEvaluation>,
        private val recordSurfaced: suspend (ReminderCard, ReminderSurfaceType) -> Outcome<Unit>,
        private val applyAction: suspend (TaskId, TaskAction, ReminderSurfaceType, String?) -> Outcome<Task?>,
        private val notificationsAvailable: suspend () -> Boolean,
        private val overlayAvailable: suspend () -> Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QuestCardViewModel(
            evaluate, recordSurfaced, applyAction, notificationsAvailable, overlayAvailable,
        ) as T
    }
}
