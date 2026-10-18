package com.chrona.app.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.TaskQuery
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
    val viewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.Factory(
            loadTasks = { container.listTasks(TaskQuery.ACTIVE) },
            loadCommitments = { from: ZonedDateTime, to: ZonedDateTime -> container.events.list(from, to) },
            loadRelationships = { container.relationshipRepository.listAll() },
            currentRevision = { container.scheduleRevisionRepository.current() },
            plan = { input -> container.scheduleEngine.propose(input) },
            apply = { proposal, expected, reason -> container.applyRecovery(proposal, expected, reason) },
            undo = { id -> container.undoSchedule(id) },
            now = { ZonedDateTime.now(zoneId) },
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
