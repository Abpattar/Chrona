package com.chrona.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.chrona.app.domain.model.Priority

/**
 * Priority presentation.
 *
 * The accent reinforces the label and never replaces it: [label] and [iconDescription] carry the
 * same information as words, so every list stays readable without colour (UI-002, ACC-001).
 *
 * Tones are chosen against the charcoal ground: neutral for ordinary work, the warm accent for
 * important work, and the thread red reserved for what genuinely cannot slip.
 */
data class PriorityStyle(
    val label: String,
    val iconDescription: String,
    val accent: Color,
)

@Composable
@ReadOnlyComposable
fun priorityStyleOf(priority: Priority): PriorityStyle = when (priority) {
    Priority.LOW -> PriorityStyle(
        label = "Low priority",
        iconDescription = "Low priority note",
        accent = ChronaColors.InkMuted,
    )

    Priority.IMPORTANT -> PriorityStyle(
        label = "Important",
        iconDescription = "Important note",
        accent = ChronaColors.Amber,
    )

    Priority.CRITICAL -> PriorityStyle(
        label = "Very important",
        iconDescription = "Very important note",
        accent = ChronaColors.Thread,
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
        Priority.LOW -> ChronaColors.InkMuted
        Priority.IMPORTANT -> ChronaColors.Amber
        Priority.CRITICAL -> ChronaColors.Thread
    }