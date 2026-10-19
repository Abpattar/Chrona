package com.chrona.app.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import com.chrona.app.usecase.BuildDayProposalUseCase
import com.chrona.app.di.allTaskLoader
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Binds [ScheduleViewModel] to [ScheduleScreen].
 *
 * The composition root is consulted only here, so the screen stays pure and previewable, and
 * planning is the same deterministic engine used everywhere else.
 */
@Composable
fun ScheduleRoute(container: AppContainer) {
    val zoneId = remember { ZoneId.systemDefault() }

    // One builder, shared with the task browser, so the two can never disagree about what day it
    // is or which hours are available.
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

    val viewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.Factory(
            buildProposal = { buildProposal() },
            apply = { proposal, expected, reason -> container.applyRecovery(proposal, expected, reason) },
            undo = { id -> container.undoSchedule(id) },
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshOnEntry() }

    ScheduleScreen(
        state = state,
        onApply = viewModel::applyProposal,
        onUndo = viewModel::undoLast,
        onRefresh = viewModel::refresh,
        onClearMessage = viewModel::clearMessage,
    )
}
