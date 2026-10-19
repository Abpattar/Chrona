package com.chrona.app.ui.taskbrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.task.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Loads every task Chrona holds and joins it with the current plan.
 *
 * The join is what makes the browser honest: a task row can say *when* it is scheduled, or, when
 * it has no slot, *why*. Without joining the proposal the list would show half the truth — that
 * a task exists — and stop short of the half the user actually needs.
 */
class TaskBrowserViewModel(
    private val loadTasks: suspend (TaskQuery) -> Outcome<List<Task>>,
    private val loadProposal: suspend () -> Outcome<ScheduleProposal?>,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskBrowserUiState())
    val state: StateFlow<TaskBrowserUiState> = _state.asStateFlow()

    private var hasLoadedOnce = false

    // The route calls refreshOnEntry(), which deliberately skips the first call because every
    // screen is expected to load itself on construction. Without this the browser and History
    // would sit on "Loading…" forever on first open.
    init {
        refresh()
    }
    /** Re-reads when the surface is shown again, so an edit made elsewhere is visible here. */
    fun refreshOnEntry() {
        if (hasLoadedOnce) refresh() else hasLoadedOnce = true
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Closed states are included on purpose: "Completed" is a filter, and a task the
            // user finished should still be findable.
            // Every task, real and simulated, in one list: the browser is where a user goes to
            // find the 142 simulated tasks, and each row says which is which.
            val tasks = when (val result = loadTasks(TaskQuery.EVERYTHING)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val proposal = when (val result = loadProposal()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }

            val placements = proposal?.placements.orEmpty().groupBy { it.taskId }
            val unplaced = proposal?.unplaced.orEmpty().associateBy { it.taskId }
            val deadlineFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm")

            val rows = tasks.map { task ->
                val sessions = placements[task.id].orEmpty().sortedBy { it.start }
                TaskBrowserRow(
                    id = task.id.value,
                    title = task.title,
                    status = task.status,
                    priority = task.priority,
                    isFixed = task.fixedRange != null,
                    origin = task.origin,
                    deadline = task.deadline?.format(deadlineFormat),
                    scheduledStart = sessions.firstOrNull()?.start,
                    scheduledEnd = sessions.maxOfOrNull { it.end },
                    unscheduledReason = if (sessions.isEmpty()) {
                        unplaced[task.id]?.let { entry ->
                            "${com.chrona.app.ui.schedule.reasonText(entry)}"
                        } ?: if (task.status.isOpen) "Not scheduled yet." else null
                    } else {
                        null
                    },
                    isSplit = sessions.size > 1,
                )
            }.sortedWith(
                // Open work first, then by deadline, then by id: a total order, so the list never
                // reshuffles between two identical reads.
                compareBy<TaskBrowserRow> { if (it.status.isOpen) 0 else 1 }
                    .thenBy { it.scheduledStart }
                    .thenBy { it.id },
            )

            _state.update {
                it.copy(
                    isLoading = false,
                    rows = rows,
                    totalCount = rows.size,
                    completedCount = rows.count { row -> row.status == com.chrona.app.domain.model.TaskStatus.COMPLETED },
                    unscheduledCount = rows.count { row -> row.unscheduledReason != null },
                    message = null,
                )
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onFilterChange(filter: TaskFilter) = _state.update { it.copy(filter = filter) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun fail(failure: Failure) {
        _state.update {
            it.copy(isLoading = false, message = describe(failure))
        }
    }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString("\n")
        Failure.NotFound -> "That task no longer exists."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not read local storage. Try again."
        else -> "Something went wrong. Try again."
    }

    class Factory(
        private val loadTasks: suspend (TaskQuery) -> Outcome<List<Task>>,
        private val loadProposal: suspend () -> Outcome<ScheduleProposal?>,
        private val zoneId: ZoneId,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskBrowserViewModel(loadTasks, loadProposal, zoneId) as T
    }
}
