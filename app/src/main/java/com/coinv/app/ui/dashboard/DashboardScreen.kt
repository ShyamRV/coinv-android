package com.coinv.app.ui.dashboard

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinv.app.domain.AppMode
import com.coinv.app.domain.ModeState
import com.coinv.app.domain.VoicePhase
import com.coinv.app.navigation.CoinVTab
import com.coinv.app.ui.CoinOrb
import com.coinv.app.ui.components.EmptyState
import com.coinv.app.ui.components.InsightCard
import com.coinv.app.ui.components.MetricCard
import com.coinv.app.ui.components.QuickAction
import com.coinv.app.ui.components.QuickActionsRow
import com.coinv.app.ui.components.RecommendationCard
import com.coinv.app.ui.components.SectionHeader
import com.coinv.app.ui.components.SurfaceCard
import com.coinv.app.ui.components.TimelineItem
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinBlueDim
import com.coinv.app.ui.theme.CoinBorder
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.theme.CoinWarning
import com.coinv.app.ui.theme.jetBrainsMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    modeState: ModeState,
    onOrbClick: () -> Unit,
    onEnterListening: () -> Unit,
    onEnterMonitoring: () -> Unit,
    onStopMode: () -> Unit,
    onQuickAction: (String) -> Unit = {},
    onNavigate: (CoinVTab) -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dailySummary by viewModel.dailySummaryFlow.collectAsState()
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val profile = state.profile
    val userName = profile?.name ?: "Shyam"
    val metrics = state.metrics
    val greeting = greetingForHour()
    val orbStatus = modeState.orbStatus()

    val quickActions = listOf(
        QuickAction("Start Listening", "voice"),
        QuickAction("Capture idea", "idea"),
        QuickAction("Analyze decision", "decision"),
        QuickAction("Create goal", "goal")
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(bottom = 8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$greeting, $userName",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CoinChrome
                )
                Text(
                    text = "Cognitive Operating System",
                    fontFamily = jetBrainsMono,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = CoinChromeMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item {
            ActiveModeCard(
                modeState = modeState,
                onEnterListening = onEnterListening,
                onEnterMonitoring = onEnterMonitoring,
                onStopMode = onStopMode
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CoinOrb(
                    status = orbStatus,
                    enabled = true,
                    reduceMotion = reduceMotion,
                    onClick = onOrbClick,
                    sizeDp = 240.dp
                )
                ModeStatusLabel(modeState)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            if (dailySummary.isNotBlank()) {
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Daily Summary", fontFamily = jetBrainsMono, fontSize = 10.sp, color = CoinBlue)
                    Text(dailySummary, style = MaterialTheme.typography.bodyMedium, color = CoinChrome, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        item { SectionHeader("Recent Context") }
        if (state.recentContext.isEmpty()) {
            item {
                EmptyState("Context builds while Monitoring or during conversations.", Modifier.padding(horizontal = 20.dp))
            }
        } else {
            items(state.recentContext.take(5)) { event ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
                    Text(event.type.replace('_', ' ').uppercase(), fontFamily = jetBrainsMono, fontSize = 9.sp, color = CoinBlueDim)
                    Text(event.payload.take(100), style = MaterialTheme.typography.bodySmall, color = CoinChromeMuted)
                }
            }
        }

        item { SectionHeader("Cognitive Metrics") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { MetricCard("Focus", "${metrics.focusScore}%", metrics.focusScore / 100f, CoinBlue, Modifier.width(130.dp)) }
                item { MetricCard("Energy", "${metrics.mentalEnergy}%", metrics.mentalEnergy / 100f, CoinSuccess, Modifier.width(130.dp)) }
                item { MetricCard("Goals", "${metrics.dailyProgress}%", metrics.dailyProgress / 100f, CoinBlue, Modifier.width(130.dp)) }
                item { MetricCard("Memory", "${metrics.memoriesTotal}", metrics.memoryActivity / 100f, modifier = Modifier.width(130.dp)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        state.personalization?.let { p ->
            item { SectionHeader("Learning Progress") }
            item {
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Personalization", color = CoinChrome, style = MaterialTheme.typography.titleSmall)
                        Text(p.confidenceLabel, fontFamily = jetBrainsMono, fontSize = 11.sp, color = CoinBlue)
                    }
                    LinearProgressIndicator(
                        progress = { p.learningProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = CoinBlue,
                        trackColor = CoinSurface
                    )
                    Text(
                        "${p.profile.totalInteractions} interactions · ${p.profile.acceptedSuggestions} accepted · ${p.profile.ignoredSuggestions} dismissed",
                        fontSize = 11.sp,
                        color = CoinChromeMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        val suggestions = state.rankedSuggestions.ifEmpty {
            state.recommendations.mapIndexed { i, r ->
                com.coinv.app.data.local.entity.SuggestionScoreEntity(
                    id = i.toLong(),
                    text = r.text,
                    category = r.category,
                    score = 0.5f,
                    confidence = 0.5f
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionHeader("Decision Suggestions") }
            items(suggestions.take(4)) { rec ->
                RecommendationCard(
                    text = rec.text,
                    priority = ((1f - rec.score) * 5).toInt().coerceIn(1, 5),
                    onClick = { onNavigate(CoinVTab.DECISIONS) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        if (state.recentMemories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Memory Highlights", modifier = Modifier.padding(0.dp))
                    TextButton(onClick = { onNavigate(CoinVTab.MEMORY) }) {
                        Text("See all", color = CoinBlue, fontSize = 12.sp)
                    }
                }
            }
            items(state.recentMemories) { m ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall, color = CoinChrome)
                    Text(m.content.take(80), style = MaterialTheme.typography.bodySmall, color = CoinChromeMuted)
                }
            }
        }

        if (state.insights.isNotEmpty()) {
            item { SectionHeader("Insights") }
            items(state.insights.take(3)) { insight ->
                InsightCard(text = insight.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("Timeline", modifier = Modifier.padding(0.dp))
                TextButton(onClick = onOpenTimeline) { Text("View all", color = CoinBlue, fontSize = 12.sp) }
            }
        }
        if (state.timeline.isEmpty()) {
            item { EmptyState("Your timeline builds as you interact.", Modifier.padding(horizontal = 20.dp)) }
        } else {
            items(state.timeline.take(6)) { event ->
                TimelineItem(event.type, event.title, event.description, formatTime(event.timestamp))
            }
        }

        item {
            QuickActionsRow(actions = quickActions, onAction = { onQuickAction(it.id) })
        }
    }
}

@Composable
private fun ActiveModeCard(
    modeState: ModeState,
    onEnterListening: () -> Unit,
    onEnterMonitoring: () -> Unit,
    onStopMode: () -> Unit
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Active Mode", fontFamily = jetBrainsMono, fontSize = 10.sp, color = CoinBlue)
        Text(
            modeState.mode.displayLabel() +
                if (modeState.phase != VoicePhase.None && modeState.phase != VoicePhase.Capturing)
                    " · ${modeState.phase.name}" else "",
            style = MaterialTheme.typography.titleMedium,
            color = CoinChrome,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (modeState.mode == AppMode.Monitoring) {
            Row(
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CoinBlue)
                )
                Text(
                    " Monitoring active — context collection only",
                    fontSize = 11.sp,
                    color = CoinBlueDim
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton("Listening", modeState.mode == AppMode.Listening, onEnterListening)
            ModeButton("Monitoring", modeState.mode == AppMode.Monitoring, onEnterMonitoring)
            if (modeState.mode != AppMode.Idle) {
                ModeButton("Stop", false, onStopMode, muted = true)
            }
        }
        Text(
            "Headset: 1× tap = Listening · 2× tap = Monitoring",
            fontSize = 10.sp,
            color = CoinChromeMuted,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit, muted: Boolean = false) {
    Text(
        text = label,
        fontFamily = jetBrainsMono,
        fontSize = 11.sp,
        color = when {
            selected -> CoinBlue
            muted -> CoinWarning
            else -> CoinChromeMuted
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) CoinBlue else CoinBorder, RoundedCornerShape(8.dp))
            .background(if (selected) CoinSurface else CoinBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun ModeStatusLabel(modeState: ModeState) {
    val label = when {
        modeState.phase == VoicePhase.Thinking -> "THINKING"
        modeState.phase == VoicePhase.Speaking -> "SPEAKING"
        modeState.mode == AppMode.Listening -> "LISTENING"
        modeState.mode == AppMode.Monitoring -> "MONITORING"
        else -> "IDLE"
    }
    Text(
        text = label,
        fontFamily = jetBrainsMono,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = if (modeState.mode != AppMode.Idle) CoinBlue else CoinChromeMuted,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp)
    )
}

private fun greetingForHour(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

private fun formatTime(ts: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val eventDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    return if (today == eventDay) fmt.format(Date(ts)) else SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
}
