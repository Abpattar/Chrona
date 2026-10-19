package com.chrona.app.ui.design

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * One scheme, deliberately: charcoal ground, amber accent.
 *
 * A light variant would need a second opinion about every surface in `Components.kt`, and the
 * product intent is a single low-light look, so the theme exposes one palette rather than
 * pretending to support two.
 */
private val ChronaColorScheme = darkColorScheme(
    primary = ChronaColors.Amber,
    onPrimary = ChronaColors.Backdrop,
    primaryContainer = ChronaColors.AmberDeep,
    onPrimaryContainer = ChronaColors.Ink,
    secondary = ChronaColors.AmberBright,
    onSecondary = ChronaColors.Backdrop,
    secondaryContainer = ChronaColors.PaperRaised,
    onSecondaryContainer = ChronaColors.Ink,
    tertiary = ChronaColors.Thread,
    onTertiary = ChronaColors.Backdrop,
    background = ChronaColors.Backdrop,
    onBackground = ChronaColors.Ink,
    surface = ChronaColors.Paper,
    onSurface = ChronaColors.Ink,
    surfaceVariant = ChronaColors.PaperRaised,
    onSurfaceVariant = ChronaColors.InkMuted,
    // Tertiary ink. Material has no slot for it, so it rides in the token set below.
    surfaceContainerLow = ChronaColors.Paper,
    surfaceContainer = ChronaColors.PaperRaised,
    surfaceContainerHigh = ChronaColors.PaperShaded,
    outline = ChronaColors.Outline,
    outlineVariant = ChronaColors.GlassBorder,
    error = ChronaColors.Danger,
    onError = ChronaColors.Backdrop,
    scrim = ChronaColors.Scrim,
)

/**
 * Tokens Material's colour scheme has no slot for: the glass surfaces, the warm accent and the
 * ambient glow used by floating controls.
 */
data class ChronaTokens(
    /** Raised graphite surface for cards. */
    val surfaceRaised: Color,
    /** Translucent surface for floating controls. */
    val glass: Color,
    /** Opaque variant, for panels that sit over scrolling content. */
    val glassStrong: Color,
    /** Hairline border that separates a floating control from what is behind it. */
    val glassBorder: Color,
    /** The single warm accent. */
    val accent: Color,
    /** Accent at full strength, for text and icons on a dark ground. */
    val accentBright: Color,
    /** Restrained halo behind the accent, used only while listening. */
    val glow: Color,
    val pin: Color,
    val thread: Color,
    val cork: Color,
    /** Tertiary text: timestamps, separators. Never used for anything a reader must not miss. */
    val inkFaint: Color,
)

private val ChronaTokensValue = ChronaTokens(
    surfaceRaised = ChronaColors.PaperRaised,
    glass = ChronaColors.Glass,
    glassStrong = ChronaColors.GlassStrong,
    glassBorder = ChronaColors.GlassBorder,
    accent = ChronaColors.Amber,
    accentBright = ChronaColors.AmberBright,
    glow = ChronaColors.Amber,
    pin = ChronaColors.Amber,
    thread = ChronaColors.Thread,
    cork = ChronaColors.Cork,
    inkFaint = ChronaColors.InkFaint,
)

val LocalChronaTokens = staticCompositionLocalOf { ChronaTokensValue }

object ChronaTheme {
    val tokens: ChronaTokens
        @Composable @ReadOnlyComposable get() = LocalChronaTokens.current
}

@Composable
fun ChronaTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            // The app is dark in every system mode, so the system bars follow suit.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalChronaTokens provides ChronaTokensValue,
        // The shell's Scaffold is transparent so the window background shows through, and
        // Material derives a Scaffold's content colour from its container. A transparent
        // container resolves to *unspecified*, so every `Text` that relied on the inherited
        // colour rendered black — on a near-black ground, i.e. invisible. Pinning it here fixes
        // every such text at once instead of patching them one screen at a time.
        LocalContentColor provides ChronaColors.Ink,
    ) {
        MaterialTheme(
            colorScheme = ChronaColorScheme,
            typography = ChronaTypography,
            content = content,
        )
    }
}