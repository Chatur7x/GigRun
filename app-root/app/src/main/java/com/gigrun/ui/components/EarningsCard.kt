package com.gigrun.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.Apple

@Composable
fun EarningsCard(
    title: String,
    amount: String,
    subtitle: String? = null,
    accentColor: Color = Apple.colors.blue,
    modifier: Modifier = Modifier
) {
    val c = Apple.colors
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.secondaryLabel, letterSpacing = (-0.08).sp)
            Spacer(Modifier.height(6.dp))
            Text(amount, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 0.36.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = c.tertiaryLabel, letterSpacing = (-0.08).sp)
            }
        }
    }
}
