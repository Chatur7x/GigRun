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

import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Apple.colors.label,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val c = Apple.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = c.secondaryLabel,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
            }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = c.secondaryLabel, letterSpacing = (-0.24).sp)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = valueColor, letterSpacing = (-0.24).sp)
    }
}
