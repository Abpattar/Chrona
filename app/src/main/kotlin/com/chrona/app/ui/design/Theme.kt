package com.chrona.app.ui.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = ChronaColors.Ink,
    onPrimary = ChronaColors.PaperRaised,
    primaryContainer = ChronaColors.PaperShaded,
    onPrimaryContainer = ChronaColors.Ink,
    secondary = ChronaColors.Thread,
    onSecondary = ChronaColors.PaperRaised,
    background = ChronaColors.Paper,
    onBackground = ChronaColors.Ink,
    surface = ChronaColors.Paper,
    onSurface = ChronaColors.Ink,
    surfaceVariant = ChronaColors.PaperShaded,
    onSurfaceVariant = ChronaColors.InkMuted,
    outline = ChronaColors.Outline,
    error = Color(0xFF9B2C2C),
    onError = ChronaColors.PaperRaised,
)

private val DarkColors = darkColorScheme(
    primary = ChronaColors.InkDark,
    onPrimary = ChronaColors.PaperDark,
    primaryContainer = ChronaColors.CorkDark,
    onPrimaryContainer = ChronaColors.InkDark,
    secondary = ChronaColors.Thread,
    onSecondary = ChronaColors.PaperDark,
    background = ChronaColors.PaperDark,
    onBackground = ChronaColors.InkDark,
    surface = ChronaColors.PaperDark,
    onSurface = ChronaColors.InkDark,
    surfaceVariant = ChronaColors.PaperShadedDark,
    onSurfaceVariant = ChronaColors.InkMutedDark,
    outline = ChronaColors.OutlineDark,
    error = Color(0xFFE29191),
    onError = ChronaColors.PaperDark,
)

/** Paper-like tokens that Material's colour scheme has no slot for. */
data class PaperTokens(
    val paperRaised: Color,
    val cork: Color,
    val thread: Color,
)

val LocalPaperTokens = staticCompositionLocalOf {
    PaperTokens(paperRaised = LightColors.surface, cork = LightColors.primary, thread = ChronaColors.Thread)
}

object ChronaTheme {
    val paper: PaperTokens
        @Composable @ReadOnlyComposable get() = LocalPaperTokens.current
}

@Composable
fun ChronaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val paperTokens = if (darkTheme) {
        PaperTokens(
            paperRaised = ChronaColors.PaperRaisedDark,
            cork = ChronaColors.CorkDark,
            thread = ChronaColors.Thread,
        )
    } else {
        PaperTokens(
            paperRaised = ChronaColors.PaperRaised,
            cork = ChronaColors.Cork,
            thread = ChronaColors.Thread,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPaperTokens provides paperTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChronaTypography,
            content = content,
        )
    }
}