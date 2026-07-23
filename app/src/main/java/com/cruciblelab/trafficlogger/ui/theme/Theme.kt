package com.cruciblelab.trafficlogger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEBFC),
    onPrimaryContainer = AccentVioletDark,
    secondary = AccentMint,
    onSecondary = Color.White,
    background = BackgroundOffWhite,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    error = AccentCoral,
)

/**
 * Always renders a clean, modern light/white theme regardless of system
 * dark-mode or dynamic color, per product direction: a bright, confident
 * UI rather than the previous dark/dynamic scheme.
 */
@Composable
fun NetworkTrafficLoggerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = TrafficTypography,
        shapes = TrafficShapes,
        content = content
    )
}
