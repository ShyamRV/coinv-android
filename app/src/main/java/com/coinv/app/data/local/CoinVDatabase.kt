package com.coinv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.coinv.app.data.local.dao.AnalyticsDao
import com.coinv.app.data.local.dao.ConversationDao
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.GoalDao
import com.coinv.app.data.local.dao.InsightDao
import com.coinv.app.data.local.dao.LearningDao
import com.coinv.app.data.local.dao.MemoryDao
import com.coinv.app.data.local.dao.MessageDao
import com.coinv.app.data.local.dao.ProfileDao
import com.coinv.app.data.local.dao.RecommendationDao
import com.coinv.app.data.local.dao.SeedDao
import com.coinv.app.data.local.dao.TaskDao
import com.coinv.app.data.local.dao.TimelineDao
import com.coinv.app.data.local.dao.VoiceSessionDao
import com.coinv.app.data.local.entity.AnalyticsEntity
import com.coinv.app.data.local.entity.ConversationEntity
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.GoalEntity
import com.coinv.app.data.local.entity.InsightEntity
import com.coinv.app.data.local.entity.LearningItemEntity
import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.RecommendationEntity
import com.coinv.app.data.local.entity.TaskEntity
import com.coinv.app.data.local.entity.TimelineEventEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.data.local.entity.VoiceSessionEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        GoalEntity::class,
        TaskEntity::class,
        DecisionEntity::class,
        InsightEntity::class,
        LearningItemEntity::class,
        VoiceSessionEntity::class,
        UserProfileEntity::class,
        AnalyticsEntity::class,
        TimelineEventEntity::class,
        RecommendationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CoinVDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
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
}
