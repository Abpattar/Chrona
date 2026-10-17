package com.chrona.app.ui.today

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.navigation.TaskEntryRequests
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
 *
 * Voice is not owned here: the shell holds the capture state machine, because the microphone is
 * shared by every destination. Today only reacts to its two results — tasks that were created, and
 * a request to open the typed editor.
 */
@Composable
fun TodayRoute(
    container: AppContainer,
    requests: TaskEntryRequests = TaskEntryRequests(),
    onTextEntryHandled: () -> Unit = {},
    onOpenQuestCards: () -> Unit = {},
) {
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

    // A spoken task is a new task, so Today reloads from the same repository.
    LaunchedEffect(requests.createdCount) {
        if (requests.createdCount > 0) viewModel.refresh()
    }

    // "Type instead" from any screen lands here, prefilled with whatever was heard.
    val prefill = requests.textPrefill
    LaunchedEffect(prefill) {
        if (prefill != null) {
            viewModel.openEditor(prefill)
            onTextEntryHandled()
        }
    }

    TodayScreen(
        state = state,
        onCreate = viewModel::submitTask,
        onStart = viewModel::start,
        onComplete = viewModel::complete,
        onPostpone = viewModel::postpone,
        onEditorOpen = { viewModel.openEditor() },
        onEditorClose = viewModel::closeEditor,
        onOpenQuestCards = onOpenQuestCards,
    )
}