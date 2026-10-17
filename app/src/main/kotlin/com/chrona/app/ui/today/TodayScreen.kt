package com.chrona.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PostAdd
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
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.taskeditor.TaskEditorSheet

/**
 * Today — "What should I do next?".
 *
 * Voice is not offered here: the microphone is on every screen, in the same place, because the
 * fastest way to add a task should not depend on which tab the user happens to be on. Typing
 * stays available through exactly one control.
 *
 * Every row carries an explicit status label and an explicit priority label, so nothing depends
 * on colour alone (UI-002, ACC-001).
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
    modifier: Modifier = Modifier,
    onOpenQuestCards: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "today_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_today),
            // Never the same words as the empty state below, and always pointing at the way a
            // task actually gets added.
            subtitle = state.nextTask?.let { "Next: ${it.title}" } ?: "Hold the microphone to add one",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpenQuestCards,
                        modifier = Modifier.semantics { testTag = "today_open_quest_cards" },
                    ) { Text("Quest Cards") }
                    // The one text-entry control, kept in the header where it cannot be pushed
                    // out of the screen by a long list.
                    TextButton(
                        onClick = onEditorOpen,
                        modifier = Modifier.semantics { testTag = "today_add_task" },
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text("  Type a task")
                    }
                }
            },
        )
        HorizontalDivider(color = ChronaTheme.tokens.glassBorder)

        if (state.isEditorOpen) {
            TaskEditorSheet(
                onSubmit = onCreate,
                onDismiss = onEditorClose,
                initialTitle = state.editorPrefill,
            )
            return@Column
        }

        if (state.tasks.isEmpty() && state.commitments.isEmpty()) {
            EmptyState(
                title = "Nothing queued yet",
                message = "Hold the microphone and say what needs doing. Everything stays on this device.",
                icon = Icons.Outlined.PostAdd,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom padding clears the microphone and the navigation bar, so the last card
                // is never trapped underneath them.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
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
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CommitmentRow(title: String, timeRange: String) {
    PinnedNoteCard(modifier = Modifier.fillMaxWidth(), accent = ChronaTheme.tokens.thread) {
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
                TextButton(onClick = onStart) { Text("Start") }
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

/** Screen title with an optional trailing action, so the header stays one element. */
@Composable
internal fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
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
        )
    }
}