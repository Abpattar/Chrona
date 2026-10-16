package com.chrona.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.recovery.ApplyRecoveryResult
import com.chrona.app.domain.recovery.ScheduleRevisionReason
import com.chrona.app.domain.recovery.UndoResult
import com.chrona.app.domain.scheduling.ScheduleInput
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import com.chrona.app.domain.scheduling.FixedCommitment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/** Everything the Schedule surface renders. A preview is data until the user applies it. */
data class ScheduleUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val proposal: ScheduleProposal? = null,
    val titles: Map<TaskId, String> = emptyMap(),
    val currentRevisionId: RevisionId? = null,
    /** Plain-language message; never a stack trace. */
    val message: String? = null,
)

/**
 * State holder for Schedule (UI-001, SCHED-001..003).
 *
 * It loads the current world, asks the deterministic engine for a preview, and shows it before
 * anything is written. Applying and undoing are the only mutations, and both go through the
 * revision repository so a stale plan can never overwrite newer work.
 */
class ScheduleViewModel(
    private val loadTasks: suspend () -> Outcome<List<Task>>,
    private val loadCommitments: suspend (ZonedDateTime, ZonedDateTime) -> Outcome<List<CalendarEvent>>,
    private val loadRelationships: suspend () -> Outcome<List<TaskRelationship>>,
    private val currentRevision: suspend () -> Outcome<ScheduleRevision?>,
    private val plan: suspend (ScheduleInput) -> ScheduleProposal,
    private val apply: suspend (ScheduleProposal, RevisionId?, String) -> ApplyRecoveryResult,
    private val undo: suspend (RevisionId) -> UndoResult,
    private val now: () -> ZonedDateTime,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val reference = now()
            val dayStart = reference.toLocalDate().atStartOfDay(reference.zone)
            val dayEnd = dayStart.plusDays(1)

            val tasks = when (val result = loadTasks()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val commitments = when (val result = loadCommitments(dayStart, dayEnd)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val relationships = when (val result = loadRelationships()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val revision = when (val result = currentRevision()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }

            val input = ScheduleInput(
                tasks = tasks,
                fixedCommitments = commitments.map {
                    FixedCommitment(it.id.value, it.title, it.range)
                },
                dayStart = dayStart,
                dayEnd = dayEnd,
                relationships = relationships,
                now = reference,
                buffer = Duration.ofMinutes(10),
                previousPlacements = revision?.placements ?: emptyList(),
                basedOnRevisionId = revision?.id,
            )
            val proposal = plan(input)
            _state.update {
                it.copy(
                    isLoading = false,
                    proposal = proposal,
                    titles = tasks.associate { task -> task.id to task.title },
                    currentRevisionId = revision?.id,
                    message = null,
                )
            }
        }
    }

    fun applyProposal() {
        val proposal = _state.value.proposal ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = apply(proposal, _state.value.currentRevisionId, ScheduleRevisionReason.APPLIED)
            _state.update { it.copy(isWorking = false, message = result.reason) }
            refresh()
        }
    }

    fun undoLast() {
        val revisionId = _state.value.currentRevisionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = undo(revisionId)
            _state.update { it.copy(isWorking = false, message = result.reason) }
            refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun fail(failure: Failure) {
        _state.update { it.copy(isLoading = false, message = describe(failure)) }
    }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString("\n")
        Failure.NotFound -> "That no longer exists."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not read local storage. Try again."
        else -> "Something went wrong. Try again."
    }

    class Factory(
        private val loadTasks: suspend () -> Outcome<List<Task>>,
        private val loadCommitments: suspend (ZonedDateTime, ZonedDateTime) -> Outcome<List<CalendarEvent>>,
        private val loadRelationships: suspend () -> Outcome<List<TaskRelationship>>,
        private val currentRevision: suspend () -> Outcome<ScheduleRevision?>,
        private val plan: suspend (ScheduleInput) -> ScheduleProposal,
        private val apply: suspend (ScheduleProposal, RevisionId?, String) -> ApplyRecoveryResult,
        private val undo: suspend (RevisionId) -> UndoResult,
        private val now: () -> ZonedDateTime,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(
            loadTasks, loadCommitments, loadRelationships, currentRevision, plan, apply, undo, now,
        ) as T
    }
}
