package com.chrona.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import java.time.Instant
import java.time.ZoneId

/**
 * Binds [HistoryViewModel] to [HistoryScreen].
 *
 * Reads the same revision and status tables production writes to. There is no sample data and no
 * placeholder: an empty screen means nothing has happened yet, which is the truth.
 */
@Composable
fun HistoryRoute(container: AppContainer) {
    val zoneId = remember { ZoneId.systemDefault() }

    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(
            loadRevisions = { container.scheduleRevisionRepository.history() },
            loadStatusLog = { limit ->
                com.chrona.app.domain.model.Outcome.Success(
                    container.database.taskStatusLogDao()
                        .findRecentWithTitles(limit)
                        .map { row ->
                            TaskHistoryEntry(
                                taskId = row.taskId,
                                taskTitle = row.taskTitle,
                                fromStatus = row.fromStatus,
                                toStatus = row.toStatus,
                                reasonCode = row.reasonCode,
                                changedAt = Instant.ofEpochMilli(row.changedAtEpochMillis),
                            )
                        },
                )
            },
            loadClosedTasks = { query -> container.taskRepository.list(query) },
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshOnEntry() }

    HistoryScreen(state = state, zoneId = zoneId)
}
