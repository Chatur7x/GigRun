package com.gigrun.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Minimal clean palette for GigRun — flat, no gradients, no shadows.
 * Single accent (teal) + earnings green + warning orange.
 * Semantic tokens that adapt to light/dark.
 */
data class GigRunColors(
    val isDark: Boolean,

    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnPrimary: Color,

    // Borders / dividers
    val border: Color,
    val divider: Color,

    // Primary (teal — trust, calm, productivity)
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    // Accent (warm orange — CTA, earnings highlight)
    val accent: Color,
    val accentContainer: Color,

    // Semantic
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,

    // Platform tints (subtle, for badges)
    val platformZepto: Color,
    val platformBlinkit: Color,
    val platformRapido: Color,
    val platformUber: Color,
    val platformOther: Color,

    // Chart
    val chartGrid: Color,
    val chartAxis: Color,
)

// Light — warm off-white background, pure white cards, 1px teal-tinted borders
val GigRunLight = GigRunColors(
    isDark = false,
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F4),
    surfaceContainer = Color(0xFFF0FDFA),
    textPrimary = Color(0xFF1C1917),
    textSecondary = Color(0xFF57534E),
    textTertiary = Color(0xFFA8A29E),
    textOnPrimary = Color(0xFFFFFFFF),
    border = Color(0xFFE7E5E4),
    divider = Color(0xFFE7E5E4),
    primary = Color(0xFF0D9488),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    accent = Color(0xFFEA580C),
    accentContainer = Color(0xFFFFEDD5),
    success = Color(0xFF16A34A),
    successContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    platformZepto = Color(0xFF7C3AED),
    platformBlinkit = Color(0xFFFACC15),
    platformRapido = Color(0xFF0D9488),
    platformUber = Color(0xFF000000),
    platformOther = Color(0xFF78716C),
    chartGrid = Color(0xFFF5F5F4),
    chartAxis = Color(0xFFE7E5E4),
)

// Dark — slate 950 bg, slate 900 cards, slate 800 borders
val GigRunDark = GigRunColors(
    isDark = true,
    background = Color(0xFF0C0A09),
    surface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFF292524),
    surfaceContainer = Color(0xFF1E3A3A),
    textPrimary = Color(0xFFFAFAF9),
    textSecondary = Color(0xFFA8A29E),
    textTertiary = Color(0xFF78716C),
    textOnPrimary = Color(0xFFFFFFFF),
    border = Color(0xFF292524),
    divider = Color(0xFF292524),
    primary = Color(0xFF2DD4BF),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    accent = Color(0xFFFB923C),
    accentContainer = Color(0xFF7C2D12),
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF14532D),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF78350F),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    platformZepto = Color(0xFFA78BFA),
    platformBlinkit = Color(0xFFFDE68A),
    platformRapido = Color(0xFF2DD4BF),
    platformUber = Color(0xFFE7E5E4),
    platformOther = Color(0xFFA8A29E),
    chartGrid = Color(0xFF292524),
    chartAxis = Color(0xFF44403C),
)

val LocalGigRunColors = staticCompositionLocalOf { GigRunLight }

object GigRun {
    // Access via CompositionLocal in composables: val c = LocalGigRunColors.current
}
