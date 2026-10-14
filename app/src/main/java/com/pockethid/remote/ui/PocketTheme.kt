package com.pockethid.remote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF41D6C3),
    onPrimary = Color(0xFF003731),
    secondary = Color(0xFF9BCBC3),
    background = Color(0xFF0B1220),
    surface = Color(0xFF121C2E),
    surfaceVariant = Color(0xFF1B2940),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    secondary = Color(0xFF4A635F),
    background = Color(0xFFF5FAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDEBE7),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
