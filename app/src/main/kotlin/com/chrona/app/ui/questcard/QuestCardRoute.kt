package com.chrona.app.ui.questcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.reminder.TaskAction

/** Binds [QuestCardViewModel] to [QuestCardScreen] (UI-001). */
@Composable
fun QuestCardRoute(container: AppContainer) {
    val viewModel: QuestCardViewModel = viewModel(
        factory = QuestCardViewModel.Factory(
            evaluate = { notificationAvailable, overlayAvailable ->
                container.evaluateReminders(notificationAvailable, overlayAvailable)
            },
            recordSurfaced = { card, surface -> container.recordReminderHistory.surfaced(card, surface) },
            applyAction = { taskId, action, surface, cardId ->
                container.applyReminderAction(taskId, action, surface, cardId)
            },
            notificationsAvailable = { container.notificationPresenter.isAvailable() },
            overlayAvailable = { container.overlayPresenter.isAvailable() },
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    QuestCardScreen(
        state = state,
        onStart = { viewModel.act(TaskAction.START) },
        onDone = { viewModel.act(TaskAction.DONE) },
        onLater = { viewModel.act(TaskAction.LATER) },
        onDismiss = { viewModel.act(TaskAction.DISMISS) },
        onRefresh = viewModel::refresh,
    )
}
