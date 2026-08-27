package com.coinv.app.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinv.app.domain.AppMode
import com.coinv.app.domain.ModeState
import com.coinv.app.ui.theme.CoinBlue

/**
 * Persistent full-width mode indicator visible on every screen.
 * Idle = absent bar; Listening = solid blue; Monitoring = pulsing blue (tap to stop).
 */
@Composable
fun ModeIndicatorBar(
    modeState: ModeState,
    onStopMonitoring: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    when (modeState.mode) {
        AppMode.Idle -> Unit
        AppMode.Listening -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(CoinBlue)
            )
        }
        AppMode.Monitoring -> {
            val alpha = if (reduceMotion) {
                0.8f
            } else {
                val transition = rememberInfiniteTransition(label = "monitorBar")
                val animated by transition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "monitorPulse"
                )
                animated
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .alpha(alpha)
                    .background(CoinBlue)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStopMonitoring
                    )
                    .semantics {
                        contentDescription = "Monitoring active. Tap to stop."
                    }
            )
        }
    }
}
