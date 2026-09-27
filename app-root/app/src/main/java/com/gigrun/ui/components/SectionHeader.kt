package com.gigrun.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.design.LocalGigRunColors

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.textTertiary,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
}
