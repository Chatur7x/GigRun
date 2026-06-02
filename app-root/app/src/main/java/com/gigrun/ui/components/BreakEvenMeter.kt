package com.gigrun.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "breakeven_progress"
    )

    val arcColor = when {
        progress >= 1.0 -> EmeraldGreen
        progress >= 0.6 -> MoltenAmber
        else -> CyberCrimson
    }

    val statusText = when {
        progress >= 1.0 -> "IN PROFIT"
        progress >= 0.8 -> "ALMOST THERE"
        else -> "BELOW BREAK-EVEN"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 12.dp.toPx()
                val sweepAngle = 270f * animatedProgress.coerceAtMost(1f)

                drawArc(
                    color = DividerColor, startAngle = 135f, sweepAngle = 270f,
                    useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
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
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = arcColor
                )
                Text(
                    text = statusText,
                    fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    color = TextSecondary, letterSpacing = 1.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "₹${earned.toInt()} / ₹${breakEvenTarget.toInt()}",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
        )
    }
}
