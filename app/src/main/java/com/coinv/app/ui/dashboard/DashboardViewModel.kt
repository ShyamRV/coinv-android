package com.coinv.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.analytics.CognitiveMetrics
import com.coinv.app.data.analytics.LiveRecommendation
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.InsightEntity
import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.TimelineEventEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val profile: UserProfileEntity? = null,
    val metrics: CognitiveMetrics = CognitiveMetrics(),
    val insights: List<InsightEntity> = emptyList(),
    val recommendations: List<LiveRecommendation> = emptyList(),
    val timeline: List<TimelineEventEntity> = emptyList(),
    val recentMessages: List<MessageEntity> = emptyList(),
    val recentDecisions: List<DecisionEntity> = emptyList(),
    val recentMemories: List<MemoryEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: DashboardRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.observeProfile(),
            repository.observeMetrics(),
            repository.observeInsights(),
            repository.observeRecommendations(),
            repository.observeTimeline()
        ) { profile, metrics, insights, recommendations, timeline ->
            DashboardUiState(
                profile = profile,
                metrics = metrics,
                insights = insights,
                recommendations = recommendations,
                timeline = timeline,
                isLoading = false
            )
        },
        combine(
            repository.observeRecentMessages(),
            repository.observeRecentDecisions(),
            repository.observeRecentMemories()
        ) { messages, decisions, memories ->
            Triple(messages.take(5), decisions.take(3), memories.take(3))
        }
    ) { base, recent ->
        base.copy(
            recentMessages = recent.first,
            recentDecisions = recent.second,
            recentMemories = recent.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val dailySummaryFlow: StateFlow<String> = combine(
        repository.observeProfile(),
        repository.observeMetrics()
    ) { profile, metrics ->
        val name = profile?.name ?: "Shyam"
        when {
            metrics.messagesToday == 0 && metrics.voiceSessionsToday == 0 ->
                "$name, your cognitive loop is quiet today. Start a voice session to activate CoinV."
            metrics.goalsActive > 0 && metrics.dailyProgress > 0 ->
                "Welcome back, $name. ${metrics.voiceSessionsToday} sessions, ${metrics.messagesToday} messages, goals at ${metrics.dailyProgress}%."
            else ->
                "Good progress, $name — ${metrics.voiceSessionsToday} voice sessions and ${metrics.memoriesTotal} memories in your vault."
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}
