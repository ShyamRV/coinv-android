package com.coinv.app.data.analytics

data class CognitiveMetrics(
    val focusScore: Int = 0,
    val mentalEnergy: Int = 0,
    val learningVelocity: Int = 0,
    val decisionReadiness: Int = 0,
    val memoryActivity: Int = 0,
    val aiConfidence: Int = 0,
    val dailyProgress: Int = 0,
    val voiceSessionsToday: Int = 0,
    val messagesToday: Int = 0,
    val memoriesTotal: Int = 0,
    val goalsActive: Int = 0,
    val tasksCompleted: Int = 0,
    val decisionsPending: Int = 0
)

data class LiveRecommendation(
    val text: String,
    val priority: Int,
    val navigateTo: String? = null,
    val category: String = "general"
)

data class DashboardSnapshot(
    val metrics: CognitiveMetrics = CognitiveMetrics(),
    val dailySummary: String = "",
    val recentMessages: List<com.coinv.app.data.local.entity.MessageEntity> = emptyList(),
    val recentDecisions: List<com.coinv.app.data.local.entity.DecisionEntity> = emptyList(),
    val recentMemories: List<com.coinv.app.data.local.entity.VaultMemoryEntity> = emptyList()
)
