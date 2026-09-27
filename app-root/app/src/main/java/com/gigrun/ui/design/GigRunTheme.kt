package com.gigrun.ui.design

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

private val lightScheme = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF57534E),
    onSecondary = Color.White,
    tertiary = Color(0xFFEA580C),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = Color(0xFF57534E),
    outline = Color(0xFFE7E5E4),
    outlineVariant = Color(0xFFF5F5F4),
    scrim = Color(0x52000000),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFA8A29E),
    onSecondary = Color(0xFF1C1917),
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF431407),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    background = Color(0xFF0C0A09),
    onBackground = Color(0xFFFAFAF9),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFFAFAF9),
    surfaceVariant = Color(0xFF292524),
    onSurfaceVariant = Color(0xFFA8A29E),
    outline = Color(0xFF44403C),
    outlineVariant = Color(0xFF292524),
    scrim = Color(0x80000000),
)

private val gigRunTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = (-0.1).sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp, lineHeight = 14.sp),
)

private val gigRunShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun GigRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) GigRunDark else GigRunLight
    val scheme = if (darkTheme) darkScheme else lightScheme
    // R&D fix (beast-mode): bridge the dual-theme bug — MainActivity provides only
    // LocalGigRunColors, but 12+ screens read Apple.colors (LocalAppleColors defaults
    // to dark always). Provide BOTH so Apple.colors follows the same dark/light choice.
    val appleColors = if (darkTheme) com.gigrun.ui.theme.AppleDark else com.gigrun.ui.theme.AppleLight
    CompositionLocalProvider(
        LocalGigRunColors provides colors,
        com.gigrun.ui.theme.LocalAppleColors provides appleColors
    ) {
        MaterialTheme(colorScheme = scheme, typography = gigRunTypography, shapes = gigRunShapes, content = content)
    }
}
