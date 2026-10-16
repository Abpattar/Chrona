package com.chrona.app.ui.today

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.usecase.NewEvent
import com.chrona.app.usecase.NewTask
import com.chrona.app.ui.voice.VoiceCaptureSheet
import com.chrona.app.ui.voice.VoiceCaptureViewModel
import com.chrona.app.ui.voice.VoiceUiState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Binds [TodayViewModel] to [TodayScreen].
 *
 * This is the only place the composition root is consulted by a screen, so the screen itself
 * stays free of storage and clock details and remains previewable in isolation.
 */
@Composable
fun TodayRoute(container: AppContainer) {
    val zoneId = remember { ZoneId.systemDefault() }
    // The recogniser is asked for the device language. Chrona only parses English, so a
    // non-English transcript is refused for the user rather than misread (P03 limitation).
    val localeTag = remember { Locale.getDefault().toLanguageTag() }
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

    // Voice is a second state holder over the same repositories: it is never allowed to write
    // anything the typed path could not write, only with `origin = VOICE`.
    val voiceViewModel: VoiceCaptureViewModel = viewModel(
        factory = VoiceCaptureViewModel.Factory(
            speech = container.speechRecognition,
            parser = container.taskUnderstanding,
            createTaskFromIntent = { draft -> container.createTaskFromIntent(draft) },
            timeSource = container.timeSource,
            zoneId = zoneId,
            localeTag = localeTag,
            parsePreferences = container.parsePreferences,
        ),
    )
    val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()

    // A confirmed voice capture is a new task, so Today reloads from the same repository.
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceUiState.Saved) viewModel.refresh()
    }

    // Any non-idle state is an open capture sheet. There is no other way in, and no way back
    // into listening without an explicit tap.
    if (voiceState !is VoiceUiState.Closed) {
        VoiceCaptureSheet(
            state = voiceState,
            onStart = voiceViewModel::start,
            onPermissionResult = voiceViewModel::onPermissionResult,
            onAnswer = voiceViewModel::answer,
            onEditTitle = voiceViewModel::editTitle,
            onEditDuration = voiceViewModel::editDurationMinutes,
            onEditDate = voiceViewModel::editDate,
            onConfirm = voiceViewModel::confirm,
            onRetry = voiceViewModel::retry,
            onCancel = voiceViewModel::cancel,
            onReset = voiceViewModel::reset,
            // The typed fallback is always reachable, and it starts from what was heard.
            onTypeInstead = { prefill ->
                voiceViewModel.close()
                viewModel.openEditor(prefill)
            },
        )
        return
    }

    TodayScreen(
        state = state,
        onCreate = viewModel::submitTask,
        onStart = viewModel::start,
        onComplete = viewModel::complete,
        onPostpone = viewModel::postpone,
        onEditorOpen = { viewModel.openEditor() },
        onEditorClose = viewModel::closeEditor,
        onSpeak = voiceViewModel::open,
    )
}