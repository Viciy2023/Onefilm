package com.onefilm.android.tv.compose.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkScheme = darkColorScheme(
    primary = OneFilmColors.Accent,
    onPrimary = OneFilmColors.Background,
    background = OneFilmColors.Background,
    onBackground = OneFilmColors.TextPrimary,
    surface = OneFilmColors.Surface,
    onSurface = OneFilmColors.TextPrimary,
    surfaceVariant = OneFilmColors.SurfaceElevated,
    onSurfaceVariant = OneFilmColors.TextSecondary,
    outline = OneFilmColors.Divider,
)

private val LightScheme = lightColorScheme(
    primary = OneFilmColors.LightTextPrimary,
    onPrimary = OneFilmColors.LightSurface,
    background = OneFilmColors.LightBackground,
    onBackground = OneFilmColors.LightTextPrimary,
    surface = OneFilmColors.LightSurface,
    onSurface = OneFilmColors.LightTextPrimary,
    surfaceVariant = OneFilmColors.LightSurfaceElevated,
    onSurfaceVariant = OneFilmColors.LightTextSecondary,
    outline = OneFilmColors.LightTextSecondary,
)

@Composable
fun OneFilmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = OneFilmTypography,
            shapes = OneFilmShapes,
            content = content,
        )
    }
}
