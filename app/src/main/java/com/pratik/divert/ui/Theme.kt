package com.pratik.divert.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DivertColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF06231A),
    primaryContainer = Color(0xFF14342A),
    onPrimaryContainer = Color(0xFF7BF3C9),
    secondary = Color(0xFF34D399),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFECEDEE),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFECEDEE),
    surfaceVariant = Color(0xFF1B1E24),
    onSurfaceVariant = Color(0xFF8A8F98),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0E0E),
    outline = Color(0xFF2A2E36),
    outlineVariant = Color(0xFF22262D),
)

@Composable
fun DivertTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // app is dark-only by design
    MaterialTheme(
        colorScheme = DivertColors,
        typography = Typography(),
        content = content
    )
}
