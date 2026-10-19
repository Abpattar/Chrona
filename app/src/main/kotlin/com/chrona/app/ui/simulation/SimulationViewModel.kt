package com.chrona.app.ui.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.simulation.LifeSimulationEngine
import com.chrona.app.domain.simulation.SimulationDayReport
import com.chrona.app.domain.simulation.SimulationDataset
import com.chrona.app.domain.simulation.SimulationEvent
import com.chrona.app.domain.simulation.SimulationScenario
import com.chrona.app.domain.simulation.SimulationScenarios
import com.chrona.app.domain.simulation.SimulationStore
import com.chrona.app.domain.simulation.SimulationTaskFactory
import com.chrona.app.usecase.describe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SimulationUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val scenarioId: String = SimulationScenarios.FULL_LIFE.id,
    val scenarioName: String = SimulationScenarios.FULL_LIFE.displayName,
    val totalDays: Int = SimulationScenarios.FULL_LIFE.clock.totalDays,
    /** Zero-based index of the simulated day; -1 before the first step. */
    val currentDay: Int = -1,
    val currentDateIso: String = "",
    /** Reference time on the simulated clock, not the device clock. */
    val referenceInstant: String = "",
    val taskDefinitionCount: Int = 0,
    val taskInstanceCount: Int = 0,
    val activeTaskCount: Int = 0,
    val scheduledCount: Int = 0,
    val completedCount: Int = 0,
    val missedCount: Int = 0,
    val postponedCount: Int = 0,
    val unscheduledCount: Int = 0,
    val overdueCount: Int = 0,
    val atRiskCount: Int = 0,
    val fixedCommitmentCount: Int = 0,
    val conflictCount: Int = 0,
    val revisionCount: Int = 0,
    val availableWindows: Int = 0,
    val lostMinutes: Int = 0,
    val scenarioNote: String = "",
    val processingMode: String = LifeSimulationEngine.PROCESSING_MODE_DETERMINISTIC,
    val days: List<SimulationDayReport> = emptyList(),
    val events: List<SimulationEvent> = emptyList(),
    val message: String? = null,
    /** Genuine assertion counts from the host run, or "not run" when nothing has been executed. */
    val checksPassed: Int? = null,
    val checksFailed: Int? = null,
    val checksRunAt: String? = null,
) {
    val isComplete: Boolean get() = currentDay >= totalDays - 1
    val canAdvance: Boolean get() = !isWorking && !isComplete
    val isStarted: Boolean get() = currentDay >= 0
}

/**
 * Drives the in-app life simulation.
 *
 * Everything it shows is counted from persisted state, never declared. The processing mode is
 * taken from the engine that produced the plan, so the screen cannot claim AI was used when the
 * deterministic path ran — and cannot claim it was not when it was.
 *
 * The test-run block is deliberately separate from the simulation block: the app can advance a
 * simulated life, but it cannot execute Gradle instrumentation on the device. Those are reported
 * from an imported host run, clearly labelled as such.
 */
class SimulationViewModel(
    private val store: SimulationStore,
    private val engine: LifeSimulationEngine,
    /**
     * Where planning runs.
     *
     * Injected rather than hard-coded so a test can substitute the scheduler it controls, and so
     * the choice is visible at the composition root instead of buried in a `withContext`.
     */
    private val planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(SimulationUiState())
    val state: StateFlow<SimulationUiState> = _state.asStateFlow()

    private var hasLoadedOnce = false

    init {
        // Restores whatever the last session persisted, so opening the screen shows the run as
        // it actually stands rather than an empty shell the user has to reload by hand.
        refresh()
    }

    fun refreshOnEntry() {
        if (hasLoadedOnce) refresh() else hasLoadedOnce = true
    }

    /** Reads whatever the last session persisted, so state survives navigation and restart. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val scenario = scenario()
            val days = (store.loadDays() as? Outcome.Success)?.value.orEmpty()
            val events = (store.loadEvents() as? Outcome.Success)?.value.orEmpty()
            val pool = (store.loadAll() as? Outcome.Success)?.value.orEmpty()
            val latest = days.lastOrNull()

            _state.update { current ->
                current.withRun(scenario, days, events).copy(
                    isLoading = false,
                    message = if (days.isEmpty()) {
                        "Load the simulation to create " +
                            "${SimulationTaskFactory.allSpecs(scenario.clock.runId).size} tasks, " +
                            "then advance a day at a time."
                    } else {
                        null
                    },
                )
            }
            if (pool.isNotEmpty() && latest == null) {
                // Tasks exist but no day was recorded. That is the normal state straight after
                // Load, and also what an interrupted run looks like; say which it is rather than
                // showing a day that never happened.
                _state.update {
                    it.copy(
                        message = "Loaded ${pool.size} tasks. Advance a day to see the plan.",
                    )
                }
            }
        }
    }

    /** Loads the scenario from scratch: clears the previous run and creates the task pool. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, isLoading = true) }
            val scenario = scenario()
            // Reset first so "load" means the same thing as "reset then load": no leftovers from a
            // previous scenario can colour this one.
            val cleared = engine.reset(scenario)
            if (cleared is Outcome.Failed) {
                _state.update { it.copy(isWorking = false, isLoading = false, message = describe(cleared.failure)) }
                return@launch
            }
            val seeded = withContext(planningDispatcher) { engine.seed(scenario) }
            if (seeded is Outcome.Failed) {
                _state.update { it.copy(isWorking = false, isLoading = false, message = describe(seeded.failure)) }
                return@launch
            }
            val pool = (store.loadAll() as? Outcome.Success)?.value.orEmpty()
            _state.update {
                it.copy(
                    isWorking = false,
                    isLoading = false,
                    days = (store.loadDays() as? Outcome.Success)?.value.orEmpty(),
                    events = (store.loadEvents() as? Outcome.Success)?.value.orEmpty(),
                    message = "Loaded ${pool.size} tasks. Advance a day to see the plan.",
                )
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val scenario = scenario()
            val result = engine.reset(scenario)
            _state.update {
                when (result) {
                    is Outcome.Success -> SimulationUiState(
                        isLoading = false,
                        scenarioId = scenario.id,
                        scenarioName = scenario.displayName,
                        totalDays = scenario.clock.totalDays,
                        taskDefinitionCount = com.chrona.app.domain.simulation.SimulationDataset.DEFINITIONS.size,
                        taskInstanceCount = SimulationTaskFactory.allSpecs(scenario.clock.runId).size,
                        message = "Simulation data cleared. Your own tasks were not touched.",
                    )

                    is Outcome.Failed -> it.copy(
                        isWorking = false,
                        message = describe(result.failure),
                    )
                }
            }
        }
    }

    /** Advances exactly one simulated day. Replaying the same day is safe and does not duplicate. */
    fun advanceDay() {
        viewModelScope.launch {
            val scenario = scenario()
            // Which day comes next is read from storage, not from the in-memory counter. Planning
            // finishes before the state update lands, so two taps in quick succession would both
            // read the same stale day and re-plan it — which silently discards a day of the run
            // instead of advancing it.
            val lastRecorded = (store.loadDays() as? Outcome.Success)
                ?.value?.maxOfOrNull { it.dayOffset } ?: -1
            val next = maxOf(lastRecorded + 1, _state.value.currentDay + 1)
                .coerceAtMost(scenario.clock.totalDays - 1)
            _state.update { it.copy(isWorking = true) }
            // Planning 140 tasks is not main-thread work, and the UI must stay responsive.
            val result = withContext(planningDispatcher) { engine.advanceTo(scenario, next) }
            when (result) {
                is Outcome.Success -> {
                    val days = (store.loadDays() as? Outcome.Success)?.value.orEmpty()
                    val events = (store.loadEvents() as? Outcome.Success)?.value.orEmpty()
                    _state.update { current ->
                        current.withReport(scenario, result.value.report).copy(
                            isWorking = false,
                            isLoading = false,
                            days = days,
                            events = events,
                            message = result.value.message,
                            processingMode = result.value.processingMode,
                        )
                    }
                }

                is Outcome.Failed -> _state.update {
                    it.copy(isWorking = false, message = describe(result.failure))
                }
            }
        }
    }

    /** Runs the remaining days back to back. Stops at the first failure rather than hiding it. */
    fun runToEnd() {
        viewModelScope.launch {
            val scenario = scenario()
            _state.update { it.copy(isWorking = true) }
            // Same reasoning as advanceDay: storage decides where we are, not stale state.
            val lastRecorded = (store.loadDays() as? Outcome.Success)
                ?.value?.maxOfOrNull { it.dayOffset } ?: -1
            val from = maxOf(lastRecorded, _state.value.currentDay) + 1
            for (day in from until scenario.clock.totalDays) {
                val result = withContext(planningDispatcher) { engine.advanceTo(scenario, day) }
                if (result is Outcome.Failed) {
                    _state.update { it.copy(isWorking = false, message = describe(result.failure)) }
                    return@launch
                }
            }
            val days = (store.loadDays() as? Outcome.Success)?.value.orEmpty()
            val events = (store.loadEvents() as? Outcome.Success)?.value.orEmpty()
            val latest = days.lastOrNull()
            _state.update { current ->
                val folded = if (latest != null) current.withReport(scenario, latest) else current
                folded.copy(
                    isWorking = false,
                    isLoading = false,
                    days = days,
                    events = events,
                    message = "Reached the end of day ${scenario.clock.totalDays}.",
                )
            }
        }
    }

    fun selectScenario(id: String) {
        _state.update { it.copy(scenarioId = id) }
        refresh()
    }

    /** Records a genuine host test run so the screen can report it without inventing one. */
    fun recordHostTestRun(passed: Int, failed: Int, runAt: String) {
        _state.update { it.copy(checksPassed = passed, checksFailed = failed, checksRunAt = runAt) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun scenario(): SimulationScenario =
        SimulationScenarios.byId(_state.value.scenarioId) ?: SimulationScenarios.FULL_LIFE

    class Factory(
        private val store: SimulationStore,
        private val engine: LifeSimulationEngine,
        private val planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SimulationViewModel(store, engine, planningDispatcher) as T
    }
}

/** Overlays the counts of one simulated day onto the screen state. */
private fun SimulationUiState.withReport(
    scenario: SimulationScenario,
    day: SimulationDayReport,
): SimulationUiState = copy(
    scenarioId = scenario.id,
    scenarioName = scenario.displayName,
    totalDays = scenario.clock.totalDays,
    currentDay = day.dayOffset,
    currentDateIso = day.dateIso,
    referenceInstant = day.referenceInstant.toString(),
    activeTaskCount = day.activeTaskCount,
    scheduledCount = day.scheduledTaskCount,
    completedCount = day.completedTaskCount,
    missedCount = day.missedTaskCount,
    postponedCount = day.postponedTaskCount,
    unscheduledCount = day.unscheduledTaskCount,
    overdueCount = day.overdueTaskCount,
    atRiskCount = day.atRiskTaskCount,
    fixedCommitmentCount = day.fixedCommitmentCount,
    conflictCount = day.conflictCount,
    revisionCount = day.revisionCount,
    availableWindows = day.availableWindows,
    lostMinutes = day.lostMinutesToInterruption,
    scenarioNote = day.scenarioNote,
)

/** Restores a whole persisted run, so state survives navigation and an app restart. */
private fun SimulationUiState.withRun(
    scenario: SimulationScenario,
    days: List<SimulationDayReport>,
    events: List<SimulationEvent>,
): SimulationUiState {
    val restored = days.lastOrNull()?.let { withReport(scenario, it) } ?: copy(
        scenarioId = scenario.id,
        scenarioName = scenario.displayName,
        totalDays = scenario.clock.totalDays,
    )
    return restored.copy(
        days = days,
        events = events,
        taskDefinitionCount = SimulationDataset.DEFINITIONS.size,
        taskInstanceCount = SimulationTaskFactory.allSpecs(scenario.clock.runId).size,
    )
}
