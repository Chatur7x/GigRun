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
import com.gigrun.ui.theme.Apple

@Composable
fun BreakEvenMeter(earned: Double, breakEvenTarget: Double, modifier: Modifier = Modifier) {
    val c = Apple.colors
    val progress = if (breakEvenTarget > 0) (earned / breakEvenTarget).coerceIn(0.0, 1.5) else 0.0
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing), label = "bp"
    )
    val arcColor = when { progress >= 1.0 -> c.green; progress >= 0.6 -> c.orange; else -> c.red }
    val statusText = when { progress >= 1.0 -> "In Profit"; progress >= 0.8 -> "Almost There"; else -> "Below Break-Even" }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            val trackColor = c.gray4
            Canvas(Modifier.size(130.dp)) {
                val sw = 10.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress.coerceAtMost(1f),
                    useCenter = false,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = arcColor)
                Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.secondaryLabel)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("₹${earned.toInt()} / ₹${breakEvenTarget.toInt()}", fontSize = 15.sp, color = c.secondaryLabel)
    }
}
