package com.chrona.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.chrona.app.domain.model.Priority

/**
 * Priority presentation (APP_VISUAL_SPEC.md §Priority note treatments).
 *
 * Yellow = low/side, blue = important, green = very important. The accent colour is only ever
 * a reinforcement: [label] and [iconDescription] carry the same information as words, so
 * every list stays readable without colour (UI-002, ACC-001).
 */
data class PriorityStyle(
    val label: String,
    val iconDescription: String,
    val accent: Color,
)

/**
 * Resolves a priority to its presentation.
 *
 * The low-priority note deliberately takes the theme's cork tone so it reads as "ordinary
 * paper" rather than a third accent colour.
 */
@Composable
@ReadOnlyComposable
fun priorityStyleOf(priority: Priority): PriorityStyle = when (priority) {
    Priority.LOW -> PriorityStyle(
        label = "Low priority",
        iconDescription = "Low priority note",
        accent = ChronaTheme.paper.cork,
    )

    Priority.IMPORTANT -> PriorityStyle(
        label = "Important",
        iconDescription = "Important note",
        accent = Color(0xFFBFD4E8),
    )

    Priority.CRITICAL -> PriorityStyle(
        label = "Very important",
        iconDescription = "Very important note",
        accent = Color(0xFFB9DCB4),
    )
}

/** Compact label used on the editor's priority chips. */
fun Priority.shortLabel(): String = when (this) {
    Priority.LOW -> "Low"
    Priority.IMPORTANT -> "Important"
    Priority.CRITICAL -> "Very important"
}

/** Accent for a priority without needing composition context (previews, tests). */
val Priority.accentColor: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Priority.LOW -> ChronaTheme.paper.cork
        Priority.IMPORTANT -> Color(0xFFBFD4E8)
        Priority.CRITICAL -> Color(0xFFB9DCB4)
    }
