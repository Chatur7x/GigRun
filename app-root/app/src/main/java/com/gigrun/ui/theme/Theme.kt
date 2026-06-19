package com.gigrun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Material Color Schemes (mapped from Apple system colors) ──

private val AppleDarkColorScheme = darkColorScheme(
    primary          = Color(0xFF0A84FF),
    onPrimary        = Color.White,
    secondary        = Color(0xFF30D158),
    onSecondary      = Color.White,
    tertiary         = Color(0xFFFF9F0A),
    onTertiary       = Color.White,
    error            = Color(0xFFFF453A),
    onError          = Color.White,
    background       = Color(0xFF000000),
    onBackground     = Color.White,
    surface          = Color(0xFF1C1C1E),
    onSurface        = Color.White,
    surfaceVariant   = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0x99EBEBF5),
    outline          = Color(0xFF38383A),
    surfaceContainerHigh = Color(0xFF2C2C2E)
)

private val AppleLightColorScheme = lightColorScheme(
    primary          = Color(0xFF007AFF),
    onPrimary        = Color.White,
    secondary        = Color(0xFF34C759),
    onSecondary      = Color.White,
    tertiary         = Color(0xFFFF9500),
    onTertiary       = Color.White,
    error            = Color(0xFFFF3B30),
    onError          = Color.White,
    background       = Color(0xFFF2F2F7),
    onBackground     = Color.Black,
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color.Black,
    surfaceVariant   = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0x993C3C43),
    outline          = Color(0xFFC6C6C8),
    surfaceContainerHigh = Color(0xFFFFFFFF)
)

// ── SF Pro-inspired Typography ─────────────────────────────────
private val AppleTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.37.sp, lineHeight = 41.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.36.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.35.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.38.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.41).sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.24).sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.41).sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.24).sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = (-0.08).sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.24).sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.06.sp, lineHeight = 13.sp)
)

// ── iOS-style Shapes ───────────────────────────────────────────
val premiumShapes = Shapes(
    small  = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large  = RoundedCornerShape(20.dp)
)

// ── Theme ──────────────────────────────────────────────────────
@Composable
fun GigRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appleColors = if (darkTheme) AppleDark else AppleLight
    val colorScheme = if (darkTheme) AppleDarkColorScheme else AppleLightColorScheme

    CompositionLocalProvider(LocalAppleColors provides appleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppleTypography,
            shapes      = premiumShapes,
            content     = content
        )
    }
}

// ── Legacy aliases for backward compat ─────────────────────────
// (Used only by CrashCountdownOverlay which doesn't need theme-awareness)
val CyberCyan     = Color(0xFF0A84FF)
val EmeraldGreen  = Color(0xFF30D158)
val MoltenAmber   = Color(0xFFFF9F0A)
val CyberCrimson  = Color(0xFFFF453A)
val DeepCarbon    = Color(0xFF000000)
val DarkSurface   = Color(0xFF1C1C1E)
val CardSurface   = Color(0xFF1C1C1E)
val TextPrimary   = Color.White
val TextSecondary = Color(0x99EBEBF5)
val DividerColor  = Color(0xFF38383A)
