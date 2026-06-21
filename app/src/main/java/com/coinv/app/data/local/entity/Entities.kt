package com.coinv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class VaultMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String,
    val tags: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val progress: Int,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val completed: Boolean = false,
    val dueDate: Long? = null
)

@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val context: String = "",
    val pros: String,
    val cons: String,
    val risks: String,
    val opportunities: String,
    val missingInfo: String,
    val recommendation: String,
    val confidenceScore: Int,
    val outcome: String? = null,
    val emotionalState: String? = null,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "insights")
data class InsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learning_items")
data class LearningItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val summary: String,
    val progress: Int,
    val flashcardsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_sessions")
data class VoiceSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val durationMs: Long,
    val mode: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val cognitiveState: String,
    val activeCoach: String,
    val listeningMode: String = "push_to_talk",
    val wakeWordEnabled: Boolean = false,
    val continuousListening: Boolean = false
)

@Entity(tableName = "analytics")
data class AnalyticsEntity(
    @PrimaryKey val id: Int = 1,
    val focusScore: Int,
    val mentalEnergy: Int,
    val learningVelocity: Int,
    val decisionReadiness: Int,
    val memoryActivity: Int,
    val aiConfidence: Int,
    val dailyProgress: Int,
    val peakHours: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
