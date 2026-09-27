package com.gigrun.ui.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.provider.Settings

object Motion {
    val fast = tween<Float>(150, easing = FastOutSlowInEasing)
    val standard = tween<Float>(250, easing = FastOutSlowInEasing)
    val slow = tween<Float>(350, easing = FastOutSlowInEasing)

    val springGentle = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
    val springBouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)

    // ── Beast phase (taste-skill → motion) ──
    // One easing family everywhere (shape-consistency lock for motion).
    val beastEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    fun entrance(delayMs: Int = 0) = tween<Float>(
        durationMillis = 420,
        delayMillis = delayMs,
        easing = beastEasing
    )

    val pressSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 500f)
    val ringSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 220f)
    val counterTween = tween<Float>(durationMillis = 700, easing = beastEasing)

    // motion-framer spring presets (Skill 3/6) — physics names, not raw numbers.
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = 100f)
    val wobbly = spring<Float>(dampingRatio = 0.5f, stiffness = 200f)
    val stiff = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
    val tapSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 600f)

    // Staggered list entrance
    const val staggerDelayMs = 40L
    const val staggerDurationMs = 220

    // Beast stagger: 0, 60, 120, 180… capped so long lists don't drift.
    fun staggerFor(index: Int): Int = (index * 60).coerceAtMost(420)
}

/**
 * ui-ux-pro-max P1: reduced-motion gate (Android port of prefers-reduced-motion).
 * Animator duration scale 0 = user disabled animations → jump to final state.
 * Live: re-reads when the setting changes (ContentObserver), keyed on context.
 */
@Composable
fun beastReducedMotion(): Boolean {
    val context = LocalContext.current
    var scale by remember(context) {
        mutableStateOf(readAnimatorScale(context))
    }
    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        try {
            context.contentResolver.registerContentObserver(uri, false, observer)
        } catch (_: Exception) {}
        onDispose {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
    }
    // Any non-1 scale damps motion; 0 disables it. Entrance uses snap at 0,
    // normal tween otherwise (partial scales still animate, just shorter upstream).
    return scale == 0f
}

private fun readAnimatorScale(context: android.content.Context): Float {
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Exception) { 1f }
}
fun Modifier.beastPress(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    // Reset stuck-pressed state when the enabled key restarts the gesture scope.
    LaunchedEffect(enabled) { if (!enabled) pressed = false }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.pressSpring,
        label = "beastPress"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}

/**
 * Taste-skill §5.C: scroll-reveal stagger — Compose port of RevealStagger.
 * [visible] should flip true once (screen enter). Each item gets i*60ms delay.
 * ui-ux-pro-max P1: respects reduced-motion via snap() (same call count either way,
 * so no conditional-composable hazard). Offsets are dp → px via density.
 */
@Composable
fun beastEntranceAlpha(index: Int, visible: Boolean): Float {
    val target = if (visible) 1f else 0f
    return animateFloatAsState(
        targetValue = target,
        animationSpec = if (beastReducedMotion()) androidx.compose.animation.core.snap() else Motion.entrance(Motion.staggerFor(index)),
        label = "beastAlpha$index"
    ).value
}

@Composable
fun beastEntranceOffsetPx(index: Int, visible: Boolean): Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetDp = if (visible) 0.dp else 26.dp
    val targetPx = with(density) { targetDp.toPx() }
    return animateFloatAsState(
        targetValue = targetPx,
        animationSpec = if (beastReducedMotion()) androidx.compose.animation.core.snap() else Motion.entrance(Motion.staggerFor(index)),
        label = "beastY$index"
    ).value
}

// Backward-compat alias (px value, now density-correct).
@Composable
fun beastEntranceOffsetY(index: Int, visible: Boolean): Float = beastEntranceOffsetPx(index, visible)
