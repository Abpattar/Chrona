package com.chrona.app.ui.schedule

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import com.chrona.app.R
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.scheduling.UnplacedReason
import com.chrona.app.domain.scheduling.UnplacedTask
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.today.ScreenHeader
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Schedule — the preview-before-apply surface (SCHED-001..003, REC-001).
 *
 * Nothing here mutates data. The plan is shown first, including what could not be placed, and
 * only "Apply plan" writes. Overload is stated plainly; the user is never blamed for it.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    onRefresh: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "schedule_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_schedule),
            subtitle = "Preview first. Nothing changes until you apply.",
        )

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { testTag = "schedule_message" },
                )
                TextButton(onClick = onClearMessage) { Text("Dismiss") }
            }
        }

        val proposal = state.proposal
        when {
            state.isLoading -> Text(
                text = "Planning your day\u2026",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )

            // An empty day needs saying out loud. The engine answers with an empty proposal
            // rather than nothing, so "nothing to plan" is judged by the world, not the result.
            proposal == null || state.titles.isEmpty() -> EmptyState(
                title = "No day planned yet",
                message = "Add tasks and fixed commitments, then Chrona will propose a plan you can review.",
                icon = Icons.Outlined.CalendarMonth,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = proposal.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )

                if (proposal.overloaded) {
                    PinnedNoteCard(
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "Today cannot fit everything",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = proposal.assumptions.lastOrNull()?.text
                                ?: "There is more work than the day can hold.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (proposal.placements.isNotEmpty()) {
                    SectionTitle("Planned (${proposal.placements.size} blocks)")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (proposal.placements.size > 6) 320.dp else (proposal.placements.size * 56).dp),
                        userScrollEnabled = false,
                    ) {
                        items(proposal.placements) { placement ->
                            PlacementRow(placement, state.titles[placement.taskId] ?: placement.taskId.value)
                        }
                    }
                }

                if (proposal.changes.isNotEmpty()) {
                    SectionTitle("Moved")
                    proposal.changes.forEach { change ->
                        Text(
                            text = "\u2022 ${state.titles[change.taskId] ?: change.taskId.value}: ${change.explanation}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                if (proposal.unplaced.isNotEmpty()) {
                    SectionTitle("Could not be placed")
                    proposal.unplaced.forEach { item ->
                        Text(
                            text = "\u2022 ${state.titles[item.taskId] ?: item.taskId.value} \u2014 ${reasonText(item)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onApply,
                        enabled = !state.isWorking && proposal.placements.isNotEmpty(),
                        modifier = Modifier.semantics { testTag = "schedule_apply" },
                    ) { Text("Apply plan") }

                    if (state.currentRevisionId != null) {
                        OutlinedButton(
                            onClick = onUndo,
                            enabled = !state.isWorking,
                            modifier = Modifier.semantics { testTag = "schedule_undo" },
                        ) { Text("Undo") }
                    }

                    TextButton(onClick = onRefresh, enabled = !state.isWorking) { Text("Refresh") }
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlacementRow(placement: SchedulePlacement, title: String) {
    val window = "${placement.start.format(timeFormat)}\u2013${placement.end.format(timeFormat)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = window, style = MaterialTheme.typography.bodyMedium)
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun reasonText(item: UnplacedTask): String = when (item.reason) {
    UnplacedReason.NO_AVAILABLE_WINDOW -> "no free time left today"
    UnplacedReason.DEADLINE_INFEASIBLE -> "its deadline cannot be met"
    UnplacedReason.DEPENDENCY_INCOMPLETE -> item.explanation.ifBlank { "it depends on unplanned work" }
    UnplacedReason.FIXED_CONFLICT -> "its fixed time clashes"
    UnplacedReason.MIN_SESSION_UNAVAILABLE -> "only gaps shorter than its minimum session were free"
    UnplacedReason.INVALID_DURATION -> "its duration is not usable"
    UnplacedReason.USER_CHOICE_REQUIRED -> "it needs a decision from you"
    UnplacedReason.OUTSIDE_HORIZON -> item.explanation.ifBlank { "it is planned for another day" }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    ChronaTheme { Surface { ScheduleScreen(ScheduleUiState(), {}, {}, {}, {}) } }
}
