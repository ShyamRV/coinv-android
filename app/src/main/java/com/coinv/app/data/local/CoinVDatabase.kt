package com.coinv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.coinv.app.data.local.dao.AnalyticsDao
import com.coinv.app.data.local.dao.ContextEventDao
import com.coinv.app.data.local.dao.ConversationDao
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.FeedbackEventDao
import com.coinv.app.data.local.dao.GoalDao
import com.coinv.app.data.local.dao.InsightDao
import com.coinv.app.data.local.dao.LearningDao
import com.coinv.app.data.local.dao.ModeHistoryDao
import com.coinv.app.data.local.dao.SemanticMemoryDao
import com.coinv.app.data.local.dao.VaultMemoryDao
import com.coinv.app.data.local.dao.MessageDao
import com.coinv.app.data.local.dao.InterventionDao
import com.coinv.app.data.local.dao.PromiseDao
import com.coinv.app.data.local.dao.PreferenceProfileDao
import com.coinv.app.data.local.dao.ProfileDao
import com.coinv.app.data.local.dao.RecommendationDao
import com.coinv.app.data.local.dao.SeedDao
import com.coinv.app.data.local.dao.SuggestionScoreDao
import com.coinv.app.data.local.dao.TaskDao
import com.coinv.app.data.local.dao.TimelineDao
import com.coinv.app.data.local.dao.VoiceSessionDao
import com.coinv.app.data.local.entity.AnalyticsEntity
import com.coinv.app.data.local.entity.ContextEventEntity
import com.coinv.app.data.local.entity.ConversationEntity
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.FeedbackEventEntity
import com.coinv.app.data.local.entity.GoalEntity
import com.coinv.app.data.local.entity.InsightEntity
import com.coinv.app.data.local.entity.LearningItemEntity
import com.coinv.app.data.local.entity.VaultMemoryEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.InterventionEntity
import com.coinv.app.data.local.entity.PromiseEntity
import com.coinv.app.data.local.entity.ModeHistoryEntity
import com.coinv.app.data.local.entity.PreferenceProfileEntity
import com.coinv.app.data.local.entity.RecommendationEntity
import com.coinv.app.data.local.entity.SuggestionScoreEntity
import com.coinv.app.data.local.entity.TaskEntity
import com.coinv.app.data.local.entity.TimelineEventEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.data.local.entity.VoiceSessionEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        VaultMemoryEntity::class,
        GoalEntity::class,
        TaskEntity::class,
        DecisionEntity::class,
        InsightEntity::class,
        LearningItemEntity::class,
        VoiceSessionEntity::class,
        UserProfileEntity::class,
        AnalyticsEntity::class,
        TimelineEventEntity::class,
        RecommendationEntity::class,
        ContextEventEntity::class,
        FeedbackEventEntity::class,
        PreferenceProfileEntity::class,
        ModeHistoryEntity::class,
        SuggestionScoreEntity::class,
        com.coinv.app.data.local.entity.MemoryEntity::class,
        InterventionEntity::class,
        PromiseEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class CoinVDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun vaultMemoryDao(): VaultMemoryDao
    abstract fun semanticMemoryDao(): SemanticMemoryDao
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun decisionDao(): DecisionDao
    abstract fun insightDao(): InsightDao
    abstract fun learningDao(): LearningDao
    abstract fun voiceSessionDao(): VoiceSessionDao
    abstract fun profileDao(): ProfileDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun timelineDao(): TimelineDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun seedDao(): SeedDao
    abstract fun contextEventDao(): ContextEventDao
    abstract fun feedbackEventDao(): FeedbackEventDao
    abstract fun preferenceProfileDao(): PreferenceProfileDao
    abstract fun modeHistoryDao(): ModeHistoryDao
    abstract fun suggestionScoreDao(): SuggestionScoreDao
    abstract fun interventionDao(): InterventionDao
    abstract fun promiseDao(): PromiseDao
}
