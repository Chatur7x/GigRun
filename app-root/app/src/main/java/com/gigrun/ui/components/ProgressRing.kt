package com.gigrun.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigrun.ui.design.LocalGigRunColors
import com.gigrun.ui.design.Motion
import com.gigrun.ui.design.beastReducedMotion

@Composable
fun ProgressRing(
    progress: Float, // 0..1 (clamped visually, can exceed 1 for label)
    size: Dp = 88.dp,
    strokeWidth: Dp = 8.dp,
    label: String? = null,
    sublabel: String? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalGigRunColors.current
    val clamped = progress.coerceIn(0f, 1f).let { if (it.isFinite()) it else 0f }
    // Beast: spring the sweep so goal hits feel physical, not stepped.
    // Reduced-motion: snap to final value.
    val reduce = beastReducedMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else Motion.ringSpring,
        label = "ring"
    )
    val isOver = progress >= 1f
    val ringColor = when {
        isOver -> c.success
        progress >= 0.7f -> c.primary
        progress >= 0.4f -> c.warning
        else -> c.textTertiary
    }
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "${label ?: "Progress"} ${(clamped * 100).toInt()} percent"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            // track
            drawArc(color = c.border, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            // progress
            drawArc(color = ringColor, startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            label?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, lineHeight = 20.sp) }
            sublabel?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = c.textTertiary) }
        }
    }
}
