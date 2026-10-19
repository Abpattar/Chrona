package com.chrona.app.ui.taskbrowser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.task.label
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.today.ScreenHeader
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/** The filters a user can narrow by. Kept explicit so a screen cannot invent a filter. */
enum class TaskFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    FIXED("Fixed time"),
    COMPLETED("Completed"),
    UNSCHEDULED("No slot"),
    SIMULATED("Simulated"),
}

/** One row in the browser. Placement is optional: an unscheduled task still has to be listed. */
data class TaskBrowserRow(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val priority: Priority,
    val isFixed: Boolean,
    val origin: TaskOrigin,
    val deadline: String?,
    val scheduledStart: ZonedDateTime?,
    val scheduledEnd: ZonedDateTime?,
    /** Why this task has no slot, when it has none. */
    val unscheduledReason: String?,
    val isSplit: Boolean,
)

data class TaskBrowserUiState(
    val isLoading: Boolean = true,
    val rows: List<TaskBrowserRow> = emptyList(),
    val query: String = "",
    val filter: TaskFilter = TaskFilter.ALL,
    val message: String? = null,
    /** Count before the search and filter were applied, so the header can say "showing 8 of 142". */
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val unscheduledCount: Int = 0,
) {
    /** Rows after the current search and filter. Counted here so the view has one definition. */
    val visible: List<TaskBrowserRow>
        get() {
            val needle = query.trim().lowercase()
            return rows.filter { row ->
                val matchesQuery = needle.isEmpty() ||
                    row.title.lowercase().contains(needle) ||
                    row.id.lowercase().contains(needle)
                val matchesFilter = when (filter) {
                    TaskFilter.ALL -> true
                    TaskFilter.ACTIVE -> row.status.isOpen
                    TaskFilter.FIXED -> row.isFixed
                    TaskFilter.COMPLETED -> row.status == TaskStatus.COMPLETED
                    TaskFilter.UNSCHEDULED -> row.unscheduledReason != null
                    TaskFilter.SIMULATED -> row.origin == TaskOrigin.SIMULATION
                }
                matchesQuery && matchesFilter
            }
        }
}

/**
 * Task browser — every task Chrona holds, searchable and filterable.
 *
 * It reads the same repositories the rest of the app writes to. There is no separate list here:
 * a task created by voice, typed by hand, or produced by the simulation all appear, and each row
 * says which it is, because a user looking at a 140-task simulation should never mistake it for
 * their own work.
 *
 * Unscheduled tasks are first-class rows with a stated reason. Listing only what fitted is how a
 * planner quietly loses a user's trust.
 */
@Composable
fun TaskBrowserScreen(
    state: TaskBrowserUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "tasks_screen" },
    ) {
        ScreenHeader(
            title = "Tasks",
            subtitle = if (state.totalCount == 0) {
                "Everything Chrona is holding for you."
            } else {
                "Showing ${state.visible.size} of ${state.totalCount} tasks."
            },
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search tasks") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { testTag = "tasks_search" },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskFilter.entries.forEach { candidate ->
                val selected = candidate == state.filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(candidate) },
                    label = { Text(candidate.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.semantics {
                        testTag = "task_filter_${candidate.name.lowercase()}"
                        contentDescription = candidate.label
                    },
                )
            }
        }

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { testTag = "task_browser_message" },
                )
                TextButton(onClick = onClearMessage) { Text("Dismiss") }
            }
        }

        when {
            state.isLoading -> Text(
                text = "Loading tasks\u2026",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { testTag = "task_browser_loading" },
            )

            state.rows.isEmpty() -> EmptyState(
                title = "No tasks yet",
                message = "Speak or type a task and it will appear here.",
                icon = Icons.Outlined.Search,
            )

            state.visible.isEmpty() -> EmptyState(
                title = "Nothing matches",
                message = "No task matches that search and filter. Try a different word, " +
                    "or switch the filter back to All.",
                icon = Icons.Outlined.Search,
            )

            // An explicit tag on the list itself: a test needs to address *this* scrollable,
            // and `hasScrollAction` also matches the horizontally scrolling filter row.
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTag = "tasks_list" },
            ) {
                items(state.visible, key = { it.id }) { row ->
                    TaskRow(row)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(row: TaskBrowserRow) {
    val style = priorityStyleOf(row.priority)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                testTag = "task_row_${row.id}"
                contentDescription = buildString {
                    append(row.title)
                    append(", ").append(row.status.label())
                    append(", ").append(style.label)
                    if (row.isFixed) append(", fixed time")
                    row.scheduledStart?.let { append(", scheduled ${it.format(dateTimeFormat)}") }
                        ?: append(", no time slot")
                    if (row.origin == TaskOrigin.SIMULATION) append(", simulated data")
                }
            },
    ) {
        Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = row.status.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color = style.accent,
            )
            if (row.isFixed) {
                Text(
                    text = "\u2022 fixed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (row.isSplit) {
                Text(
                    text = "\u2022 split into parts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.origin == TaskOrigin.SIMULATION) {
                Text(
                    text = "\u2022 simulated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        row.scheduledStart?.let { start ->
            Text(
                text = "${start.format(dateTimeFormat)}\u2013${row.scheduledEnd?.format(timeFormat) ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
            )
        } ?: row.unscheduledReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "task_unplaced_reason_${row.id}" },
            )
        }

        row.deadline?.let { deadline ->
            Text(
                text = "Due $deadline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskBrowserPreview() {
    ChronaTheme {
        Surface {
            TaskBrowserScreen(
                TaskBrowserUiState(
                    isLoading = false,
                    rows = listOf(
                        TaskBrowserRow(
                            id = "a", title = "DBMS lecture", status = TaskStatus.PLANNED,
                            priority = Priority.CRITICAL, isFixed = true, origin = TaskOrigin.SIMULATION,
                            deadline = null,
                            scheduledStart = ZonedDateTime.now(ZoneId.of("UTC")),
                            scheduledEnd = null, unscheduledReason = null, isSplit = false,
                        ),
                        TaskBrowserRow(
                            id = "b", title = "Read documentation", status = TaskStatus.INBOX,
                            priority = Priority.LOW, isFixed = false, origin = TaskOrigin.TYPED,
                            deadline = null, scheduledStart = null, scheduledEnd = null,
                            unscheduledReason = "There was no free time left in your available hours.",
                            isSplit = false,
                        ),
                    ),
                ),
                onQueryChange = {}, onFilterChange = {}, onClearMessage = {},
            )
        }
    }
}
