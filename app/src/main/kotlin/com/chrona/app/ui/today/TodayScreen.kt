package com.chrona.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.R
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.task.label
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.ChronaColors
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.taskeditor.TaskEditorSheet

/**
 * Today — "What should I do next?".
 *
 * Shows the next action, the rest of the day, and manual fixed commitments. Every row carries
 * an explicit status label and an explicit priority label, so nothing depends on colour alone
 * (UI-002, ACC-001).
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onCreate: (title: String, details: String?, priority: Priority, minutes: Int?, deadlineDate: java.time.LocalDate?) -> Unit,
    onStart: (TaskId) -> Unit,
    onComplete: (TaskId) -> Unit,
    onPostpone: (TaskId) -> Unit,
    onEditorOpen: () -> Unit,
    onEditorClose: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "today_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_today),
            subtitle = state.nextTask?.let { "Next: ${it.title}" } ?: "Nothing queued yet",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.isEditorOpen) {
            TaskEditorSheet(
                onSubmit = onCreate,
                onDismiss = onEditorClose,
                onSpeak = onSpeak,
                initialTitle = state.editorPrefill,
            )
            return@Column
        }

        if (state.tasks.isEmpty() && state.commitments.isEmpty()) {
            EmptyState(
                title = "Nothing queued yet",
                message = "Add a task and it is stored on this device straight away — " +
                    "no account, no setup.",
                icon = Icons.Outlined.PostAdd,
                action = {
                    TextButton(
                        onClick = onEditorOpen,
                        modifier = Modifier.semantics { testTag = "today_add_task_cta" },
                    ) {
                        Text("Add your first task")
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { message ->
                    item(key = "message") {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { testTag = "today_message" },
                        )
                    }
                }

                if (state.commitments.isNotEmpty()) {
                    item(key = "commitments_header") { SectionLabel("Fixed commitments") }
                    items(state.commitments, key = { it.id.value }) { event ->
                        CommitmentRow(event.title, "${event.startAt.toLocalTime()} – ${event.endAt.toLocalTime()}")
                    }
                }

                if (state.tasks.isNotEmpty()) {
                    item(key = "tasks_header") { SectionLabel("Your tasks") }
                    items(state.tasks, key = { it.id.value }) { task ->
                        TaskNoteCard(
                            task = task,
                            onStart = { onStart(task.id) },
                            onComplete = { onComplete(task.id) },
                            onPostpone = { onPostpone(task.id) },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onEditorOpen,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New task") },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.End)
                .semantics { testTag = "today_add_task" },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CommitmentRow(title: String, timeRange: String) {
    PinnedNoteCard(modifier = Modifier.fillMaxWidth(), accent = ChronaColors.Thread) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            timeRange,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Fixed — recovery will not move this",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskNoteCard(
    task: Task,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onPostpone: () -> Unit,
) {
    val priorityStyle = priorityStyleOf(task.priority)
    PinnedNoteCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "task_note_${task.id.value}" },
        accent = priorityStyle.accent,
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (task.status == TaskStatus.COMPLETED) TextDecoration.LineThrough else null,
        )
        Text(
            text = "${priorityStyle.label} · ${task.status.label()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.estimatedDuration?.let { duration ->
            Text(
                text = "About ${duration.toMinutes()} min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (task.status != TaskStatus.IN_PROGRESS && task.status.isOpen) {
                TextButton(onClick = onStart, modifier = Modifier.width(96.dp)) { Text("Start") }
            }
            if (task.status.isOpen) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.semantics { testTag = "complete_${task.id.value}" },
                ) { Text("Done") }
                TextButton(onClick = onPostpone) { Text("Later") }
            }
        }
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    PinnedNoteCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    ChronaTheme {
        TodayScreen(
            state = TodayUiState(
                isLoading = false,
                tasks = listOf(
                    Task(
                        id = TaskId("preview-1"),
                        title = "Study for GATE",
                        priority = Priority.CRITICAL,
                        estimatedDuration = java.time.Duration.ofHours(2),
                        createdAt = java.time.Instant.EPOCH,
                        updatedAt = java.time.Instant.EPOCH,
                    ),
                ),
            ),
            onCreate = { _, _, _, _, _ -> },
            onStart = {},
            onComplete = {},
            onPostpone = {},
            onEditorOpen = {},
            onEditorClose = {},
            onSpeak = {},
        )
    }
}
