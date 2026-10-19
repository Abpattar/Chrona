package com.chrona.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.recovery.ApplyRecoveryResult
import com.chrona.app.domain.recovery.ScheduleRevisionReason
import com.chrona.app.domain.recovery.UndoResult
import com.chrona.app.domain.scheduling.DayHorizon
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.usecase.BuildDayProposalUseCase
import com.chrona.app.usecase.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Schedule surface renders. A preview is data until the user applies it. */
data class ScheduleUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val proposal: ScheduleProposal? = null,
    val titles: Map<TaskId, String> = emptyMap(),
    val currentRevisionId: RevisionId? = null,
    /** Plain-language message; never a stack trace. */
    val message: String? = null,
    /** The day being planned and the hours the user said they have. Drives the header. */
    val horizon: DayHorizon? = null,
    /** Every open task, so the screen can report what exists rather than only what fitted. */
    val allTasks: List<Task> = emptyList(),
    /** Priority per task, so a block can be labelled without a second lookup. */
    val priorities: Map<TaskId, Priority> = emptyMap(),
    /** Tasks with a hard time, so a fixed block is marked without relying on colour. */
    val fixedTaskIds: Set<TaskId> = emptySet(),
)

/**
 * State holder for Schedule (UI-001, SCHED-001..003).
 *
 * It asks [BuildDayProposalUseCase] for a preview and shows it before anything is written.
 * Applying and undoing are the only mutations, and both go through the revision repository so a
 * stale plan can never overwrite newer work.
 */
class ScheduleViewModel(
    private val buildProposal: suspend () -> Outcome<com.chrona.app.usecase.DayProposalResult>,
    private val apply: suspend (ScheduleProposal, RevisionId?, String) -> ApplyRecoveryResult,
    private val undo: suspend (RevisionId) -> UndoResult,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private var hasLoadedOnce = false

    /**
     * Re-plans when the surface is shown again.
     *
     * The view model outlives the navigation to another destination, so a plan made before a
     * task was created or completed elsewhere would otherwise be offered as if it were current.
     */
    fun refreshOnEntry() {
        if (hasLoadedOnce) refresh() else hasLoadedOnce = true
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            reload()
        }
    }

    /**
     * Re-reads the world and replaces the plan, keeping any existing message.
     *
     * Split from [refresh] so apply and undo can refresh *and then* explain themselves. Doing it
     * the other way round silently discarded the one sentence the user needed.
     */
    private suspend fun reload() {
        val result = when (val built = buildProposal()) {
            is Outcome.Success -> built.value
            is Outcome.Failed -> return fail(built.failure)
        }
        val tasks = result.tasks
        _state.update {
            it.copy(
                isLoading = false,
                proposal = result.proposal,
                titles = tasks.associate { task -> task.id to task.title },
                currentRevisionId = result.currentRevisionId,
                horizon = result.horizon,
                allTasks = tasks,
                priorities = tasks.associate { task -> task.id to task.priority },
                fixedTaskIds = tasks.filter { task -> task.fixedRange != null }.map { task -> task.id }.toSet(),
            )
        }
    }

    fun applyProposal() {
        val proposal = _state.value.proposal ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = apply(proposal, _state.value.currentRevisionId, ScheduleRevisionReason.APPLIED)
            // Refresh first, then report. The other order wiped the explanation the moment the
            // screen reloaded, so a rejected apply — the one case where the user most needs to
            // read why — flashed and vanished.
            reload()
            _state.update { it.copy(isWorking = false, message = result.reason) }
        }
    }

    fun undoLast() {
        val revisionId = _state.value.currentRevisionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = undo(revisionId)
            reload()
            _state.update { it.copy(isWorking = false, message = result.reason) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun fail(failure: com.chrona.app.domain.model.Failure) {
        _state.update { it.copy(isLoading = false, message = describe(failure)) }
    }


    class Factory(
        private val buildProposal: suspend () -> Outcome<com.chrona.app.usecase.DayProposalResult>,
        private val apply: suspend (ScheduleProposal, RevisionId?, String) -> ApplyRecoveryResult,
        private val undo: suspend (RevisionId) -> UndoResult,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(
            buildProposal, apply, undo,
        ) as T
    }
}
