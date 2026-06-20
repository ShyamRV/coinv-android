package com.coinv.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import coil.compose.AsyncImage
import com.coinv.app.R
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinBlueDim
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinError
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CoinOrb(
    status: String,
    enabled: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 240.dp
) {
    val semanticsLabel = when (status) {
        "listening" -> "Stop listening"
        "thinking" -> "Thinking"
        "speaking" -> "Speaking"
        "learning" -> "Learning"
        "monitoring" -> "Monitoring"
        "error" -> "Error"
        else -> "Start listening"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "rotation"
    )

    val neuralRing1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "neural1"
    )

    val neuralRing2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing)),
        label = "neural2"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "wave"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    val heartbeat by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "heartbeat"
    )

    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "speaking"
    )

    val thinkingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "thinking"
    )

    val logoSize = sizeDp * 0.58f
    val innerSize = sizeDp * 0.78f

    Box(
        modifier = modifier
            .size(sizeDp)
            .semantics { contentDescription = semanticsLabel }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            when (status) {
                "listening" -> {
                    if (reduceMotion) {
                        drawCircle(color = CoinBlue, radius = radius - inset, style = Stroke(stroke))
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CoinBlue.copy(alpha = 0.25f), CoinBlue.copy(alpha = 0f)),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                        drawArc(
                            color = CoinBlue,
                            startAngle = rotation - 90f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                "thinking" -> {
                    if (reduceMotion) {
                        drawCircle(color = CoinBlueDim, radius = radius - inset, style = Stroke(stroke))
                    } else {
                        drawArc(
                            color = CoinBlueDim,
                            startAngle = neuralRing1 - 90f,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke * 0.8f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = CoinBlue,
                            startAngle = neuralRing2 - 90f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        val segments = 8
                        val filled = (thinkingProgress * segments).toInt().coerceIn(1, segments)
                        val segSweep = 360f / segments
                        for (i in 0 until filled) {
                            drawArc(
                                color = CoinBlue.copy(alpha = 0.5f),
                                startAngle = -90f + i * segSweep,
                                sweepAngle = segSweep * 0.65f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
                "speaking" -> {
                    if (!reduceMotion) {
                        for (i in 0..3) {
                            val waveRadius = radius * (0.7f + wavePhase * 0.3f + i * 0.08f)
                            drawCircle(
                                color = CoinBlue.copy(alpha = 0.15f - i * 0.03f),
                                radius = waveRadius,
                                center = center,
                                style = Stroke(1.5.dp.toPx())
                            )
                        }
                    }
                    drawCircle(color = CoinBlueDim, radius = radius - inset, style = Stroke(stroke))
                }
                "learning" -> {
                    if (!reduceMotion) {
                        repeat(6) { i ->
                            val angle = (rotation + i * 60f) * (Math.PI / 180f)
                            val dist = radius * 0.75f
                            val px = center.x + cos(angle).toFloat() * dist
                            val py = center.y + sin(angle).toFloat() * dist
                            drawCircle(
                                color = CoinBlue.copy(alpha = 0.4f + (i % 3) * 0.15f),
                                radius = 3.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }
                    drawCircle(color = CoinBlue, radius = radius - inset, style = Stroke(stroke * 0.7f))
                }
                "monitoring" -> {
                    val alpha = if (reduceMotion) 0.5f else heartbeat
                    drawCircle(
                        color = CoinBlue.copy(alpha = alpha * 0.4f),
                        radius = radius * 0.9f,
                        center = center
                    )
                    drawCircle(
                        color = CoinBlue.copy(alpha = alpha),
                        radius = radius - inset,
                        style = Stroke(stroke)
                    )
                }
                "error" -> {
                    drawCircle(color = CoinError, radius = radius - inset, style = Stroke(stroke))
                }
                else -> {
                    drawCircle(
                        color = CoinChromeMuted,
                        radius = radius - inset / 2f,
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(CoinBackground),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = R.drawable.coinv_logo,
                contentDescription = "CoinV neural orb",
                modifier = Modifier
                    .size(logoSize)
                    .clip(CircleShape)
                    .scale(
                        when {
                            status == "speaking" && !reduceMotion -> speakingPulse
                            status == "listening" && !reduceMotion -> pulse.coerceIn(0.95f, 1.05f)
                            else -> 1f
                        }
                    ),
                contentScale = ContentScale.Crop
            )

            if (status == "listening" || status == "speaking" || status == "monitoring") {
                val glowAlpha = when (status) {
                    "speaking" -> if (reduceMotion) 1f else speakingPulse
                    "monitoring" -> if (reduceMotion) 0.6f else heartbeat
                    else -> if (reduceMotion) 0.85f else pulse
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 4.dp)
                        .size(14.dp)
                        .scale(glowAlpha)
                        .alpha(glowAlpha.coerceIn(0.5f, 1f))
                        .clip(CircleShape)
                        .background(CoinBlue)
                )
            }
        }
    }
}
