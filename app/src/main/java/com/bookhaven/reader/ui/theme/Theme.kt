package com.bookhaven.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = AccentRed,
    onPrimary = CreamElevated,
    background = Cream,
    onBackground = InkBlack,
    surface = CreamElevated,
    onSurface = InkBlack,
    surfaceVariant = Cream,
    onSurfaceVariant = InkSecondary,
    outlineVariant = HairlineLight
)

private val DarkColors = darkColorScheme(
    primary = AccentOrange,
    onPrimary = InkBlack,
    background = NightBackground,
    onBackground = NightText,
    surface = NightElevated,
    onSurface = NightText,
    surfaceVariant = NightElevated,
    onSurfaceVariant = NightSecondary,
    outlineVariant = HairlineDark
)

@Composable
fun BookHavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
