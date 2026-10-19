package com.chrona.app.ui.history

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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.R
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.today.ScreenHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * History — applied schedule revisions and task status changes.
 *
 * Both sections read the tables production writes to, so an empty screen means "nothing has
 * happened yet", not "this screen was never built". Each revision states what triggered it and
 * what it replaced, because a change with no cause is not something a user can act on.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "history_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_history),
            subtitle = "Completed, postponed and moved work, with reasons.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when {
            state.isLoading -> Text(
                text = "Loading history\u2026",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { testTag = "history_loading" },
            )

            state.revisions.isEmpty() && state.taskEntries.isEmpty() -> EmptyState(
                title = "Nothing recorded yet",
                message = "Apply a plan or complete a task and it will appear here, with the " +
                    "reason for the change and what it replaced.",
                icon = Icons.Outlined.History,
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTag = "history_list" },
            ) {
                if (state.revisions.isNotEmpty()) {
                    item { SectionTitle("Schedule revisions (${state.revisions.size})") }
                    items(state.revisions, key = { "rev-${it.id}" }) { revision ->
                        RevisionRow(revision, zoneId)
                    }
                }

                if (state.taskEntries.isNotEmpty()) {
                    item {
                        SectionTitle(
                            "Task changes \u2014 ${state.completedCount} completed, " +
                                "${state.skippedCount} skipped, ${state.archivedCount} archived",
                        )
                    }
                    items(state.taskEntries, key = { "log-${it.taskId}-${it.changedAt}-${it.reasonCode}" }) { entry ->
                        TaskEntryRow(entry, zoneId)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { contentDescription = text },
    )
}

@Composable
private fun RevisionRow(entry: RevisionEntry, zoneId: ZoneId) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                testTag = "history_revision_${entry.id}"
                contentDescription = "${reasonLabel(entry.reasonCode)}, " +
                    "${entry.blockCount} blocks, ${entry.appliedAt.atZone(zoneId).format(stampFormat)}"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reasonLabel(entry.reasonCode),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = entry.appliedAt.atZone(zoneId).format(stampFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${entry.blockCount} block(s) planned" +
                (entry.replaces?.let { ", replacing $it" } ?: ", first plan"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskEntryRow(entry: TaskHistoryEntry, zoneId: ZoneId) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                testTag = "history_task_${entry.taskId}"
                contentDescription = "${entry.taskTitle} ${reasonLabel(entry.reasonCode)}, " +
                    entry.changedAt.atZone(zoneId).format(stampFormat)
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = entry.taskTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = entry.changedAt.atZone(zoneId).format(stampFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildString {
                append(reasonLabel(entry.reasonCode))
                entry.fromStatus?.let { append(", was ").append(it.lowercase()) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    ChronaTheme {
        Surface {
            HistoryScreen(
                HistoryUiState(
                    isLoading = false,
                    revisions = listOf(
                        RevisionEntry(
                            id = "rev-2",
                            reasonCode = "SCHEDULE_APPLIED",
                            appliedAt = Instant.parse("2026-01-06T09:15:00Z"),
                            blockCount = 12,
                            replaces = "rev-1",
                        ),
                    ),
                    taskEntries = listOf(
                        TaskHistoryEntry(
                            taskId = "t1",
                            taskTitle = "DBMS revision",
                            fromStatus = "PLANNED",
                            toStatus = "COMPLETED",
                            reasonCode = "TASK_COMPLETED",
                            changedAt = Instant.parse("2026-01-06T09:20:00Z"),
                        ),
                    ),
                    completedCount = 1,
                ),
            )
        }
    }
}
