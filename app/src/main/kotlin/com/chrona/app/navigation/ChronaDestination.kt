package com.chrona.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.chrona.app.R

/**
 * Canonical route identifiers. Route strings live here only, so navigation IDs
 * have a single owner and screen code never repeats a literal.
 */
enum class ChronaDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY("today", R.string.nav_today, Icons.Outlined.Dashboard),
    SCHEDULE("schedule", R.string.nav_schedule, Icons.Outlined.CalendarMonth),
    BOARD("board", R.string.nav_board, Icons.AutoMirrored.Outlined.ViewQuilt),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}