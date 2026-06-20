package com.coinv.app.ui.dashboard

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.CoinWarning
import com.coinv.app.ui.theme.jetBrainsMono
import com.coinv.app.voice.VoiceListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    voiceListener: VoiceListener,
    voiceStatus: String,
    onOrbClick: () -> Unit,
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

    val orbStatus = when (voiceStatus) {
        "idle" -> if (profile?.continuousListening == true) "monitoring" else "idle"
        else -> voiceStatus
    }

    val quickActions = listOf(
        QuickAction("Start conversation", "voice"),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$greeting, $userName",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CoinChrome
                )
                Text(
                    text = "$userName's Cognitive Dashboard",
                    fontFamily = jetBrainsMono,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = CoinChromeMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = profile?.cognitiveState ?: "Ready",
                    fontFamily = jetBrainsMono,
                    fontSize = 12.sp,
                    color = CoinBlue
                )
                AiStatusIndicator(voiceStatus)
            }
        }

        item {
            if (dailySummary.isNotBlank()) {
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Daily Summary", fontFamily = jetBrainsMono, fontSize = 10.sp, color = CoinBlue)
                    Text(dailySummary, style = MaterialTheme.typography.bodyMedium, color = CoinChrome, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CoinOrb(
                    status = orbStatus,
                    enabled = voiceStatus in listOf("idle", "error", "listening", "monitoring"),
                    reduceMotion = reduceMotion,
                    onClick = onOrbClick,
                    sizeDp = 248.dp
                )
                StatusLabel(orbStatus)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            QuickActionsRow(
                actions = quickActions,
                onAction = { action -> onQuickAction(action.id) }
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                item { MetricCard("Learning", "${metrics.learningVelocity}%", metrics.learningVelocity / 100f, CoinWarning, Modifier.width(130.dp)) }
                item { MetricCard("Memory", "${metrics.memoriesTotal}", metrics.memoryActivity / 100f, modifier = Modifier.width(130.dp)) }
                item { MetricCard("Decisions", "${metrics.decisionsPending} pending", metrics.decisionReadiness / 100f, modifier = Modifier.width(130.dp)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.recentMessages.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Recent Conversations", modifier = Modifier.padding(0.dp))
                    TextButton(onClick = { onNavigate(CoinVTab.VOICE) }) {
                        Text("See all", color = CoinBlue, fontSize = 12.sp)
                    }
                }
            }
            items(state.recentMessages.take(3)) { msg ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = if (msg.role == "user") "You" else "CoinV",
                        fontFamily = jetBrainsMono,
                        fontSize = 10.sp,
                        color = CoinBlue
                    )
                    Text(msg.text.take(120), style = MaterialTheme.typography.bodySmall, color = CoinChromeMuted)
                }
            }
        }

        if (state.recentDecisions.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Recent Decisions", modifier = Modifier.padding(0.dp))
                    TextButton(onClick = { onNavigate(CoinVTab.DECISIONS) }) {
                        Text("See all", color = CoinBlue, fontSize = 12.sp)
                    }
                }
            }
            items(state.recentDecisions) { d ->
                SurfaceCard(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clickable { onNavigate(CoinVTab.DECISIONS) }
                ) {
                    Text(d.question.take(80), style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
                    Text("${d.confidenceScore}% confidence", fontFamily = jetBrainsMono, fontSize = 10.sp, color = CoinChromeMuted)
                }
            }
        }

        if (state.recentMemories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader("Memory Activity", modifier = Modifier.padding(0.dp))
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
            item { SectionHeader("Recent Insights") }
            items(state.insights) { insight ->
                InsightCard(text = insight.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        } else if (!state.isLoading) {
            item {
                EmptyState(
                    "Insights appear from your voice sessions, goals, and memory activity.",
                    Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        if (state.recommendations.isNotEmpty()) {
            item { SectionHeader("Recommendations") }
            items(state.recommendations) { rec ->
                RecommendationCard(
                    text = rec.text,
                    priority = rec.priority,
                    onClick = rec.navigateTo?.let { route ->
                        {
                            when (route) {
                                "voice" -> onNavigate(CoinVTab.VOICE)
                                "memory" -> onNavigate(CoinVTab.MEMORY)
                                "decisions" -> onNavigate(CoinVTab.DECISIONS)
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Cognitive Timeline", modifier = Modifier.padding(0.dp))
                TextButton(onClick = onOpenTimeline) {
                    Text("View all", color = CoinBlue, fontSize = 12.sp)
                }
            }
        }

        if (state.timeline.isEmpty()) {
            item {
                EmptyState(
                    "Your timeline builds as you interact with CoinV.",
                    Modifier.padding(horizontal = 20.dp)
                )
            }
        } else {
            items(state.timeline.take(8)) { event ->
                TimelineItem(
                    type = event.type,
                    title = event.title,
                    description = event.description,
                    timeLabel = formatTime(event.timestamp)
                )
            }
        }
    }
}

@Composable
private fun AiStatusIndicator(voiceStatus: String) {
    val (label, color) = when (voiceStatus) {
        "listening" -> "AI Listening" to CoinBlue
        "thinking" -> "AI Thinking" to CoinWarning
        "speaking" -> "AI Speaking" to CoinSuccess
        "learning" -> "AI Learning" to CoinBlue
        "monitoring" -> "Background Monitor" to CoinBlue
        "error" -> "AI Error" to com.coinv.app.ui.theme.CoinError
        else -> "AI Ready" to CoinChromeMuted
    }
    Text("● $label", fontFamily = jetBrainsMono, fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun StatusLabel(status: String) {
    Text(
        text = status.uppercase(),
        fontFamily = jetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = if (status in listOf("listening", "thinking", "speaking", "learning", "monitoring")) CoinBlue else CoinChromeMuted,
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
