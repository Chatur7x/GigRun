package com.gigrun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.Apple

@Composable
fun ChipLabel(
    text: String,
    backgroundColor: Color = Apple.colors.secondaryFill,
    textColor: Color = Apple.colors.label,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 11.sp,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
