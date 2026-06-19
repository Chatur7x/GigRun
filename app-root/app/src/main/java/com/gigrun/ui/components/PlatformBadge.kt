package com.gigrun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.*

@Composable
fun PlatformBadge(platform: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (platform.lowercase()) {
        "blinkit"  -> Triple(SystemGreen.copy(alpha = 0.15f), SystemGreen, "Blinkit")
        "zepto"    -> Triple(SystemPurple.copy(alpha = 0.15f), SystemPurple, "Zepto")
        "rapido"   -> Triple(SystemYellow.copy(alpha = 0.15f), SystemYellow, "Rapido")
        "uber"     -> Triple(SystemBlue.copy(alpha = 0.15f), SystemBlue, "Uber")
        "swiggy"   -> Triple(SystemOrange.copy(alpha = 0.15f), SystemOrange, "Swiggy")
        "zomato"   -> Triple(SystemRed.copy(alpha = 0.15f), SystemRed, "Zomato")
        "bigbasket" -> Triple(SystemMint.copy(alpha = 0.15f), SystemMint, "BigBasket")
        else       -> Triple(SystemGray4, LabelPrimary, platform.replaceFirstChar { it.uppercase() })
    }

    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        letterSpacing = (-0.08).sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
