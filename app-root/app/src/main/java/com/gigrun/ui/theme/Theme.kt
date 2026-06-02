package com.gigrun.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand Colors
val CyberCyan = Color(0xFF00E5FF)
val NeonCyanLight = Color(0xFF6EFFFF)
val DeepCarbon = Color(0xFF0A0E17)
val DarkSurface = Color(0xFF111827)
val CardSurface = Color(0xFF1A2332)
val EmeraldGreen = Color(0xFF00E676)
val MoltenAmber = Color(0xFFFFC400)
val CyberCrimson = Color(0xFFFF1744)
val TextPrimary = Color(0xFFE8EAED)
val TextSecondary = Color(0xFF9AA0A6)
val DividerColor = Color(0xFF2D3748)

private val GigRunDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = DeepCarbon,
    secondary = EmeraldGreen,
    onSecondary = DeepCarbon,
    tertiary = MoltenAmber,
    onTertiary = DeepCarbon,
    error = CyberCrimson,
    onError = Color.White,
    background = DeepCarbon,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor
)

@Composable
fun GigRunTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GigRunDarkColorScheme,
        typography = Typography(),
        content = content
    )
}
