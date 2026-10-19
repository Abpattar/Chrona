package com.chrona.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.scheduling.ScheduleRevision
import com.chrona.app.usecase.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** One applied schedule revision, as History shows it. */
data class RevisionEntry(
    val id: String,
    val reasonCode: String,
    val appliedAt: Instant,
    val blockCount: Int,
    val replaces: String?,
)

/** One status change, as History shows it. */
data class TaskHistoryEntry(
    val taskId: String,
    val taskTitle: String,
    val fromStatus: String?,
    val toStatus: String,
    val reasonCode: String,
    val changedAt: Instant,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val revisions: List<RevisionEntry> = emptyList(),
    val taskEntries: List<TaskHistoryEntry> = emptyList(),
    val currentRevisionId: String? = null,
    val completedCount: Int = 0,
    val skippedCount: Int = 0,
    val archivedCount: Int = 0,
    val message: String? = null,
)

/**
 * Reads the two real histories Chrona keeps: applied schedule revisions and task status changes.
 *
 * Both come from the same tables production writes to. There is no placeholder path here: if the
 * screen is empty, it is because nothing has happened yet, and the empty state says exactly
 * that rather than promising data that does not exist.
 */
class HistoryViewModel(
    private val loadRevisions: suspend () -> Outcome<List<ScheduleRevision>>,
    private val loadStatusLog: suspend (Int) -> Outcome<List<TaskHistoryEntry>>,
    private val loadClosedTasks: suspend (TaskQuery) -> Outcome<List<com.chrona.app.domain.model.Task>>,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var hasLoadedOnce = false

    // The route calls refreshOnEntry(), which deliberately skips the first call because every
    // screen is expected to load itself on construction. Without this the browser and History
    // would sit on "Loading…" forever on first open.
    init {
        refresh()
    }
    fun refreshOnEntry() {
        if (hasLoadedOnce) refresh() else hasLoadedOnce = true
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val revisions = when (val result = loadRevisions()) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val entries = when (val result = loadStatusLog(LIMIT)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }
            val closed = when (val result = loadClosedTasks(TaskQuery.CLOSED)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> return@launch fail(result.failure)
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    revisions = revisions.map { revision ->
                        RevisionEntry(
                            id = revision.id.value,
                            reasonCode = revision.reasonCode,
                            appliedAt = revision.appliedAt,
                            blockCount = revision.placements.size,
                            replaces = revision.basedOnRevisionId?.value,
                        )
                    },
                    taskEntries = entries,
                    currentRevisionId = revisions.firstOrNull()?.id?.value,
                    completedCount = closed.count { it.status == TaskStatus.COMPLETED },
                    skippedCount = closed.count { it.status == TaskStatus.SKIPPED },
                    archivedCount = closed.count { it.status == TaskStatus.ARCHIVED },
                    message = null,
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun fail(failure: Failure) {
        _state.update { it.copy(isLoading = false, message = describe(failure)) }
    }

    class Factory(
        private val loadRevisions: suspend () -> Outcome<List<ScheduleRevision>>,
        private val loadStatusLog: suspend (Int) -> Outcome<List<TaskHistoryEntry>>,
        private val loadClosedTasks: suspend (TaskQuery) -> Outcome<List<com.chrona.app.domain.model.Task>>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(loadRevisions, loadStatusLog, loadClosedTasks) as T
    }

    companion object {
        const val LIMIT: Int = 60
    }
}

/** Turns a stored reason code into something a person can read. */
fun reasonLabel(code: String): String = when (code) {
    "TASK_CREATED" -> "created"
    "TASK_EDITED" -> "edited"
    "TASK_STARTED" -> "started"
    "TASK_COMPLETED" -> "completed"
    "TASK_POSTPONED" -> "postponed"
    "TASK_SKIPPED" -> "skipped"
    "TASK_ARCHIVED" -> "archived"
    "TASK_RESTORED" -> "restored"
    "TASK_DELETED" -> "deleted"
    "SCHEDULE_APPLIED" -> "plan applied"
    "SCHEDULE_UNDONE" -> "plan undone"
    "SCHEDULE_RECOVERY" -> "recovered"
    else -> code.lowercase().replace('_', ' ')
}
