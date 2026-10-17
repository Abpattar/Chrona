package com.chrona.app.platform.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chrona.app.ChronaApplication
import com.chrona.app.domain.model.Outcome
import com.chrona.app.usecase.DeliverRemindersUseCase
import java.time.Duration

/**
 * Periodic reminder reconciliation (NOTIFY-001, ADR-019).
 *
 * A single inexact periodic job re-runs the same deterministic evaluation the UI uses. It is
 * deliberately inexact and low-frequency: reminders are "come back to this soon", not alarms, so
 * battery and Doze are respected over punctuality. The job depends on the composition root through
 * the application, so background delivery uses exactly the same policy, history and presenters.
 */
class ReminderReconciliationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? ChronaApplication ?: return Result.success()
        return when (application.container.deliverReminders()) {
            is Outcome.Success -> Result.success()
            // Storage was unavailable this run; a later run retries with backoff. Nothing is lost.
            is Outcome.Failed -> Result.retry()
        }
    }

    companion object {
        /** Hourly is plenty for a "come back to this" nudge, and stays well within Doze windows. */
        private val INTERVAL: Duration = Duration.ofHours(1)

        /**
         * Schedules the one reconciliation job, keyed by a stable unique name.
         *
         * `KEEP` means an already-scheduled job is left untouched, so calling this on every app
         * start neither resets the interval nor stacks duplicates.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReconciliationWorker>(INTERVAL).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DeliverRemindersUseCase.REMINDER_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
