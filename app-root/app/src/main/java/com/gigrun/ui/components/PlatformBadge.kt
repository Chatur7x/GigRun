package com.gigrun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.Apple

@Composable
fun PlatformBadge(platform: String, modifier: Modifier = Modifier) {
    val c = Apple.colors
    val (bgColor, textColor, label) = when (platform.lowercase()) {
        "blinkit"   -> Triple(c.green.copy(alpha = 0.15f), c.green, "Blinkit")
        "zepto"     -> Triple(c.purple.copy(alpha = 0.15f), c.purple, "Zepto")
        "rapido"    -> Triple(c.yellow.copy(alpha = 0.15f), c.yellow, "Rapido")
        "uber"      -> Triple(c.blue.copy(alpha = 0.15f), c.blue, "Uber")
        "swiggy"    -> Triple(c.orange.copy(alpha = 0.15f), c.orange, "Swiggy")
        "zomato"    -> Triple(c.red.copy(alpha = 0.15f), c.red, "Zomato")
        "bigbasket" -> Triple(c.mint.copy(alpha = 0.15f), c.mint, "BigBasket")
        else        -> Triple(c.gray4, c.label, platform.replaceFirstChar { it.uppercase() })
    }
    Text(
        text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bgColor).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
