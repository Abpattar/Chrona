package com.chrona.app.ui.today

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.usecase.NewEvent
import com.chrona.app.usecase.NewTask
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Binds [TodayViewModel] to [TodayScreen].
 *
 * This is the only place the composition root is consulted by a screen, so the screen itself
 * stays free of storage and clock details and remains previewable in isolation.
 */
@Composable
fun TodayRoute(container: AppContainer) {
    val zoneId = remember { ZoneId.systemDefault() }
    val viewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(
            listTasks = { query: TaskQuery -> container.listTasks(query) },
            createTask = { request: NewTask -> container.createTask(request) },
            completeTask = { id -> container.lifecycle.complete(id) },
            startTask = { id -> container.lifecycle.start(id) },
            postponeTask = { id -> container.lifecycle.postpone(id, null) },
            listCommitments = { from: Instant, to: Instant -> container.events.list(
                Instant.ofEpochMilli(from.toEpochMilli()).atZone(zoneId),
                Instant.ofEpochMilli(to.toEpochMilli()).atZone(zoneId),
            ) },
            createCommitment = { request: NewEvent -> container.events.create(request) },
            clock = Clock.systemUTC(),
            zoneId = zoneId,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onCreate = viewModel::submitTask,
        onStart = viewModel::start,
        onComplete = viewModel::complete,
        onPostpone = viewModel::postpone,
        onEditorOpen = viewModel::openEditor,
        onEditorClose = viewModel::closeEditor,
    )
}