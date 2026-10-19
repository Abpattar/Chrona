package com.chrona.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Science
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
    /** Peers appear in the bottom bar; detail surfaces are reached from a peer instead. */
    val isTopLevel: Boolean = true,
) {
    TODAY("today", R.string.nav_today, Icons.Outlined.Dashboard),
    SCHEDULE("schedule", R.string.nav_schedule, Icons.Outlined.CalendarMonth),
    TASKS("tasks", R.string.nav_tasks, Icons.Outlined.Checklist),
    BOARD("board", R.string.nav_board, Icons.AutoMirrored.Outlined.ViewQuilt),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),

    /**
     * The life simulation and its genuine test results.
     *
     * A detail surface rather than a sixth bottom-bar tab: it is a place to *look at* what the
     * planner did, not a peer of Today, and five tabs already fill a phone's width.
     */
    SIMULATION("simulation", R.string.nav_simulation, Icons.Outlined.Science, isTopLevel = false),
    ;

    /** The destinations rendered in the bottom navigation bar, in order. */
    companion object {
        val topLevel: List<ChronaDestination> get() = entries.filter { it.isTopLevel }
    }
}