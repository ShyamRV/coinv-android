package com.coinv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_events")
data class ContextEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val source: String,
    val appMode: String,
    val hourOfDay: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "feedback_events")
data class FeedbackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: String,
    val accepted: Boolean,
    val topic: String = "",
    val responseStyle: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "preference_profile")
data class PreferenceProfileEntity(
    @PrimaryKey val id: Int = 1,
    val shortResponseWeight: Float = 0.5f,
    val businessTopicWeight: Float = 0.5f,
    val goalTopicWeight: Float = 0.5f,
    val decisionTopicWeight: Float = 0.5f,
    val learningTopicWeight: Float = 0.5f,
    val interruptionTolerance: Float = 0.5f,
    val nightActivityWeight: Float = 0.5f,
    val morningActivityWeight: Float = 0.5f,
    val proactiveInsightThreshold: Float = 0.55f,
    val totalInteractions: Int = 0,
    val acceptedSuggestions: Int = 0,
    val ignoredSuggestions: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mode_history")
data class ModeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "suggestion_scores")
data class SuggestionScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: String,
    val score: Float,
    val confidence: Float,
    val surfaced: Boolean = false,
    val accepted: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis()
)
