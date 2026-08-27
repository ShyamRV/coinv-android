package com.coinv.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinBorder
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.theme.CoinWarning
import com.coinv.app.ui.theme.jetBrainsMono

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        fontFamily = jetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = CoinChromeMuted
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    progress: Float? = null,
    accentColor: androidx.compose.ui.graphics.Color = CoinBlue,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CoinSurface)
            .border(1.dp, CoinBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = label,
            fontFamily = jetBrainsMono,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = CoinChromeMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = CoinChrome
        )
        if (progress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = CoinBorder,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun InsightCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CoinSurface)
            .border(1.dp, CoinBorder, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .background(CoinBlue)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
    }
}

@Composable
fun RecommendationCard(
    text: String,
    priority: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = when (priority) {
        1 -> CoinBlue
        2 -> CoinSuccess
        else -> CoinWarning
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(CoinSurface)
            .border(1.dp, CoinBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
    }
}

@Composable
fun TimelineItem(
    type: String,
    title: String,
    description: String,
    timeLabel: String,
    modifier: Modifier = Modifier
) {
    val typeColor = when (type) {
        "voice" -> CoinBlue
        "idea" -> CoinWarning
        "goal" -> CoinSuccess
        "decision" -> CoinBlue
        "learning" -> CoinSuccess
        "memory" -> CoinChromeMuted
        "insight" -> CoinBlue
        else -> CoinChromeMuted
    }
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(typeColor)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(CoinBorder)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
                Text(
                    text = timeLabel,
                    fontFamily = jetBrainsMono,
                    fontSize = 10.sp,
                    color = CoinChromeMuted
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = CoinChromeMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CoinSurface)
            .border(1.dp, CoinBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CoinChromeMuted
        )
    }
}
