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

/**
 * Decision engine row.
 *
 * Field-name mapping from prior schema (kept to avoid duplicates):
 * - [context] = contextNotes (user free-text)
 * - [missingInfo] = missingInformation (JSON list)
 * - [outcome] = outcomeNotes (follow-up free text)
 * - [confidenceScore] was Int 0–100; now Float 0.0–1.0
 * - [status] was pending/analyzed/resolved; now pending_outcome|resolved_*|abandoned
 *
 * pros/cons/risks/opportunities/missingInfo store JSON-encoded string lists.
 */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    /** User-provided free-text context (prompt: contextNotes). */
    val context: String = "",
    val pros: String,
    val cons: String,
    val risks: String,
    val opportunities: String,
    /** JSON list of missing-info strings (prompt: missingInformation). */
    val missingInfo: String,
    val recommendation: String,
    /** 0.0–1.0 LLM-stated confidence. */
    val confidenceScore: Float,
    /** Follow-up notes (prompt: outcomeNotes). */
    val outcome: String? = null,
    val emotionalState: String? = null,
    val status: String = "pending_outcome",
    val createdAt: Long = System.currentTimeMillis(),
    val outcomeFollowUpAt: Long = 0L,
    val outcomeAskedAt: Long? = null,
    /** JSON-encoded FloatArray for pattern retrieval; same pattern as MemoryEntity. */
    val embedding: String? = null
)

object DecisionStatuses {
    const val PENDING_OUTCOME = "pending_outcome"
    const val RESOLVED_GOOD = "resolved_good"
    const val RESOLVED_BAD = "resolved_bad"
    const val RESOLVED_MIXED = "resolved_mixed"
    const val ABANDONED = "abandoned"

    const val FOLLOWUP_MS = 21L * 24 * 60 * 60 * 1000

    fun isResolved(status: String): Boolean =
        status == RESOLVED_GOOD || status == RESOLVED_BAD ||
            status == RESOLVED_MIXED || status == ABANDONED

    fun label(status: String): String = when (status) {
        PENDING_OUTCOME -> "Awaiting outcome"
        RESOLVED_GOOD -> "Outcome: good"
        RESOLVED_BAD -> "Outcome: bad"
        RESOLVED_MIXED -> "Outcome: mixed"
        ABANDONED -> "Abandoned"
        else -> status
    }
}

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
