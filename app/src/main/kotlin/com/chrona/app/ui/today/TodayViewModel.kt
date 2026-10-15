package com.chrona.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.CalendarEvent
import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.usecase.NewEvent
import com.chrona.app.usecase.NewTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What Today needs to render. Immutable so Compose can diff cheaply. */
data class TodayUiState(
    val isLoading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val commitments: List<CalendarEvent> = emptyList(),
    /** Plain-language message; never a stack trace or a raw record. */
    val message: String? = null,
    val isEditorOpen: Boolean = false,
) {
    /** The next action: in-progress work first, then the first open task. */
    val nextTask: Task?
        get() = tasks.firstOrNull { it.status == com.chrona.app.domain.model.TaskStatus.IN_PROGRESS }
            ?: tasks.firstOrNull()
}

/**
 * State holder for Today (UI-001, TASK-002).
 *
 * It collects state and dispatches use cases; it holds no scheduling rules and never touches a
 * DAO. Its only job here is to make the create → list → complete path work end to end and to
 * reload from the same repository every other surface uses.
 */
class TodayViewModel(
    private val listTasks: suspend (TaskQuery) -> Outcome<List<Task>>,
    private val createTask: suspend (NewTask) -> Outcome<Task>,
    private val completeTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
    private val startTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
    private val postponeTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
    private val listCommitments: suspend (Instant, Instant) -> Outcome<List<CalendarEvent>>,
    private val createCommitment: suspend (NewEvent) -> Outcome<CalendarEvent>,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val dayStart = startOfToday()
            val dayEnd = dayStart.plus(Duration.ofDays(1))
            val tasks = when (val result = listTasks(TaskQuery.ACTIVE)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> {
                    _state.update { it.copy(isLoading = false, message = describe(result.failure)) }
                    return@launch
                }
            }
            val commitments = when (val result = listCommitments(dayStart, dayEnd)) {
                is Outcome.Success -> result.value
                is Outcome.Failed -> {
                    _state.update { it.copy(isLoading = false, message = describe(result.failure)) }
                    return@launch
                }
            }
            _state.update { it.copy(isLoading = false, tasks = tasks, commitments = commitments, message = null) }
        }
    }

    fun openEditor() = _state.update { it.copy(isEditorOpen = true) }

    fun closeEditor() = _state.update { it.copy(isEditorOpen = false) }

    /** Typed creation. Voice arrives in P03 and will land in the same place. */
    fun submitTask(
        title: String,
        details: String?,
        priority: com.chrona.app.domain.model.Priority,
        estimatedMinutes: Int?,
        deadlineDate: LocalDate?,
    ) {
        viewModelScope.launch {
            val request = NewTask(
                title = title,
                details = details,
                priority = priority,
                estimatedDuration = estimatedMinutes?.let { Duration.ofMinutes(it.toLong()) },
                // A date-only deadline is stored as the end of that local day, so a user who
                // says "due Friday" does not accidentally get a Friday-morning deadline.
                deadline = deadlineDate?.let { date ->
                    date.plusDays(1).atTime(java.time.LocalTime.MAX).atZone(zoneId).withNano(0)
                },
            )
            when (val result = createTask(request)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isEditorOpen = false, message = "Saved.") }
                    refresh()
                }

                is Outcome.Failed -> _state.update {
                    it.copy(message = describe(result.failure), isEditorOpen = true)
                }
            }
        }
    }

    fun start(taskId: com.chrona.app.domain.model.TaskId) = act(taskId, startTask, "Started.")

    fun complete(taskId: com.chrona.app.domain.model.TaskId) = act(taskId, completeTask, "Done.")

    fun postpone(taskId: com.chrona.app.domain.model.TaskId) = act(taskId, postponeTask, "Postponed.")

    fun addCommitment(title: String, startAt: java.time.ZonedDateTime, endAt: java.time.ZonedDateTime) {
        viewModelScope.launch {
            when (val result = createCommitment(NewEvent(title, startAt, endAt))) {
                is Outcome.Success -> refresh()
                is Outcome.Failed -> _state.update { it.copy(message = describe(result.failure)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun act(
        taskId: com.chrona.app.domain.model.TaskId,
        action: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
        successMessage: String,
    ) {
        viewModelScope.launch {
            when (val result = action(taskId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(message = successMessage) }
                    refresh()
                }

                is Outcome.Failed -> _state.update { it.copy(message = describe(result.failure)) }
            }
        }
    }

    private fun startOfToday(): Instant =
        LocalDate.now(clock.withZone(zoneId)).atStartOfDay(zoneId).toInstant()

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
        private val listTasks: suspend (TaskQuery) -> Outcome<List<Task>>,
        private val createTask: suspend (NewTask) -> Outcome<Task>,
        private val completeTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
        private val startTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
        private val postponeTask: suspend (com.chrona.app.domain.model.TaskId) -> Outcome<Task>,
        private val listCommitments: suspend (Instant, Instant) -> Outcome<List<CalendarEvent>>,
        private val createCommitment: suspend (NewEvent) -> Outcome<CalendarEvent>,
        private val clock: Clock,
        private val zoneId: ZoneId,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(
            listTasks, createTask, completeTask, startTask, postponeTask,
            listCommitments, createCommitment, clock, zoneId,
        ) as T
    }
}