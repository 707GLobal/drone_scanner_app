package com.global_707.drone_scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 全局设计 token，通过 CompositionLocal 提供给所有 Composable */
val LocalDroneColors = staticCompositionLocalOf { LightDroneColors }

private val LightColorScheme = lightColorScheme(
    primary = LightDroneColors.primary,
    onPrimary = LightDroneColors.primaryForeground,
    background = LightDroneColors.background,
    onBackground = LightDroneColors.foreground,
    surface = LightDroneColors.card,
    onSurface = LightDroneColors.cardForeground,
    surfaceVariant = LightDroneColors.muted,
    onSurfaceVariant = LightDroneColors.mutedForeground,
    outline = LightDroneColors.border,
    error = LightDroneColors.danger,
    onError = Color.White,
    secondary = LightDroneColors.success,
    tertiary = LightDroneColors.warning,
)

private val DarkColorScheme = lightColorScheme(
    primary = DarkDroneColors.primary,
    onPrimary = DarkDroneColors.primaryForeground,
    background = DarkDroneColors.background,
    onBackground = DarkDroneColors.foreground,
    surface = DarkDroneColors.card,
    onSurface = DarkDroneColors.cardForeground,
    surfaceVariant = DarkDroneColors.muted,
    onSurfaceVariant = DarkDroneColors.mutedForeground,
    outline = DarkDroneColors.border,
    error = DarkDroneColors.danger,
    onError = Color.White,
    secondary = DarkDroneColors.success,
    tertiary = DarkDroneColors.warning,
)

@Composable
fun DroneScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val droneColors = if (darkTheme) DarkDroneColors else LightDroneColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalDroneColors provides droneColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
