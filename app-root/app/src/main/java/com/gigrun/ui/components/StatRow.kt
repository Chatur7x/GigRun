package com.gigrun.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.Apple

@Composable
fun StatRow(label: String, value: String, valueColor: Color = Apple.colors.label, modifier: Modifier = Modifier) {
    val c = Apple.colors
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = c.secondaryLabel, letterSpacing = (-0.24).sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = valueColor, letterSpacing = (-0.24).sp)
    }
}
