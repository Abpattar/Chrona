package com.chrona.app.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.chrona.app.ChronaApplication
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs a Quest Card action tapped on a notification (CH-UI-001, CARD-003).
 *
 * A broadcast receiver has no lifecycle, so the work is handed to `goAsync()` with a background
 * scope. The action routes through the same [com.chrona.app.usecase.ApplyReminderActionUseCase]
 * as every other surface: DISMISS writes presentation history only and never touches task state.
 * The notification is cancelled once the action is applied, so a stale card cannot be tapped twice.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)?.let {
            runCatching { TaskAction.valueOf(it) }.getOrNull()
        } ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val cardId = intent.getStringExtra(EXTRA_CARD_ID)

        val application = context.applicationContext as? ChronaApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                application.container.applyReminderAction(
                    taskId = TaskId(taskId),
                    action = action,
                    surface = ReminderSurfaceType.NOTIFICATION,
                    cardId = cardId,
                )
            } finally {
                NotificationManagerCompat.from(context)
                    .cancel(cardId ?: taskId, NotificationPresenter.NOTIFICATION_ID)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION: String = "com.chrona.app.extra.ACTION"
        const val EXTRA_TASK_ID: String = "com.chrona.app.extra.TASK_ID"
        const val EXTRA_CARD_ID: String = "com.chrona.app.extra.CARD_ID"
    }
}
