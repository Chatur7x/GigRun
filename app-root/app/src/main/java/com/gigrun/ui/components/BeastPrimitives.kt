package com.gigrun.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.gigrun.ui.design.Motion
import com.gigrun.ui.design.beastReducedMotion

/**
 * motion-primitives port — Skill 2/6.
 * React originals: TextEffect (per-word blur/slide), InView (whileInView),
 * AnimatedGroup (stagger children). Compose uses graphicsLayer (GPU) only.
 */

// Per-word rise for hero eyebrows/headlines. Words beyond 8 share the last delay.
// Keyed per word so dynamic copy never mis-associates state; FlowRow wraps on
// narrow screens / large font scales.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeastTextEffect(
    text: String,
    color: Color,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    letterSpacing: TextUnit = 0.8.sp,
    visible: Boolean = true,
    baseIndex: Int = 0
) {
    val words = text.split(" ")
    val reduce = beastReducedMotion()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val risePx = with(density) { 10.dp.toPx() }
    // One TalkBack node for the whole line — per-word nodes read choppily.
    FlowRow(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = text }
    ) {
        words.forEachIndexed { i, w ->
            key(w to i) {
                val alpha by animateFloatAsState(
                    if (visible || reduce) 1f else 0f,
                    if (reduce) androidx.compose.animation.core.snap() else Motion.entrance(Motion.staggerFor(baseIndex + i)),
                    label = "word$i"
                )
                val y by animateFloatAsState(
                    if (visible || reduce) 0f else risePx,
                    if (reduce) androidx.compose.animation.core.snap() else Motion.entrance(Motion.staggerFor(baseIndex + i)),
                    label = "wordY$i"
                )
                Text(
                    w + if (i < words.lastIndex) " " else "",
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color,
                    letterSpacing = letterSpacing,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = y }
                )
            }
        }
    }
}

// whileInView wrapper — fade+rise once when [visible] flips (screen enter / scrolled in).
// Reduced-motion: renders final state immediately. Density-correct offsets.
@Composable
fun BeastInView(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alpha = com.gigrun.ui.design.beastEntranceAlpha(index, visible)
    val y = com.gigrun.ui.design.beastEntranceOffsetPx(index, visible)
    androidx.compose.foundation.layout.Box(
        modifier.graphicsLayer { this.alpha = alpha; translationY = y }
    ) { content() }
}
