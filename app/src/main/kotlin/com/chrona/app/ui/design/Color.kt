package com.chrona.app.ui.design

import androidx.compose.ui.graphics.Color

/**
 * Chrona palette (APP_VISUAL_SPEC.md).
 *
 * Warm paper and cork tones for surfaces; restrained note accents for priority.
 * Colour is never the only carrier of meaning: every priority token is paired with
 * a text label by [PriorityStyle], so the UI stays readable without colour.
 */
internal object ChronaColors {
    // Surfaces — warm paper / cork.
    val Paper = Color(0xFFF7ECDC)
    val PaperRaised = Color(0xFFFFFAF1)
    val PaperShaded = Color(0xFFEADCC6)
    val Cork = Color(0xFFD8BE93)

    // Ink.
    val Ink = Color(0xFF3A2E22)
    val InkMuted = Color(0xFF6B5B4A)
    val Outline = Color(0xFFBFAE95)

    // Relationship thread — thin red string.
    val Thread = Color(0xFFC1666B)

    // Dark scheme keeps the same hue family at lower luminance.
    val PaperDark = Color(0xFF1C1B19)
    val PaperRaisedDark = Color(0xFF2A2723)
    val PaperShadedDark = Color(0xFF151412)
    val CorkDark = Color(0xFF4A3E2E)
    val InkDark = Color(0xFFF2E7D8)
    val InkMutedDark = Color(0xFFC0B2A1)
    val OutlineDark = Color(0xFF5A4E40)
}