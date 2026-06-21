package com.coinv.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.analytics.CognitiveMetrics
import com.coinv.app.data.analytics.LiveRecommendation
import com.coinv.app.data.local.entity.ContextEventEntity
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.InsightEntity
import com.coinv.app.data.local.entity.VaultMemoryEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.SuggestionScoreEntity
import com.coinv.app.data.local.entity.TimelineEventEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.data.repository.DashboardRepository
import com.coinv.app.engine.PersonalizationStatus
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
    val rankedSuggestions: List<SuggestionScoreEntity> = emptyList(),
    val recentContext: List<ContextEventEntity> = emptyList(),
    val personalization: PersonalizationStatus? = null,
    val timeline: List<TimelineEventEntity> = emptyList(),
    val recentMessages: List<MessageEntity> = emptyList(),
    val recentDecisions: List<DecisionEntity> = emptyList(),
    val recentMemories: List<VaultMemoryEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: DashboardRepository
) : ViewModel() {

    private val coreState = combine(
        repository.observeProfile(),
        repository.observeMetrics(),
        repository.observeInsights(),
        repository.observeRecommendations(),
        repository.observeTimeline()
    ) { profile, metrics, insights, recommendations, timeline ->
        CoreDashboardData(profile, metrics, insights, recommendations, timeline)
    }

    private val extendedState = combine(
        repository.observeRecentContext(),
        repository.observePersonalization(),
        repository.observeRankedSuggestions()
    ) { context, personalization, suggestions ->
        Triple(context, personalization, suggestions)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        coreState,
        extendedState,
        combine(
            repository.observeRecentMessages(),
            repository.observeRecentDecisions(),
            repository.observeRecentMemories()
        ) { messages, decisions, memories ->
            Triple(messages.take(5), decisions.take(3), memories.take(3))
        }
    ) { core, extended, recent ->
        DashboardUiState(
            profile = core.profile,
            metrics = core.metrics,
            insights = core.insights,
            recommendations = core.recommendations,
            timeline = core.timeline,
            recentContext = extended.first,
            personalization = extended.second,
            rankedSuggestions = extended.third,
            recentMessages = recent.first,
            recentDecisions = recent.second,
            recentMemories = recent.third,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val dailySummaryFlow: StateFlow<String> = combine(
        repository.observeProfile(),
        repository.observeMetrics(),
        repository.observePersonalization()
    ) { profile, metrics, personalization ->
        val name = profile?.name ?: "Shyam"
        val learningLabel = personalization.confidenceLabel
        when {
            metrics.messagesToday == 0 && metrics.voiceSessionsToday == 0 ->
                "$name, your cognitive loop is quiet. Single-tap headset for Listening, double-tap for Monitoring."
            metrics.goalsActive > 0 && metrics.dailyProgress > 0 ->
                "Welcome back, $name. ${metrics.voiceSessionsToday} sessions today. Personalization: $learningLabel."
            else ->
                "Good progress, $name — ${metrics.voiceSessionsToday} voice sessions, ${metrics.memoriesTotal} memories. $learningLabel."
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private data class CoreDashboardData(
        val profile: UserProfileEntity?,
        val metrics: CognitiveMetrics,
        val insights: List<InsightEntity>,
        val recommendations: List<LiveRecommendation>,
        val timeline: List<TimelineEventEntity>
    )
}
