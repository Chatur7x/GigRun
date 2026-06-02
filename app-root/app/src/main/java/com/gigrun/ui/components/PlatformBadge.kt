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
    val (bgColor, textColor) = when (platform.lowercase()) {
        "blinkit" -> Pair(Color(0xFF2E7D32), Color(0xFFA5D6A7))
        "zepto" -> Pair(Color(0xFF6A1B9A), Color(0xFFCE93D8))
        "rapido" -> Pair(Color(0xFFE65100), Color(0xFFFFCC80))
        "uber" -> Pair(Color(0xFF1565C0), Color(0xFF90CAF9))
        "commute" -> Pair(Color(0xFF37474F), Color(0xFF90A4AE))
        else -> Pair(DividerColor, TextSecondary)
    }

    Text(
        text = platform.replaceFirstChar { it.uppercase() },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
