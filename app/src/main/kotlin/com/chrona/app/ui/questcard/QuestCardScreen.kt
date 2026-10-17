package com.chrona.app.ui.questcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.reminder.ReminderAction
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderDecision
import com.chrona.app.domain.reminder.ReminderDecisionReason
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.today.ScreenHeader

/**
 * The in-app Quest Card surface — the always-available fallback (CH-UI-001, ADR-009).
 *
 * It is reachable from Today and needs no notification or overlay permission, so quiet reminders
 * still work on a device that grants nothing. Every action carries its exact meaning; Dismiss in
 * particular promises the task is untouched.
 */
@Composable
fun QuestCardScreen(
    state: QuestCardUiState,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "quest_card_screen" },
    ) {
        ScreenHeader(
            title = "Quest Cards",
            subtitle = "What Chrona would nudge you about, available without any permission.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val card = state.card
        if (card == null) {
            EmptyState(
                title = "You're all clear",
                message = "Nothing needs a reminder right now. Check back later, or add a task in Today.",
                icon = Icons.Outlined.TaskAlt,
                action = {
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.semantics { testTag = "quest_card_refresh" },
                    ) { Text("Check again") }
                },
            )
            return@Column
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "quest_card_message" },
                )
            }

            val primary = card.tasks.first()
            val priorityStyle = priorityStyleOf(primary.priority)
            PinnedNoteCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "quest_card" },
                accent = priorityStyle.accent,
            ) {
                Text(primary.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${priorityStyle.label} · ${primary.status.name.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.tasks.size > 1) {
                    Text(
                        text = "Related: " + card.tasks.drop(1).joinToString { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onStart,
                        modifier = Modifier.semantics { testTag = "quest_start" },
                    ) { Text("Start") }
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.semantics { testTag = "quest_done" },
                    ) { Text("Done") }
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.semantics { testTag = "quest_later" },
                    ) { Text("Later") }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { testTag = "quest_dismiss" },
                    ) { Text("Dismiss") }
                }
            }

            Text(
                text = "Dismiss closes this card only — it never completes, deletes or postpones the task.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuestCardScreenPreview() {
    ChronaTheme {
        Surface {
            QuestCardScreen(
                state = QuestCardUiState(
                    isLoading = false,
                    card = ReminderCard(
                        cardId = "reminder:preview-1",
                        decision = ReminderDecision(
                            action = ReminderAction.IN_APP,
                            tasks = listOf(TaskId("preview-1")),
                            reason = ReminderDecisionReason.ELIGIBLE,
                            nextEligibleAt = null,
                        ),
                        tasks = listOf(
                            Task(
                                id = TaskId("preview-1"),
                                title = "Study for GATE",
                                priority = Priority.CRITICAL,
                                createdAt = java.time.Instant.EPOCH,
                                updatedAt = java.time.Instant.EPOCH,
                            ),
                        ),
                    ),
                ),
                onStart = {},
                onDone = {},
                onLater = {},
                onDismiss = {},
                onRefresh = {},
            )
        }
    }
}
