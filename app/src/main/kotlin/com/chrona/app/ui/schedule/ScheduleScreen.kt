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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.R
import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.scheduling.SchedulePlacement
import com.chrona.app.domain.scheduling.UnplacedReason
import com.chrona.app.domain.scheduling.UnplacedTask
import com.chrona.app.ui.design.ChronaCard
import com.chrona.app.ui.design.SectionHeading
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.StatRow
import com.chrona.app.ui.design.StatTileData
import com.chrona.app.ui.design.StatTileGrid
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.today.ScreenHeader
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * Schedule — the preview-before-apply surface (SCHED-001..003, REC-001).
 *
 * Nothing here mutates data. The plan is shown first, including what could not be placed and why,
 * and only "Apply plan" writes. Overload is stated plainly; the user is never blamed for it.
 *
 * The recovery preview is the same proposal, presented as consequences: what moved, what was left
 * alone, what lost its slot, and which deadlines are at risk. Applying is still explicit, because
 * a change to a protected commitment must never be silent.
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
                verticalAlignment = Alignment.CenterVertically,
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
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { testTag = "schedule_loading" },
            )

            // An empty day needs saying out loud. The engine answers with an empty proposal
            // rather than nothing, so "nothing to plan" is judged by the world, not the result.
            proposal == null || state.allTasks.isEmpty() -> EmptyState(
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
                DayHeader(state)

                Text(
                    text = proposal.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { testTag = "schedule_explanation" },
                )

                CountsRow(state)

                if (proposal.overloaded || proposal.unplaced.isNotEmpty()) {
                    OverloadNotice(proposal.overloaded, proposal.unplaced.size)
                }

                if (proposal.conflicts.isNotEmpty()) {
                    SectionTitle("Conflicts")
                    proposal.conflicts.forEach { conflict ->
                        Text(
                            text = "\u2022 ${state.titles[conflict.taskId] ?: conflict.taskId.value} " +
                                "against ${conflict.otherId}: " +
                                (conflict.explanation.ifBlank { "these cannot both keep their time" }),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                if (proposal.placements.isNotEmpty()) {
                    SectionTitle("Planned (${proposal.placements.size} blocks)")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (proposal.placements.size > 6) 320.dp else (proposal.placements.size * 62).dp),
                        userScrollEnabled = false,
                    ) {
                        items(proposal.placements, key = { "${it.taskId.value}-${it.sessionIndex}" }) { placement ->
                            PlacementRow(
                                placement = placement,
                                title = state.titles[placement.taskId] ?: placement.taskId.value,
                                priority = state.priorities[placement.taskId],
                                isFixed = state.fixedTaskIds.contains(placement.taskId),
                            )
                        }
                    }
                }

                if (proposal.changes.isNotEmpty()) {
                    SectionTitle("What would change")
                    proposal.changes.forEach { change ->
                        Text(
                            text = "\u2022 ${state.titles[change.taskId] ?: change.taskId.value}: ${change.explanation}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                if (proposal.unplaced.isNotEmpty()) {
                    SectionTitle("Could not be placed (${proposal.unplaced.size})")
                    proposal.unplaced.forEach { item ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = state.titles[item.taskId] ?: item.taskId.value,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = reasonText(item),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (proposal.assumptions.isNotEmpty()) {
                    SectionTitle("Assumptions")
                    proposal.assumptions.forEach { assumption ->
                        Text(
                            text = "\u2022 ${assumption.text}",
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

                if (state.isWorking) {
                    Text(
                        text = "Working\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics { testTag = "schedule_working" },
                    )
                }
            }
        }
    }
}

/**
 * Which day is being planned, over which hours, and at which revision.
 *
 * The availability line is the one that matters most: it is what makes it obvious why work is
 * not being placed at 06:00 when the user only said they were free from 09:00.
 */
@Composable
private fun DayHeader(state: ScheduleUiState) {
    val horizon = state.horizon
    val windows = horizon?.windows.orEmpty()
    val availability = when {
        windows.isEmpty() -> "No available hours configured"
        // A window that ends exactly at midnight is the end of that day, not a zero-length span.
        // Rendering it as "00:00-00:00" is what made an unconfigured day look broken.
        windows.all { it.end.toLocalTime() == LocalTime.MIDNIGHT && it.end.toLocalDate() == it.start.toLocalDate().plusDays(1) } ->
            "All day (${windows.first().start.format(timeFormat)}\u201324:00)"
        else -> windows.joinToString(", ") {
            val end = if (it.end.toLocalTime() == LocalTime.MIDNIGHT) "24:00" else it.end.format(timeFormat)
            "${it.start.format(timeFormat)}\u2013$end"
        }
    }
    ChronaCard(
        title = horizon?.date?.format(dayFormat) ?: "Today",
        accent = ChronaTheme.tokens.accent,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { testTag = "schedule_date" },
    ) {
        StatRow(
            label = "Your available hours",
            value = availability,
            testTag = "schedule_availability",
        )
        StatRow(
            label = "Plan",
            value = if (state.currentRevisionId == null) {
                "Preview only \u2014 nothing applied"
            } else {
                "${shortRevision(state.currentRevisionId?.value)} applied"
            },
            testTag = "schedule_revision",
        )
    }
}

/**
 * Counts the whole task pool, not just the plan.
 *
 * Reporting only what fitted would be the worst version of this screen: it would look like a
 * perfect day while a third of the work quietly had nowhere to go.
 */
/**
 * Counts the whole task pool, not just the plan.
 *
 * Reporting only what fitted would be the worst version of this screen: it would look like a
 * perfect day while a third of the work quietly had nowhere to go. Tiles rather than chips so the
 * numbers are readable at a glance instead of competing with the plan for attention.
 */
@Composable
private fun CountsRow(state: ScheduleUiState) {
    val proposal = state.proposal ?: return
    val open = state.allTasks.count { it.status.isOpen }
    val scheduled = proposal.placements.map { it.taskId }.toSet().size
    val unplaced = proposal.unplaced.size
    val completed = state.allTasks.count { it.status == com.chrona.app.domain.model.TaskStatus.COMPLETED }
    StatTileGrid(
        tiles = listOf(
            StatTileData("Active", open.toString(), "schedule_count_active"),
            StatTileData(
                "Scheduled", scheduled.toString(), "schedule_count_scheduled",
                ChronaTheme.tokens.accent,
            ),
            StatTileData(
                "Unplaced", unplaced.toString(), "schedule_count_unplaced",
                if (unplaced > 0) MaterialTheme.colorScheme.error else null,
            ),
            StatTileData("Completed", completed.toString(), "schedule_count_completed"),
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun OverloadNotice(overloaded: Boolean, unplacedCount: Int) {
    ChronaCard(
        title = null,
        accent = if (overloaded) MaterialTheme.colorScheme.error else ChronaTheme.tokens.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { testTag = "schedule_overloaded" },
    ) {
        Text(
            text = if (overloaded) "This day cannot fit everything" else "Some work has no room",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "$unplacedCount task(s) have no place today. Nothing was dropped \u2014 " +
                "they are listed below with the reason, and you can move them to another day.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeading(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        testTag = "section_$text",
    )
}

/**
 * One planned block.
 *
 * A fixed commitment carries a lock glyph *and* the words "fixed", never colour alone: colour is
 * the first thing to fail in bright sun, low contrast mode or a colour-blind reader.
 */
@Composable
private fun PlacementRow(
    placement: SchedulePlacement,
    title: String,
    priority: Priority?,
    isFixed: Boolean,
) {
    val window = "${placement.start.format(timeFormat)}\u2013${placement.end.format(timeFormat)}"
    val style = priority?.let { priorityStyleOf(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                testTag = "placement_${placement.taskId.value}_${placement.sessionIndex}"
                contentDescription = buildString {
                    append(window)
                    append(", ")
                    append(title)
                    if (isFixed) append(", fixed time")
                    if (placement.sessionIndex > 0) append(", part ${placement.sessionIndex + 1}")
                }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = window, style = MaterialTheme.typography.bodyMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isFixed) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(14.dp),
                    )
                    Text(
                        text = "Fixed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (style != null) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.accent,
                    )
                }
                if (placement.sessionIndex > 0) {
                    Text(
                        text = "part ${placement.sessionIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A revision id is a UUID: 36 characters of machine noise. Only its tail distinguishes one plan
 * from the next, so that is what a person is shown.
 */
private fun shortRevision(id: String?): String {
    if (id == null) return "Plan"
    val tail = id.substringAfterLast('-').take(6)
    return if (tail.isNotEmpty()) "Revision \u2026$tail applied" else "Revision applied"
}

/** Plain-language reason for an unplaced task. Every branch has a sentence the user can act on. */
internal fun reasonText(item: UnplacedTask): String = when (item.reason) {
    UnplacedReason.NO_AVAILABLE_WINDOW ->
        item.explanation.ifBlank { "There was no free time left in your available hours." }

    UnplacedReason.DEADLINE_INFEASIBLE ->
        item.explanation.ifBlank { "Its deadline cannot be met, even with everything else moved." }

    UnplacedReason.DEPENDENCY_INCOMPLETE ->
        item.explanation.ifBlank { "It depends on work that has not been planned yet." }

    UnplacedReason.FIXED_CONFLICT ->
        item.explanation.ifBlank { "Its fixed time clashes with another commitment." }

    UnplacedReason.MIN_SESSION_UNAVAILABLE ->
        item.explanation.ifBlank { "Only gaps shorter than its minimum session were free." }

    UnplacedReason.INVALID_DURATION -> "Its duration is not usable."
    UnplacedReason.USER_CHOICE_REQUIRED -> "It needs a decision from you."

    // Not a capacity failure: the task belongs to another day and must never make today look
    // overloaded.
    UnplacedReason.OUTSIDE_HORIZON ->
        item.explanation.ifBlank { "It is planned for another day." }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    ChronaTheme { Surface { ScheduleScreen(ScheduleUiState(), {}, {}, {}, {}) } }
}
