package com.chrona.app.platform.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chrona.app.R
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderPresenter
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import com.chrona.app.platform.permission.notificationsAvailable

/**
 * The dependable reminder surface (CH-PLAT-003).
 *
 * An ordinary notification with the four Quest Card actions. Identity is the card id, so a
 * re-delivered card replaces the previous notification instead of stacking duplicates. Posting is
 * wrapped in `try/catch`: a revoked permission or an OEM quirk returns null, which the caller
 * treats as a fallback, never as an error (ADR-009).
 */
class NotificationPresenter(
    private val context: Context,
) : ReminderPresenter {

    override val surface: ReminderSurfaceType = ReminderSurfaceType.NOTIFICATION

    override suspend fun isAvailable(): Boolean = context.notificationsAvailable()

    // `notificationsAvailable()` is the explicit permission + toggle check; the surrounding
    // try/catch also handles SecurityException if the grant is revoked between check and notify.
    @SuppressLint("MissingPermission")
    override suspend fun present(card: ReminderCard): ReminderSurfaceType? {
        if (!context.notificationsAvailable()) return null
        return try {
            ensureChannels()
            val manager = NotificationManagerCompat.from(context)
            manager.notify(card.cardId, NOTIFICATION_ID, build(card))
            ReminderSurfaceType.NOTIFICATION
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Includes SecurityException when POST_NOTIFICATIONS was revoked mid-flight.
            null
        }
    }

    private fun build(card: ReminderCard): android.app.Notification {
        val urgent = card.tasks.any { it.priority != Priority.LOW }
        val title = if (card.tasks.size == 1) {
            card.tasks.first().title
        } else {
            context.getString(R.string.channel_reminders_urgent_name) + ": ${card.tasks.size} tasks"
        }
        val summary = card.tasks.joinToString(separator = "\n") { "• ${it.title}" }

        return NotificationCompat.Builder(context, channelId(urgent))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(card))
            .setAutoCancel(false)
            // Every task in a card shares the card id, so its actions act on the primary task.
            .addAction(action(card, card.tasks.first().id.value, TaskAction.START, android.R.drawable.ic_media_play))
            .addAction(action(card, card.tasks.first().id.value, TaskAction.DONE, android.R.drawable.checkbox_on_background))
            .addAction(action(card, card.tasks.first().id.value, TaskAction.LATER, android.R.drawable.ic_menu_recent_history))
            .addAction(action(card, card.tasks.first().id.value, TaskAction.DISMISS, android.R.drawable.ic_menu_close_clear_cancel))
            .build()
    }

    private fun action(
        card: ReminderCard,
        taskId: String,
        action: TaskAction,
        iconRes: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            putExtra(ReminderActionReceiver.EXTRA_ACTION, action.name)
            putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderActionReceiver.EXTRA_CARD_ID, card.cardId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (card.cardId + action.name).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action(iconRes, context.getString(labelFor(action)), pending)
    }

    private fun openAppIntent(card: ReminderCard): PendingIntent {
        val intent = Intent(context, com.chrona.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_TASK, card.tasks.first().id.value)
        }
        return PendingIntent.getActivity(
            context,
            (card.cardId + "open").hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannels() {
        // minSdk is 26, so notification channels always exist; createNotificationChannelsCompat
        // is a safe no-op on any API that somehow lacks them.
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_URGENT, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_reminders_urgent_name))
                    .setDescription(context.getString(R.string.channel_reminders_urgent_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_QUIET, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_reminders_quiet_name))
                    .setDescription(context.getString(R.string.channel_reminders_quiet_desc))
                    .build(),
            ),
        )
    }

    private fun channelId(urgent: Boolean): String = if (urgent) CHANNEL_URGENT else CHANNEL_QUIET

    private fun labelFor(action: TaskAction): Int = when (action) {
        TaskAction.START -> R.string.action_start
        TaskAction.DONE -> R.string.action_done
        TaskAction.LATER -> R.string.action_later
        TaskAction.DISMISS -> R.string.action_dismiss
    }

    companion object {
        /** Stable id within the card-id tag, so a card replaces rather than duplicates. */
        const val NOTIFICATION_ID: Int = 4001

        const val CHANNEL_URGENT: String = "chrona_reminders_urgent"
        const val CHANNEL_QUIET: String = "chrona_reminders_quiet"

        /** Deep-link hint carried by the content intent; ignored until a task detail route exists. */
        const val EXTRA_FOCUS_TASK: String = "com.chrona.app.extra.FOCUS_TASK"
    }
}
