package com.gigrun.presentation.crash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gigrun.service.CrashDetectionService
import com.gigrun.ui.design.LocalGigRunColors
import kotlinx.coroutines.delay

/**
 * Full-screen crash countdown overlay using GigRun color styling.
 */
@Composable
fun CrashCountdownOverlay() {
    val c = LocalGigRunColors.current
    var showOverlay by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(30) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                showOverlay = true
                countdownSeconds = 30
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter("com.gigrun.CRASH_DETECTED"),
            Context.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (showOverlay) {
        LaunchedEffect(showOverlay) {
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds--
            }
            showOverlay = false
        }

        val pulseAnim = rememberInfiniteTransition(label = "pulse")
        val scale by pulseAnim.animateFloat(
            initialValue = 1f, targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "pulse_scale"
        )

        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.error.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "⚠️ CRASH DETECTED",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(160.dp)
                            .scale(scale)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text(
                            text = "$countdownSeconds",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Emergency SMS will be sent\nwhen countdown reaches zero.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(40.dp))
                    Button(
                        onClick = {
                            showOverlay = false
                            val intent = Intent(context, CrashDetectionService::class.java).apply {
                                action = CrashDetectionService.ACTION_CANCEL_COUNTDOWN
                            }
                            context.startService(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.success),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "I'm OK",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "I'M OK — CANCEL",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
