package com.chrona.app.platform.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The P06 runtime permission, named once so the manifest, the request flow and the availability
 * checks cannot drift apart (ERROR_HANDLING_AND_SECURITY.md §Required controls).
 *
 * The overlay's `SYSTEM_ALERT_WINDOW` is a special access, not a runtime permission: it is checked
 * with `Settings.canDrawOverlays` and granted only in system settings, so it is not listed here.
 */
object ReminderPermissions {
    /**
     * Runtime permission on API 33+; on older APIs notifications need no runtime grant. The
     * constant is a plain string, so referencing it on older devices is safe.
     */
    @Suppress("InlinedApi")
    const val POST_NOTIFICATIONS: String = Manifest.permission.POST_NOTIFICATIONS
}

/**
 * True when a notification posted right now could actually reach the user.
 *
 * This is the honest check the reminder policy depends on: it folds in both the API 33+ runtime
 * grant and the app-level notification toggle, so a user who has switched Chrona's notifications
 * off is treated as "no notification surface" — not as a delivery that silently vanished.
 */
fun Context.notificationsAvailable(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPostNotificationsPermission())

/** Narrow runtime check, separate from the app-level toggle above. */
fun Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, ReminderPermissions.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** True only when the user has granted the special overlay access. */
fun Context.canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)
