package com.gigrun.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Apple iOS Dark Mode System Colors ──────────────────────────
val SystemBackground     = Color(0xFF000000)
val SecondaryBackground  = Color(0xFF1C1C1E)
val TertiaryBackground   = Color(0xFF2C2C2E)
val GroupedBackground    = Color(0xFF1C1C1E)
val ElevatedBackground   = Color(0xFF2C2C2E)

// System Colors (Dark Mode variants)
val SystemBlue    = Color(0xFF0A84FF)
val SystemGreen   = Color(0xFF30D158)
val SystemRed     = Color(0xFFFF453A)
val SystemOrange  = Color(0xFFFF9F0A)
val SystemYellow  = Color(0xFFFFD60A)
val SystemPurple  = Color(0xFFBF5AF2)
val SystemPink    = Color(0xFFFF375F)
val SystemTeal    = Color(0xFF64D2FF)
val SystemIndigo  = Color(0xFF5E5CE6)
val SystemMint    = Color(0xFF66D4CF)

// Grays
val SystemGray    = Color(0xFF8E8E93)
val SystemGray2   = Color(0xFF636366)
val SystemGray3   = Color(0xFF48484A)
val SystemGray4   = Color(0xFF3A3A3C)
val SystemGray5   = Color(0xFF2C2C2E)
val SystemGray6   = Color(0xFF1C1C1E)

// Labels
val LabelPrimary    = Color(0xFFFFFFFF)
val LabelSecondary  = Color(0x99EBEBF5) // 60%
val LabelTertiary   = Color(0x4DEBEBF5) // 30%
val LabelQuaternary = Color(0x2EEBEBF5) // 18%

// Separators
val SeparatorColor    = Color(0x5C545458) // 36%
val OpaqueSeparator   = Color(0xFF38383A)

// Fill colors
val SystemFill      = Color(0x5C787880) // 36%
val SecondaryFill   = Color(0x52787880) // 32%
val TertiaryFill    = Color(0x3D767680) // 24%
val QuaternaryFill  = Color(0x2E767680) // 18%

// ── Legacy aliases (backward compat with existing code) ────────
val CyberCyan     = SystemBlue
val EmeraldGreen  = SystemGreen
val MoltenAmber   = SystemOrange
val CyberCrimson  = SystemRed
val DeepCarbon    = SystemBackground
val DarkSurface   = SecondaryBackground
val CardSurface   = SecondaryBackground
val TextPrimary   = LabelPrimary
val TextSecondary = LabelSecondary
val DividerColor  = OpaqueSeparator
val NeonCyanLight = SystemTeal
val AccentCyan    = SystemBlue
val AccentMint    = SystemGreen
val AccentAmber   = SystemOrange
val AccentRose    = SystemRed
val AccentViolet  = SystemPurple

// ── Color Scheme ───────────────────────────────────────────────
private val AppleDarkColorScheme = darkColorScheme(
    primary          = SystemBlue,
    onPrimary        = Color.White,
    secondary        = SystemGreen,
    onSecondary      = Color.White,
    tertiary         = SystemOrange,
    onTertiary       = Color.White,
    error            = SystemRed,
    onError          = Color.White,
    background       = SystemBackground,
    onBackground     = LabelPrimary,
    surface          = SecondaryBackground,
    onSurface        = LabelPrimary,
    surfaceVariant   = TertiaryBackground,
    onSurfaceVariant = LabelSecondary,
    outline          = OpaqueSeparator,
    surfaceContainerHigh = ElevatedBackground
)

// ── Typography (SF Pro-inspired: clean, tight, weighted) ──────
private val AppleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = 0.37.sp,
        lineHeight = 41.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.36.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.35.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.38.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.41).sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = (-0.24).sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = (-0.41).sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = (-0.24).sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = (-0.08).sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = (-0.24).sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.06.sp,
        lineHeight = 13.sp
    )
)

// ── Shapes (iOS-style large radius) ───────────────────────────
val premiumShapes = Shapes(
    small  = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large  = RoundedCornerShape(20.dp)
)

// ── Theme ──────────────────────────────────────────────────────
@Composable
fun GigRunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppleDarkColorScheme,
        typography  = AppleTypography,
        shapes      = premiumShapes,
        content     = content
    )
}
