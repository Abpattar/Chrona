package com.chrona.app.ui.simulation

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.chrona.app.domain.simulation.SimulationDayReport
import com.chrona.app.domain.simulation.SimulationEventKind
import com.chrona.app.domain.simulation.SimulationScenarios
import com.chrona.app.ui.design.ChronaCard
import com.chrona.app.ui.design.ChronaDivider
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.SectionHeading
import com.chrona.app.ui.design.StatBlock
import com.chrona.app.ui.design.StatRow
import com.chrona.app.ui.design.StatTile
import com.chrona.app.ui.design.StatTileData
import com.chrona.app.ui.design.StatTileGrid
import com.chrona.app.ui.today.ScreenHeader

/**
 * Simulation & Tests — the one place that shows what the simulation actually did.
 *
 * Every number is counted from persisted state. The test-run block is kept visibly separate from
 * the simulation block and says so in words: this screen can advance a simulated life, and it can
 * *import* a host test run, but it cannot execute Gradle instrumentation on the phone. Presenting
 * a simulation as a test run, or the reverse, is the one thing this screen must never do.
 *
 * Layout note: the metrics are grouped into cards with labelled tiles rather than a flat run of
 * label/value lines. A wall of forty identical rows is technically complete and practically
 * unreadable; a reader should be able to take in "what day is this, and is anything wrong" before
 * reading a single row.
 */
@Composable
fun SimulationScreen(
    state: SimulationUiState,
    onSelectScenario: (String) -> Unit,
    onLoad: () -> Unit,
    onReset: () -> Unit,
    onAdvanceDay: () -> Unit,
    onRunToEnd: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "simulation_screen" },
    ) {
        ScreenHeader(
            title = "Simulation & Tests",
            subtitle = state.scenarioName,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.isLoading) {
            Text(
                text = "Loading simulation\u2026",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { testTag = "simulation_loading" },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = "simulation_list" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScenarioPicker(state, onSelectScenario) }
            item { Controls(state, onLoad, onAdvanceDay, onRunToEnd, onReset) }

            state.message?.let { message ->
                item {
                    MessageCard(message = message, onClearMessage = onClearMessage)
                }
            }

            item { RunCard(state) }
            item { PoolCard(state) }
            item { DayResultCard(state) }
            item { TestRunCard(state.checksPassed, state.checksFailed, state.checksRunAt) }

            if (state.days.isEmpty()) {
                item {
                    EmptyState(
                        title = "No simulation loaded",
                        message = "Choose a scenario and tap Load to create the task pool.",
                        icon = Icons.Outlined.Science,
                    )
                }
            } else {
                item { SectionHeading("Days (${state.days.size})") }
                items(state.days.reversed(), key = { it.dayOffset }) { day -> DayCard(day) }
            }

            if (state.events.isNotEmpty()) {
                item { SectionHeading("Change history (${state.events.size})") }
                items(state.events.takeLast(EVENT_LIMIT).reversed(), key = { "${it.dayOffset}-${it.detail.hashCode()}" }) { event ->
                    EventRow(event)
                }
                if (state.events.size > EVENT_LIMIT) {
                    item {
                        Text(
                            text = "\u2026 and ${state.events.size - EVENT_LIMIT} earlier entries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioPicker(state: SimulationUiState, onSelectScenario: (String) -> Unit) {
    val scenario = SimulationScenarios.byId(state.scenarioId)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SimulationScenarios.ALL.forEach { candidate ->
                FilterChip(
                    selected = candidate.id == state.scenarioId,
                    onClick = { onSelectScenario(candidate.id) },
                    label = { Text(candidate.displayName, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.semantics {
                        testTag = "simulation_scenario_${candidate.id}"
                        contentDescription = candidate.displayName
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = scenario?.description.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Controls(
    state: SimulationUiState,
    onLoad: () -> Unit,
    onAdvanceDay: () -> Unit,
    onRunToEnd: () -> Unit,
    onReset: () -> Unit,
) {
    // Scrollable: four labelled buttons do not fit across a 393dp phone, and a "Reset" that
    // wraps to "Res / et" is both ugly and harder to hit.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The tag goes on the button, not its label: a Button merges its descendants'
        // semantics, and a tag set on the inner Text only becomes findable by accident.
        OutlinedButton(
            onClick = onLoad,
            enabled = !state.isWorking,
            modifier = Modifier.semantics { testTag = "simulation_load" },
        ) { Text("Load") }
        Button(
            onClick = onAdvanceDay,
            enabled = state.canAdvance,
            modifier = Modifier.semantics { testTag = "simulation_advance" },
        ) { Text("Advance day") }
        OutlinedButton(
            onClick = onRunToEnd,
            enabled = state.canAdvance,
            modifier = Modifier.semantics { testTag = "simulation_run_all" },
        ) { Text("Run to end") }
        TextButton(
            onClick = onReset,
            enabled = !state.isWorking,
            modifier = Modifier.semantics { testTag = "simulation_reset" },
        ) { Text("Reset") }
    }
    if (state.isWorking) {
        Text(
            text = "Working\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = "simulation_working" },
        )
    }
}

@Composable
private fun MessageCard(message: String, onClearMessage: () -> Unit) {
    ChronaCard(
        title = null,
        accent = ChronaTheme.tokens.accent,
        modifier = Modifier.semantics { testTag = "simulation_message" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearMessage) { Text("Dismiss") }
        }
    }
}

@Composable
private fun RunCard(state: SimulationUiState) {
    ChronaCard(title = "Run", accent = ChronaTheme.tokens.accent, testTag = "simulation_run_card") {
        StatRow(
            label = "Simulated day",
            value = if (state.currentDay < 0) "not started" else "${state.currentDay + 1} of ${state.totalDays}",
            testTag = "simulation_day",
        )
        ChronaDivider()
        StatRow("Date", state.currentDateIso.ifBlank { "\u2014" }, testTag = "simulation_date")
        StatBlock(
            label = "Reference time",
            value = friendlyTime(state.referenceInstant),
            testTag = "simulation_reference",
        )
        StatRow(
            label = "Available windows",
            value = state.availableWindows.toString(),
            testTag = "simulation_windows",
        )
        if (state.lostMinutes > 0) {
            ChronaDivider()
            StatRow(
                label = "Lost to interruption",
                value = "${state.lostMinutes} min",
                valueColor = MaterialTheme.colorScheme.error,
                testTag = "simulation_interruption",
            )
        }
        ChronaDivider()
        StatRow(
            label = "Processing mode",
            value = state.processingMode,
            testTag = "simulation_processing_mode",
        )
        if (state.scenarioNote.isNotBlank()) {
            StatBlock(label = "Scenario", value = state.scenarioNote, testTag = "simulation_note")
        }
    }
}

@Composable
private fun PoolCard(state: SimulationUiState) {
    ChronaCard(title = "Task pool", accent = ChronaTheme.tokens.accent, testTag = "simulation_pool_card") {
        StatTileGrid(
            listOf(
                StatTileData("Definitions", state.taskDefinitionCount.toString(), "simulation_definitions"),
                StatTileData("Instances", state.taskInstanceCount.toString(), "simulation_instances"),
                StatTileData("Active now", state.activeTaskCount.toString(), "simulation_active"),
                StatTileData("Fixed commitments", state.fixedCommitmentCount.toString(), "simulation_fixed"),
            ),
        )
    }
}

@Composable
private fun DayResultCard(state: SimulationUiState) {
    val warn = MaterialTheme.colorScheme.error
    val amber = ChronaTheme.tokens.accent
    ChronaCard(title = "Day result", accent = amber, testTag = "simulation_result_card") {
        StatTileGrid(
            listOf(
                StatTileData("Scheduled", state.scheduledCount.toString(), "simulation_scheduled", amber),
                StatTileData(
                    "Done", state.completedCount.toString(), "simulation_completed",
                    if (state.completedCount > 0) amber else null,
                ),
                StatTileData(
                    "Missed", state.missedCount.toString(), "simulation_missed",
                    if (state.missedCount > 0) warn else null,
                ),
                StatTileData(
                    "Unplaced", state.unscheduledCount.toString(), "simulation_unscheduled",
                    if (state.unscheduledCount > 0) warn else null,
                ),
                StatTileData(
                    "Overdue", state.overdueCount.toString(), "simulation_overdue",
                    if (state.overdueCount > 0) warn else null,
                ),
                StatTileData(
                    "At risk", state.atRiskCount.toString(), "simulation_at_risk",
                    if (state.atRiskCount > 0) warn else null,
                ),
            ),
        )
        Spacer(Modifier.height(10.dp))
        StatRow("Conflicts", state.conflictCount.toString(), testTag = "simulation_conflicts")
        StatRow("Revisions applied", state.revisionCount.toString(), testTag = "simulation_revisions")
    }
}

/**
 * The host-test block.
 *
 * States plainly that these numbers come from a run on the development machine. Claiming an
 * on-device test run here would be the single most misleading thing this screen could do.
 */
@Composable
private fun TestRunCard(passed: Int?, failed: Int?, runAt: String?) {
    ChronaCard(
        title = "Test run \u2014 development machine",
        accent = ChronaTheme.tokens.accent,
        testTag = "simulation_test_card",
    ) {
        if (passed == null || failed == null) {
            Text(
                text = "No host test run imported yet. This screen cannot execute Gradle " +
                    "instrumentation tests on the device; run them with ./gradlew " +
                    "connectedDebugAndroidTest and import the result.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "simulation_tests_not_run" },
            )
        } else {
            StatTileGrid(
                listOf(
                    StatTileData("Passed", passed.toString(), "simulation_tests_passed"),
                    StatTileData(
                        "Failed", failed.toString(), "simulation_tests_failed",
                        if (failed > 0) MaterialTheme.colorScheme.error else null,
                    ),
                ),
            )
            Spacer(Modifier.height(8.dp))
            StatRow("Run at", runAt ?: "unknown", testTag = "simulation_tests_run_at")
        }
    }
}

@Composable
private fun DayCard(day: SimulationDayReport) {
    ChronaCard(
        title = "Day ${day.dayOffset + 1}",
        accent = if (day.overdueTaskCount > 0) ChronaTheme.tokens.accent else null,
        modifier = Modifier.semantics { testTag = "simulation_day_${day.dayOffset}" },
    ) {
        StatRow("Date", day.dateIso)
        StatRow("Scheduled", day.scheduledTaskCount.toString())
        StatRow(
            "Completed",
            day.completedTaskCount.toString(),
            valueColor = if (day.completedTaskCount > 0) ChronaTheme.tokens.accent else null,
        )
        StatRow(
            "Missed",
            day.missedTaskCount.toString(),
            valueColor = if (day.missedTaskCount > 0) MaterialTheme.colorScheme.error else null,
        )
        StatRow(
            "Could not place",
            day.unscheduledTaskCount.toString(),
            valueColor = if (day.unscheduledTaskCount > 0) MaterialTheme.colorScheme.error else null,
        )
        StatRow(
            "Overdue",
            day.overdueTaskCount.toString(),
            valueColor = if (day.overdueTaskCount > 0) MaterialTheme.colorScheme.error else null,
        )
        StatRow("At risk", day.atRiskTaskCount.toString())
        StatRow("Runtime", "${day.runtimeMillis} ms")
        if (day.scenarioNote.isNotBlank()) {
            Text(
                text = day.scenarioNote,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EventRow(event: com.chrona.app.domain.simulation.SimulationEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics {
                testTag = "simulation_event_${event.kind.name.lowercase()}"
                contentDescription = "Day ${event.dayOffset + 1}, ${event.kind.label()}: ${event.detail}"
            },
    ) {
        Text(
            text = "Day ${event.dayOffset + 1} \u00b7 ${event.kind.label()}",
            style = MaterialTheme.typography.labelMedium,
            color = ChronaTheme.tokens.accent,
        )
        Text(text = event.detail, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SimulationEventKind.label(): String = when (this) {
    SimulationEventKind.DAY_ADVANCED -> "day advanced"
    SimulationEventKind.TASK_ARRIVED -> "task arrived"
    SimulationEventKind.TASK_COMPLETED -> "completed"
    SimulationEventKind.TASK_MISSED -> "missed"
    SimulationEventKind.TASK_POSTPONED -> "postponed"
    SimulationEventKind.DISRUPTION -> "disruption"
    SimulationEventKind.RECOVERY_APPLIED -> "recovery"
    SimulationEventKind.UNPLACEABLE -> "could not place"
    SimulationEventKind.RESET -> "reset"
}

/**
 * `2026-01-07T01:30:00Z` means nothing to a person looking at a simulated day.
 *
 * Shown as local wall-clock on the simulated date instead, which is what the run actually means;
 * the exact instant is still stored and is still what the report uses.
 */
private fun friendlyTime(instant: String): String {
    if (instant.isBlank()) return "\u2014"
    val parsed = runCatching { java.time.Instant.parse(instant) }.getOrNull() ?: return instant
    return java.time.format.DateTimeFormatter
        .ofPattern("d MMM yyyy, HH:mm 'on the simulated clock'")
        .format(parsed.atZone(java.time.ZoneId.of("Asia/Kolkata")))
}

private const val EVENT_LIMIT = 60

@Preview(showBackground = true)
@Composable
private fun SimulationScreenPreview() {
    ChronaTheme {
        Surface { SimulationScreen(SimulationUiState(isLoading = false), {}, {}, {}, {}, {}, {}) }
    }
}
