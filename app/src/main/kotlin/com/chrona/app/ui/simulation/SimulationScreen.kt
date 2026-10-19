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
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.today.ScreenHeader

/**
 * Simulation & Tests — the one place that shows what the simulation actually did.
 *
 * Every number is counted from persisted state. The test-run block is kept visibly separate from
 * the simulation block and says so in words: this screen can advance a simulated life, and it can
 * *import* a host test run, but it cannot execute Gradle instrumentation on the phone. Presenting
 * a simulation as a test run, or the reverse, is the one thing this screen must never do.
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
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SimulationScenarios.ALL.forEach { scenario ->
                        FilterChip(
                            selected = scenario.id == state.scenarioId,
                            onClick = { onSelectScenario(scenario.id) },
                            label = { Text(scenario.displayName, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.semantics {
                                testTag = "simulation_scenario_${scenario.id}"
                                contentDescription = scenario.displayName
                            },
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = SimulationScenarios.byId(state.scenarioId)?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Scrollable: four labelled buttons do not fit across a 393dp phone, and a
                    // "Reset" that wraps to "Res / et" is both ugly and harder to hit.
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            modifier = Modifier.semantics { testTag = "simulation_working" },
                        )
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { testTag = "simulation_message" },
                        )
                        TextButton(onClick = onClearMessage) { Text("Dismiss") }
                    }
                }
            }

            item { SectionTitle("Run") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MetricLine(
                        label = "Simulated day",
                        value = if (state.currentDay < 0) "not started" else "${state.currentDay + 1} of ${state.totalDays}",
                        tag = "simulation_day",
                    )
                    MetricLine("Date", state.currentDateIso.ifBlank { "\u2014" }, "simulation_date")
                    MetricLine("Reference time", state.referenceInstant.ifBlank { "\u2014" }, "simulation_reference")
                    MetricLine("Available windows", state.availableWindows.toString(), "simulation_windows")
                    if (state.lostMinutes > 0) {
                        MetricLine("Time lost to interruption", "$state.lostMinutes min", "simulation_interruption")
                    }
                    MetricLine("Processing mode", state.processingMode, "simulation_processing_mode")
                    MetricLine(
                        "Scenario",
                        state.scenarioNote.ifBlank { "No scenario note for this day." },
                        "simulation_note",
                    )
                }
            }

            item { SectionTitle("Task pool") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MetricLine("Task definitions", state.taskDefinitionCount.toString(), "simulation_definitions")
                    MetricLine("Task instances", state.taskInstanceCount.toString(), "simulation_instances")
                    MetricLine("Currently active", state.activeTaskCount.toString(), "simulation_active")
                    MetricLine("Fixed commitments", state.fixedCommitmentCount.toString(), "simulation_fixed")
                }
            }

            item { SectionTitle("Day result") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MetricLine("Scheduled", state.scheduledCount.toString(), "simulation_scheduled")
                    MetricLine("Completed today", state.completedCount.toString(), "simulation_completed")
                    MetricLine("Missed today", state.missedCount.toString(), "simulation_missed")
                    MetricLine("Postponed in pool", state.postponedCount.toString(), "simulation_postponed")
                    MetricLine("Could not be placed", state.unscheduledCount.toString(), "simulation_unscheduled")
                    MetricLine("Overdue", state.overdueCount.toString(), "simulation_overdue")
                    MetricLine("Deadline at risk", state.atRiskCount.toString(), "simulation_at_risk")
                    MetricLine("Conflicts", state.conflictCount.toString(), "simulation_conflicts")
                    MetricLine("Revisions applied", state.revisionCount.toString(), "simulation_revisions")
                }
            }

            item {
                TestRunBlock(
                    passed = state.checksPassed,
                    failed = state.checksFailed,
                    runAt = state.checksRunAt,
                )
            }

            if (state.days.isEmpty()) {
                item {
                    EmptyState(
                        title = "No simulation loaded",
                        message = "Choose a scenario and tap Load to create the task pool.",
                        icon = Icons.Outlined.Science,
                    )
                }
            } else {
                item { SectionTitle("Days (${state.days.size})") }
                items(state.days, key = { it.dayOffset }) { day -> DayRow(day) }
            }

            if (state.events.isNotEmpty()) {
                item { SectionTitle("Change history (${state.events.size})") }
                items(state.events.takeLast(EVENT_LIMIT).reversed(), key = { "${it.dayOffset}-${it.detail.hashCode()}" }) { event ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics {
                                testTag = "simulation_event_${event.kind.name.lowercase()}"
                                contentDescription = "Day ${event.dayOffset + 1}, ${event.kind.label()}: ${event.detail}"
                            },
                    ) {
                        Text(
                            text = "Day ${event.dayOffset + 1} \u00b7 ${event.kind.label()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = event.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.events.size > EVENT_LIMIT) {
                    item {
                        Text(
                            text = "\u2026 and ${state.events.size - EVENT_LIMIT} earlier entries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The host-test block.
 *
 * States plainly that these numbers come from a run on the development machine. Claiming an
 * on-device test run here would be the single most misleading thing this screen could do.
 */
@Composable
private fun TestRunBlock(passed: Int?, failed: Int?, runAt: String?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Test run (executed on the development machine)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = "Host test run results" },
        )
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
            MetricLine("Passed", passed.toString(), "simulation_tests_passed")
            MetricLine("Failed", failed.toString(), "simulation_tests_failed")
            MetricLine("Run at", runAt ?: "unknown", "simulation_tests_run_at")
        }
    }
}

@Composable
private fun DayRow(day: SimulationDayReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                testTag = "simulation_day_${day.dayOffset}"
                contentDescription = "Day ${day.dayOffset + 1}, ${day.dateIso}: " +
                    "${day.scheduledTaskCount} scheduled, ${day.unscheduledTaskCount} unplaced"
            },
    ) {
        Text(
            text = "Day ${day.dayOffset + 1} \u00b7 ${day.dateIso}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${day.scheduledTaskCount} scheduled, ${day.completedTaskCount} done, " +
                "${day.missedTaskCount} missed, ${day.unscheduledTaskCount} unplaced, " +
                "${day.overdueTaskCount} overdue, ${day.atRiskTaskCount} at risk " +
                "(${day.runtimeMillis} ms)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day.scenarioNote.isNotBlank()) {
            Text(
                text = day.scenarioNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun MetricLine(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { testTag = tag },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private const val EVENT_LIMIT = 60

@Preview(showBackground = true)
@Composable
private fun SimulationScreenPreview() {
    ChronaTheme { Surface { SimulationScreen(SimulationUiState(isLoading = false), {}, {}, {}, {}, {}, {}) } }
}
