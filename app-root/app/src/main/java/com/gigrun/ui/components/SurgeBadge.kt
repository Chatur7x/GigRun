package com.gigrun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.design.LocalGigRunColors

@Composable
fun SurgeBadge(amount: Double, reason: String? = null, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    Row(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(c.warningContainer).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚡ +₹${amount.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.warning)
        reason?.let { Text(" · $it", fontSize = 10.sp, color = c.warning) }
    }
}

@Composable
fun TipBadge(amount: Double, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    Text(
        "Tip ₹${amount.toInt()}",
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.success,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(c.successContainer).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun BonusBadge(amount: Double, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    Text(
        "Bonus ₹${amount.toInt()}",
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.primary,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(c.primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
