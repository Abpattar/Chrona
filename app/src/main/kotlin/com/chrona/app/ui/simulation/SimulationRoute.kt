package com.chrona.app.ui.simulation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer

/**
 * Binds [SimulationViewModel] to [SimulationScreen].
 *
 * The store and the engine come straight from the composition root, so the screen runs the same
 * planner the app uses. Only the persistence layer is shared, never a preview copy of the data.
 */
@Composable
fun SimulationRoute(container: AppContainer) {
    val viewModel: SimulationViewModel = viewModel(
        factory = SimulationViewModel.Factory(container.simulationStore, container.simulationEngine),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshOnEntry() }

    SimulationScreen(
        state = state,
        onSelectScenario = viewModel::selectScenario,
        onLoad = viewModel::load,
        onReset = viewModel::reset,
        onAdvanceDay = viewModel::advanceDay,
        onRunToEnd = viewModel::runToEnd,
        onClearMessage = viewModel::clearMessage,
    )
}
