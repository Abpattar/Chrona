package com.chrona.app.ui.design

import androidx.compose.ui.graphics.Color

/**
 * Chrona palette.
 *
 * Deep charcoal ground, warm amber as the single accent, graphite surfaces, and hairline
 * borders that separate layers without adding light. Nothing is brighter than it needs to be:
 * the loudest element on any screen is the microphone.
 *
 * Colour is never the only carrier of meaning: every priority token is paired with a text label
 * by [priorityStyleOf], so the UI stays readable without colour (ACC-001).
 */
internal object ChronaColors {
    // Ground — near-black with a warm bias so amber sits on it rather than against it.
    val Backdrop = Color(0xFF0C0B0A)
    val Paper = Color(0xFF15130F)
    val PaperRaised = Color(0xFF1E1B17)
    val PaperShaded = Color(0xFF100E0C)

    // Accent — warm amber, one hue, three steps for emphasis and glow.
    val Amber = Color(0xFFF2A33C)
    val AmberBright = Color(0xFFFFC46B)
    val AmberDeep = Color(0xFFC9761B)

    // Glass — translucent graphite for floating controls and panels.
    val Glass = Color(0xCC1C1915)
    val GlassStrong = Color(0xF21C1915)
    val GlassBorder = Color(0x33FFC46B)

    // Ink.
    val Ink = Color(0xFFF6F1E8)
    val InkMuted = Color(0xFFA79C8C)
    val Outline = Color(0x38FFC46B)

    // Board cork reads as a graphite felt now; the pin keeps the warm accent.
    val Cork = Color(0xFF24201B)

    // Relationship thread — thin warm red string.
    val Thread = Color(0xFFE0705F)

    // Feedback.
    val Danger = Color(0xFFE2705F)
    val Success = Color(0xFF7FC08A)

    val Scrim = Color(0xFF000000)
}