// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalFluteColors = staticCompositionLocalOf { LightFluteColors }

/** The app's own colour tokens, which Material's colour scheme has no names for. */
object FluteTheme {
    val colors: FluteColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFluteColors.current
}

/*
 * Material's scheme is derived from the same tokens so the few Material pieces left (the
 * snackbar, ripples, the default text colour) match the hand-drawn ones.
 */
private fun FluteColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = outline,
        onPrimary = card,
        secondary = Go,
        onSecondary = InkOnFill,
        tertiary = Sun,
        onTertiary = InkOnFill,
        background = page,
        onBackground = text,
        surface = page,
        onSurface = text,
        surfaceVariant = track,
        onSurfaceVariant = mutedText,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        outline = border,
        outlineVariant = border,
        error = Stop,
        onError = InkOnFill,
        inverseSurface = text,
        inverseOnSurface = page,
    )
}

private val LightColorScheme = LightFluteColors.toColorScheme(dark = false)
private val DarkColorScheme = DarkFluteColors.toColorScheme(dark = true)

/**
 * Material 3 theme with a fixed playful palette (see [Color.kt]), extra-round shapes and
 * big, bold rounded type. Dark mode follows the system setting unless the caller says otherwise
 * (`MainScreen` passes the child's choice, [ThemeViewModel]).
 */
@Composable
fun FluteAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFluteColors provides if (darkTheme) DarkFluteColors else LightFluteColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
