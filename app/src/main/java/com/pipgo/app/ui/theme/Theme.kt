package com.pipgo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
)

@Composable
fun PipGoTheme(content: @Composable () -> Unit) {
    // Pip-Go always uses the dark developer console aesthetic
    MaterialTheme(colorScheme = DarkColors, content = content)
}
