package com.gigrun.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.theme.*

@Composable
fun BreakEvenMeter(
    earned: Double,
    breakEvenTarget: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (breakEvenTarget > 0) (earned / breakEvenTarget).coerceIn(0.0, 1.5) else 0.0
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "breakeven_progress"
    )

    val arcColor = when {
        progress >= 1.0 -> SystemGreen
        progress >= 0.6 -> SystemOrange
        else -> SystemRed
    }

    val statusText = when {
        progress >= 1.0 -> "In Profit"
        progress >= 0.8 -> "Almost There"
        else -> "Below Break-Even"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val strokeWidth = 10.dp.toPx()
                val sweepAngle = 270f * animatedProgress.coerceAtMost(1f)

                // Track
                drawArc(
                    color = SystemGray4, startAngle = 135f, sweepAngle = 270f,
                    useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
                // Progress
                drawArc(
                    color = arcColor, startAngle = 135f, sweepAngle = sweepAngle,
                    useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = arcColor,
                    letterSpacing = 0.36.sp
                )
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = LabelSecondary,
                    letterSpacing = 0.06.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "₹${earned.toInt()} / ₹${breakEvenTarget.toInt()}",
            fontSize = 15.sp,
            color = LabelSecondary,
            letterSpacing = (-0.24).sp
        )
    }
}
