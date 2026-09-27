package com.gigrun.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gigrun.ui.design.LocalGigRunColors
import com.gigrun.ui.design.Motion
import com.gigrun.ui.design.beastEntranceAlpha
import com.gigrun.ui.design.beastEntranceOffsetY
import com.gigrun.ui.design.beastPress
import com.gigrun.ui.design.beastReducedMotion

/**
 * Beast GigCard — taste-skill phase:
 * - 16dp shape lock (no mixed radii), 1px border, no black shadows
 * - staggered entrance (alpha + 26dp rise, 60ms cascade)
 * - physical press physics on clickable cards
 */
@Composable
fun GigCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    entranceIndex: Int = -1,
    entranceVisible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalGigRunColors.current
    // Only run entrance animation hooks when opted in — calling beastReducedMotion()
    // unconditionally on every card registers a ContentObserver mid-composition and
    // crashes when ANIMATOR_DURATION_SCALE changes during the composition pass.
    val animating = entranceIndex >= 0
    val alpha = if (animating) beastEntranceAlpha(entranceIndex, entranceVisible) else 1f
    val y = if (animating) beastEntranceOffsetY(entranceIndex, entranceVisible) else 0f
    var m = modifier
    if (entranceIndex >= 0) {
        m = m.graphicsLayer { this.alpha = alpha; translationY = y }
    }
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        // Shared interaction source: press-scale and ripple read the SAME press,
        // so scroll-drags don't fake-press and TalkBack clicks animate too.
        val interactions = remember { MutableInteractionSource() }
        val pressed by interactions.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = Motion.pressSpring,
            label = "cardPress"
        )
        Surface(
            modifier = m.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape,
            color = c.surface,
            border = BorderStroke(1.dp, c.border),
            onClick = onClick,
            interactionSource = interactions
        ) { androidx.compose.foundation.layout.Column(content = content) }
    } else {
        Surface(
            modifier = m,
            shape = shape,
            color = c.surface,
            border = BorderStroke(1.dp, c.border)
        ) { androidx.compose.foundation.layout.Column(content = content) }
    }
}

/**
 * Animated rupee counter — counts 0 → target on first reveal.
 * Fraction-driven (no float precision loss past ₹16M), Indian grouping,
 * TalkBack-stable (one announcement of the final value, not 40 frames).
 */
@Composable
fun AnimatedRupees(
    target: Double,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    val safeTarget = target.takeIf { it.isFinite() && it >= 0 } ?: 0.0
    val reduce = beastReducedMotion()
    val fraction = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(safeTarget) {
        if (reduce) {
            fraction.snapTo(1f)
        } else {
            fraction.animateTo(1f, animationSpec = Motion.counterTween)
        }
    }
    val formatter = remember {
        try {
            java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
        } catch (_: Exception) { null }
    }
    val current = safeTarget * fraction.value
    val formatted = remember(current) {
        "₹" + (formatter?.format(current.toLong()) ?: current.toInt().toString())
    }
    // Stable semantics: TalkBack announces the settled total once.
    androidx.compose.foundation.layout.Box(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "₹" + (formatter?.format(safeTarget.toLong()) ?: safeTarget.toInt().toString())
        }
    ) {
        content(formatted)
    }
}
