package com.chrona.app.ui.taskbrowser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import com.chrona.app.di.allTaskLoader
import com.chrona.app.domain.model.map
import com.chrona.app.usecase.BuildDayProposalUseCase
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Binds [TaskBrowserViewModel] to [TaskBrowserScreen].
 *
 * The browser joins the same proposal the Schedule screen shows, built through the same use case.
 * Two screens reading the same builder is what keeps them from disagreeing about placements.
 */
@Composable
fun TaskBrowserRoute(container: AppContainer) {
    val zoneId = remember { ZoneId.systemDefault() }
    val buildProposal = remember(container, zoneId) {
        BuildDayProposalUseCase(
            loadTasks = container.allTaskLoader(),
            loadCommitments = { from: ZonedDateTime, to: ZonedDateTime -> container.events.list(from, to) },
            loadRelationships = { container.relationshipRepository.listAll() },
            loadPreferences = { container.preferencesRepository.load() },
            loadRevision = { container.scheduleRevisionRepository.current() },
            engine = container.scheduleEngine,
            now = { ZonedDateTime.now(zoneId) },
            zone = zoneId,
        )
    }

    val viewModel: TaskBrowserViewModel = viewModel(
        factory = TaskBrowserViewModel.Factory(
            loadTasks = { query -> container.taskRepository.list(query) },
            loadProposal = { buildProposal().map { it.proposal } },
            zoneId = zoneId,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshOnEntry() }

    TaskBrowserScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onClearMessage = viewModel::clearMessage,
    )
}
