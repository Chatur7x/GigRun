package com.gigrun.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Complete Apple HIG color system with exact iOS light & dark mode values.
 * Reference: https://developer.apple.com/design/human-interface-guidelines/color
 */
data class AppleColors(
    // Backgrounds
    val background: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,

    // Labels
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,

    // Separators
    val separator: Color,
    val opaqueSeparator: Color,

    // Fills
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,

    // System Colors
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val indigo: Color,
    val mint: Color,
    val cyan: Color,
    val brown: Color,

    // Grays
    val gray: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val gray5: Color,
    val gray6: Color,

    // Helpers
    val isDark: Boolean
)

// ── Apple Dark Mode (exact iOS values) ─────────────────────────
val AppleDark = AppleColors(
    background               = Color(0xFF000000),
    secondaryBackground      = Color(0xFF1C1C1E),
    tertiaryBackground       = Color(0xFF2C2C2E),
    groupedBackground        = Color(0xFF000000),
    secondaryGroupedBackground = Color(0xFF1C1C1E),

    label                    = Color(0xFFFFFFFF),
    secondaryLabel           = Color(0x99EBEBF5),
    tertiaryLabel            = Color(0x4DEBEBF5),
    quaternaryLabel          = Color(0x2EEBEBF5),

    separator                = Color(0xA6545458),
    opaqueSeparator          = Color(0xFF38383A),

    fill                     = Color(0x5C787880),
    secondaryFill            = Color(0x52787880),
    tertiaryFill             = Color(0x3D767680),

    blue    = Color(0xFF0A84FF),
    green   = Color(0xFF30D158),
    red     = Color(0xFFFF453A),
    orange  = Color(0xFFFF9F0A),
    yellow  = Color(0xFFFFD60A),
    purple  = Color(0xFFBF5AF2),
    pink    = Color(0xFFFF375F),
    teal    = Color(0xFF64D2FF),
    indigo  = Color(0xFF5E5CE6),
    mint    = Color(0xFF63E6E2),
    cyan    = Color(0xFF70D7FF),
    brown   = Color(0xFFAC8E68),

    gray    = Color(0xFF8E8E93),
    gray2   = Color(0xFF636366),
    gray3   = Color(0xFF48484A),
    gray4   = Color(0xFF3A3A3C),
    gray5   = Color(0xFF2C2C2E),
    gray6   = Color(0xFF1C1C1E),

    isDark  = true
)

// ── Apple Light Mode (exact iOS values) ────────────────────────
val AppleLight = AppleColors(
    background               = Color(0xFFFFFFFF),
    secondaryBackground      = Color(0xFFF2F2F7),
    tertiaryBackground       = Color(0xFFFFFFFF),
    groupedBackground        = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color(0xFFFFFFFF),

    label                    = Color(0xFF000000),
    secondaryLabel           = Color(0x993C3C43),
    tertiaryLabel            = Color(0x4D3C3C43),
    quaternaryLabel          = Color(0x2E3C3C43),

    separator                = Color(0x4A3C3C43),
    opaqueSeparator          = Color(0xFFC6C6C8),

    fill                     = Color(0x33787880),
    secondaryFill            = Color(0x29787880),
    tertiaryFill             = Color(0x1F767680),

    blue    = Color(0xFF007AFF),
    green   = Color(0xFF34C759),
    red     = Color(0xFFFF3B30),
    orange  = Color(0xFFFF9500),
    yellow  = Color(0xFFFFCC00),
    purple  = Color(0xFFAF52DE),
    pink    = Color(0xFFFF2D55),
    teal    = Color(0xFF5AC8FA),
    indigo  = Color(0xFF5856D6),
    mint    = Color(0xFF00C7BE),
    cyan    = Color(0xFF32ADE6),
    brown   = Color(0xFFA2845E),

    gray    = Color(0xFF8E8E93),
    gray2   = Color(0xFFAEAEB2),
    gray3   = Color(0xFFC7C7CC),
    gray4   = Color(0xFFD1D1D6),
    gray5   = Color(0xFFE5E5EA),
    gray6   = Color(0xFFF2F2F7),

    isDark  = false
)

// ── CompositionLocal ───────────────────────────────────────────
val LocalAppleColors = staticCompositionLocalOf { AppleDark }

/**
 * Convenience accessor: `Apple.colors.blue`, `Apple.colors.label`, etc.
 */
object Apple {
    val colors: AppleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppleColors.current
}
