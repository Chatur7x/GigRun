package com.gigrun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.*

@Composable
fun EarningsCard(
    title: String,
    amount: String,
    subtitle: String? = null,
    accentColor: Color = SystemBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SecondaryBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LabelSecondary,
                letterSpacing = (-0.08).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.36.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = LabelTertiary,
                    letterSpacing = (-0.08).sp
                )
            }
        }
    }
}
