package com.chrona.app.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.chrona.app.R
import com.chrona.app.domain.reminder.ReminderCard
import com.chrona.app.domain.reminder.ReminderPresenter
import com.chrona.app.domain.reminder.ReminderSurfaceType
import com.chrona.app.domain.reminder.TaskAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The optional floating Quest Card (CH-PLAT-002, ADR-009).
 *
 * Best-effort by design. It is used only when the user enabled it *and* granted "display over
 * other apps"; `present` returns null on any failure (permission revoked, background window
 * restriction, OEM block), and the caller falls back to a notification. The panel is a plain
 * programmatic view posted through `WindowManager`, holds no state of its own, and changes task
 * state only by invoking the same action callback every other surface uses.
 */
class OverlayPresenter(
    private val context: Context,
    private val onAction: (TaskAction, ReminderCard) -> Unit,
) : ReminderPresenter {

    override val surface: ReminderSurfaceType = ReminderSurfaceType.OVERLAY

    private var view: View? = null

    override suspend fun isAvailable(): Boolean = Settings.canDrawOverlays(context)

    override suspend fun present(card: ReminderCard): ReminderSurfaceType? =
        withContext(Dispatchers.Main) {
            if (!Settings.canDrawOverlays(context)) return@withContext null
            try {
                val windowManager = context.getSystemService(WindowManager::class.java)
                    ?: return@withContext null
                removeCurrent(windowManager)
                val panel = buildView(card)
                windowManager.addView(panel, layoutParams())
                view = panel
                ReminderSurfaceType.OVERLAY
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Background overlay windows are restricted on modern Android; a failure here is
                // an expected fallback path, not an error.
                null
            }
        }

    /** Removes the panel if one is showing. Safe to call from the main thread only. */
    fun hide() {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return
        removeCurrent(windowManager)
    }

    private fun removeCurrent(windowManager: WindowManager) {
        view?.let { existing ->
            runCatching { windowManager.removeView(existing) }
        }
        view = null
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(96)
    }

    private fun buildView(card: ReminderCard): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(PANEL_COLOR)
                cornerRadius = dp(12).toFloat()
            }
        }
        root.addView(textView(if (card.tasks.size == 1) card.tasks.first().title else "Quest Card", 16f))
        card.tasks.drop(if (card.tasks.size == 1) 1 else 0).forEach { task ->
            root.addView(textView(task.title, 13f))
        }

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (action in ACTIONS) {
            row.addView(
                Button(context).apply {
                    text = context.getString(labelFor(action))
                    setOnClickListener {
                        hide()
                        // Act on the card's primary task; a card is batched around one focus.
                        onAction(action, card)
                    }
                },
            )
        }
        root.addView(row)
        return root
    }

    private fun textView(text: String, sizeSp: Float): TextView = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(TEXT_COLOR)
        setPadding(0, 0, 0, dp(4))
    }

    private fun labelFor(action: TaskAction): Int = when (action) {
        TaskAction.START -> R.string.action_start
        TaskAction.DONE -> R.string.action_done
        TaskAction.LATER -> R.string.action_later
        TaskAction.DISMISS -> R.string.action_dismiss
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        val ACTIONS = listOf(TaskAction.START, TaskAction.DONE, TaskAction.LATER, TaskAction.DISMISS)
        const val PANEL_COLOR = 0xF2FFFFFF.toInt()
        const val TEXT_COLOR = 0xFF1A1C1E.toInt()
    }
}
