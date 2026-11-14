package com.nandox.arcade.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFB58AFF),
    onPrimary = Color(0xFF211333),
    secondary = Color(0xFF8E6BC5),
    onSecondary = Color(0xFF1D142A),
    tertiary = Color(0xFFD2B8FF),
    background = Color(0xFF0B0A10),
    onBackground = Color(0xFFF1ECF8),
    surface = Color(0xFF17131F),
    onSurface = Color(0xFFF1ECF8),
    surfaceVariant = Color(0xFF292333),
    onSurfaceVariant = Color(0xFFA69BAF),
    outline = Color(0xFF51465F),
)

@Composable
fun ArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
