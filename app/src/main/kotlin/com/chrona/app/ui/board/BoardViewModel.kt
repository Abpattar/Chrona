package com.chrona.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which representation of the same records is shown (P05 requires a list alternative). */
enum class BoardViewMode { BOARD, LIST }

/**
 * Filter over the board's shared task records.
 *
 * Defaults show open work; the user can widen to closed states, narrow by priority, project,
 * free-text search or a due-on-or-before date.
 */
data class BoardFilter(
    val statuses: Set<TaskStatus> = TaskStatus.entries.filter { it.isOpen }.toSet(),
    val priorities: Set<Priority> = Priority.entries.toSet(),
    val projectLabel: String? = null,
    val search: String = "",
    val dueOnOrBefore: LocalDate? = null,
) {
    fun matches(task: Task): Boolean {
        if (task.status !in statuses) return false
        if (task.priority !in priorities) return false
        if (projectLabel != null && task.projectLabel != projectLabel) return false
        if (dueOnOrBefore != null) {
            // A task is "due by" if either its absolute deadline or its date-only request
            // falls on or before the bound. No deadline at all means it is not due yet.
            val deadlineDate = task.deadline?.toLocalDate()
            val requested = task.requestedDate
            val due = listOfNotNull(deadlineDate, requested).minOrNull() ?: return false
            if (due.isAfter(dueOnOrBefore)) return false
        }
        if (search.isNotBlank()) {
            val needle = search.trim()
            val haystack = task.title + " " + (task.projectLabel ?: "")
            if (!haystack.contains(needle, ignoreCase = true)) return false
        }
        return true
    }
}

/** Everything the Board surface renders. */
data class BoardUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    /** Every loaded record; [visibleTasks] applies the filter on top. */
    val allTasks: List<Task> = emptyList(),
    val relationships: List<TaskRelationship> = emptyList(),
    val filter: BoardFilter = BoardFilter(),
    val viewMode: BoardViewMode = BoardViewMode.BOARD,
    val selectedTaskId: TaskId? = null,
    val selectedRelationId: RelationId? = null,
    /** Plain-language message; never a stack trace. */
    val message: String? = null,
) {
    val visibleTasks: List<Task> get() = allTasks.filter(filter::matches).sortedBy { it.id.value }
    val visibleIds: Set<TaskId> get() = visibleTasks.mapTo(mutableSetOf()) { it.id }

    /** Threads are only drawn when both ends are visible, so no phantom line appears. */
    val visibleRelationships: List<TaskRelationship>
        get() = relationships.filter { it.sourceTaskId in visibleIds && it.targetTaskId in visibleIds }

    val layout: BoardLayout get() = BoardLayoutCalculator.layout(visibleTasks, relationships)

    val projects: List<String>
        get() = allTasks.mapNotNull { it.projectLabel }.distinct().sorted()

    val selectedTask: Task? get() = allTasks.find { it.id == selectedTaskId }
    val selectedRelation: TaskRelationship? get() = relationships.find { it.id == selectedRelationId }

    val titles: Map<TaskId, String> get() = allTasks.associate { it.id to it.title }

    /** Links touching the selected task, for the detail panel. */
    val selectedTaskLinks: List<TaskRelationship>
        get() = selectedTaskId?.let { id ->
            relationships.filter { it.sourceTaskId == id || it.targetTaskId == id }
        } ?: emptyList()
}

/**
 * State holder for the Board (UI-001, UI-002, REL-001, REL-002).
 *
 * It reads the same `TaskRepository` records Today and Schedule read, so the board never
 * duplicates a task — it renders the shared rows. Adding a link validates hard-dependency
 * cycles and self-edges before writing; a suggested edge is stored dormant until confirmed.
 */
class BoardViewModel(
    private val loadTasks: suspend () -> Outcome<List<Task>>,
    private val loadRelationships: suspend () -> Outcome<List<TaskRelationship>>,
    private val addLink: suspend (TaskId, TaskId, RelationType, String?) -> Outcome<TaskRelationship>,
    private val updateLink: suspend (TaskRelationship) -> Outcome<TaskRelationship>,
    private val deleteLink: suspend (RelationId) -> Outcome<Unit>,
    private val confirmLink: suspend (RelationId) -> Outcome<TaskRelationship>,
) : ViewModel() {

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val tasks = when (val result = loadTasks()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val relationships = when (val result = loadRelationships()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    allTasks = tasks,
                    relationships = relationships,
                    message = null,
                )
            }
        }
    }

    fun setViewMode(mode: BoardViewMode) = _state.update { it.copy(viewMode = mode) }

    fun setSearch(text: String) = _state.updateFilter { it.copy(search = text) }

    fun toggleStatus(status: TaskStatus) = _state.updateFilter {
        it.copy(statuses = it.statuses.toggle(status))
    }

    fun togglePriority(priority: Priority) = _state.updateFilter {
        it.copy(priorities = it.priorities.toggle(priority))
    }

    fun setProject(projectLabel: String?) = _state.updateFilter { it.copy(projectLabel = projectLabel) }

    fun setDueOnOrBefore(date: LocalDate?) = _state.updateFilter { it.copy(dueOnOrBefore = date) }

    fun clearFilters() = _state.update { it.copy(filter = BoardFilter()) }

    fun selectTask(taskId: TaskId) = _state.update {
        it.copy(selectedTaskId = taskId, selectedRelationId = null)
    }

    fun selectRelation(relationId: RelationId) = _state.update {
        it.copy(selectedRelationId = relationId, selectedTaskId = null)
    }

    fun clearSelection() = _state.update { it.copy(selectedTaskId = null, selectedRelationId = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /**
     * Links the selected task to [target]. The selected task is the source
     * (`source DEPENDS_ON target` ⇒ source waits for target).
     */
    fun addLinkTo(target: TaskId, type: RelationType, createdBy: RelationCreatedBy = RelationCreatedBy.USER) {
        val source = _state.value.selectedTaskId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = addLink(source, target, type, null).also { outcome ->
                val message = when (outcome) {
                    is Outcome.Success -> "Link added."
                    is Outcome.Failed -> describe(outcome.failure)
                }
                _state.update { it.copy(isWorking = false, message = message) }
            }
            if (result is Outcome.Success) refreshSilently()
        }
    }

    /** Changes a link's type, re-validating against the graph. */
    fun changeLinkType(relationId: RelationId, newType: RelationType) {
        val existing = _state.value.relationships.find { it.id == relationId } ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            when (val result = updateLink(existing.copy(type = newType))) {
                is Outcome.Success -> _state.update { it.copy(isWorking = false, message = "Link updated.") }
                is Outcome.Failed -> _state.update { it.copy(isWorking = false, message = describe(result.failure)) }
            }
            refreshSilently()
        }
    }

    fun removeLink(relationId: RelationId) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val outcome = deleteLink(relationId)
            _state.update {
                it.copy(
                    isWorking = false,
                    selectedRelationId = null,
                    message = when (outcome) {
                        is Outcome.Success -> "Link removed."
                        is Outcome.Failed -> describe(outcome.failure)
                    },
                )
            }
            refreshSilently()
        }
    }

    /** Activates a suggested edge after re-validating that it still keeps the graph acyclic. */
    fun confirmSuggestedLink(relationId: RelationId) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            when (val result = confirmLink(relationId)) {
                is Outcome.Success -> _state.update { it.copy(isWorking = false, message = "Link confirmed.") }
                is Outcome.Failed -> _state.update { it.copy(isWorking = false, message = describe(result.failure)) }
            }
            refreshSilently()
        }
    }

    /** Reload without flashing the loading state, so the board does not jump after an edit. */
    private suspend fun refreshSilently() {
        val tasks = (loadTasks() as? Outcome.Success)?.value
        val relationships = (loadRelationships() as? Outcome.Success)?.value
        _state.update {
            it.copy(
                allTasks = tasks ?: it.allTasks,
                relationships = relationships ?: it.relationships,
            )
        }
    }

    private fun fail(failure: Failure) {
        _state.update { it.copy(isLoading = false, message = describe(failure)) }
    }

    private fun describe(failure: Failure): String = when (failure) {
        is Failure.Validation -> failure.reasons.joinToString("\n")
        Failure.NotFound -> "That is no longer there."
        is Failure.Conflict -> failure.message
        is Failure.StorageFailure -> "Chrona could not reach local storage. Try again."
        else -> "Something went wrong. Try again."
    }

    private inline fun MutableStateFlow<BoardUiState>.updateFilter(
        transform: (BoardFilter) -> BoardFilter,
    ) = update { it.copy(filter = transform(it.filter)) }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    class Factory(
        private val loadTasks: suspend () -> Outcome<List<Task>>,
        private val loadRelationships: suspend () -> Outcome<List<TaskRelationship>>,
        private val addLink: suspend (TaskId, TaskId, RelationType, String?) -> Outcome<TaskRelationship>,
        private val updateLink: suspend (TaskRelationship) -> Outcome<TaskRelationship>,
        private val deleteLink: suspend (RelationId) -> Outcome<Unit>,
        private val confirmLink: suspend (RelationId) -> Outcome<TaskRelationship>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BoardViewModel(
            loadTasks, loadRelationships, addLink, updateLink, deleteLink, confirmLink,
        ) as T
    }
}
