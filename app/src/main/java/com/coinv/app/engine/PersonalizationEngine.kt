package com.coinv.app.engine

import com.coinv.app.data.analytics.LiveRecommendation
import com.coinv.app.data.local.dao.FeedbackEventDao
import com.coinv.app.data.local.dao.PreferenceProfileDao
import com.coinv.app.data.local.dao.SuggestionScoreDao
import com.coinv.app.data.local.entity.FeedbackEventEntity
import com.coinv.app.data.local.entity.PreferenceProfileEntity
import com.coinv.app.data.local.entity.SuggestionScoreEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class PersonalizationStatus(
    val profile: PreferenceProfileEntity,
    val learningProgress: Int,
    val confidenceLabel: String
)

data class RankedSuggestion(
    val text: String,
    val category: String,
    val score: Float,
    val confidence: Float,
    val entityId: Long? = null
)

@Singleton
class PersonalizationEngine @Inject constructor(
    private val preferenceProfileDao: PreferenceProfileDao,
    private val feedbackEventDao: FeedbackEventDao,
    private val suggestionScoreDao: SuggestionScoreDao
) {
    fun observeProfile(): Flow<PreferenceProfileEntity> =
        preferenceProfileDao.observe().map { it ?: defaultProfile() }

    fun observePersonalizationStatus(): Flow<PersonalizationStatus> =
        observeProfile().map { profile ->
            val progress = ((profile.totalInteractions.coerceAtMost(100) / 100f) * 100).toInt()
            val confidence = when {
                profile.totalInteractions < 10 -> "Calibrating"
                profile.acceptedSuggestions > profile.ignoredSuggestions -> "Adapting well"
                profile.ignoredSuggestions > profile.acceptedSuggestions * 2 -> "Conservative"
                else -> "Learning your patterns"
            }
            PersonalizationStatus(profile, progress, confidence)
        }

    fun observeTopSuggestions(limit: Int = 6): Flow<List<SuggestionScoreEntity>> =
        suggestionScoreDao.observeTop(limit)

    suspend fun ensureProfile(): PreferenceProfileEntity {
        val existing = preferenceProfileDao.get()
        if (existing != null) return existing
        val profile = defaultProfile()
        preferenceProfileDao.upsert(profile)
        return profile
    }

    suspend fun recordFeedback(
        targetType: String,
        targetId: String,
        accepted: Boolean,
        topic: String = "",
        responseStyle: String = ""
    ) {
        feedbackEventDao.insert(
            FeedbackEventEntity(
                targetType = targetType,
                targetId = targetId,
                accepted = accepted,
                topic = topic,
                responseStyle = responseStyle
            )
        )
        val profile = ensureProfile()
        val delta = if (accepted) 0.05f else -0.05f
        val topicWeight = when (topic) {
            "business" -> profile.businessTopicWeight + delta
            "goals" -> profile.goalTopicWeight + delta
            "decisions" -> profile.decisionTopicWeight + delta
            "learning" -> profile.learningTopicWeight + delta
            else -> profile.businessTopicWeight
        }
        preferenceProfileDao.upsert(
            profile.copy(
                businessTopicWeight = if (topic == "business") clamp(topicWeight) else profile.businessTopicWeight,
                goalTopicWeight = if (topic == "goals") clamp(topicWeight) else profile.goalTopicWeight,
                decisionTopicWeight = if (topic == "decisions") clamp(topicWeight) else profile.decisionTopicWeight,
                learningTopicWeight = if (topic == "learning") clamp(topicWeight) else profile.learningTopicWeight,
                shortResponseWeight = if (responseStyle == "short") clamp(profile.shortResponseWeight + delta)
                else profile.shortResponseWeight,
                interruptionTolerance = if (accepted) clamp(profile.interruptionTolerance + 0.03f)
                else clamp(profile.interruptionTolerance - 0.04f),
                totalInteractions = profile.totalInteractions + 1,
                acceptedSuggestions = profile.acceptedSuggestions + if (accepted) 1 else 0,
                ignoredSuggestions = profile.ignoredSuggestions + if (accepted) 0 else 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordInteractionOutcome(topic: String, hourOfDay: Int, wasSuccessful: Boolean) {
        val profile = ensureProfile()
        val timeDelta = if (hourOfDay in 21..23 || hourOfDay in 0..5) 0.04f else 0.02f
        preferenceProfileDao.upsert(
            profile.copy(
                nightActivityWeight = if (hourOfDay >= 21 || hourOfDay <= 5) {
                    clamp(profile.nightActivityWeight + if (wasSuccessful) timeDelta else -timeDelta)
                } else profile.nightActivityWeight,
                morningActivityWeight = if (hourOfDay in 5..11) {
                    clamp(profile.morningActivityWeight + if (wasSuccessful) timeDelta else -timeDelta)
                } else profile.morningActivityWeight,
                totalInteractions = profile.totalInteractions + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun rankRecommendations(
        candidates: List<LiveRecommendation>,
        currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): List<RankedSuggestion> {
        val profile = ensureProfile()
        return candidates.map { rec ->
            val topicBoost = topicWeightFor(rec.text, profile)
            val timeBoost = if (currentHour >= 21 || currentHour <= 5) profile.nightActivityWeight * 0.15f
            else if (currentHour in 5..11) profile.morningActivityWeight * 0.15f else 0f
            val priorityScore = (6 - rec.priority.coerceIn(1, 5)) * 0.1f
            val score = clamp01(topicBoost + timeBoost + priorityScore + profile.interruptionTolerance * 0.1f)
            val confidence = clamp01(0.4f + profile.totalInteractions * 0.005f)
            RankedSuggestion(rec.text, rec.category, score, confidence)
        }.sortedByDescending { it.score }.also { ranked ->
            ranked.forEach { item ->
                suggestionScoreDao.insert(
                    SuggestionScoreEntity(
                        text = item.text,
                        category = item.category,
                        score = item.score,
                        confidence = item.confidence
                    )
                )
            }
        }
    }

    suspend fun shouldSurfaceProactiveInsight(): Boolean {
        val profile = ensureProfile()
        return profile.interruptionTolerance >= profile.proactiveInsightThreshold &&
            profile.ignoredSuggestions <= profile.acceptedSuggestions + 3
    }

    suspend fun acceptSuggestion(id: Long) {
        suggestionScoreDao.markAccepted(id, true)
        recordFeedback("suggestion", id.toString(), accepted = true)
    }

    suspend fun dismissSuggestion(id: Long) {
        suggestionScoreDao.markAccepted(id, false)
        recordFeedback("suggestion", id.toString(), accepted = false)
    }

    fun responseLengthHint(profile: PreferenceProfileEntity): String =
        if (profile.shortResponseWeight > 0.6f) "Keep replies very brief — under 2 sentences."
        else if (profile.shortResponseWeight < 0.4f) "User prefers detailed, thorough responses."
        else "Keep replies concise — under 3 sentences."

    private fun topicWeightFor(text: String, profile: PreferenceProfileEntity): Float {
        val lower = text.lowercase()
        return when {
            lower.contains("decision") -> profile.decisionTopicWeight
            lower.contains("goal") -> profile.goalTopicWeight
            lower.contains("learn") -> profile.learningTopicWeight
            lower.contains("business") || lower.contains("startup") -> profile.businessTopicWeight
            else -> 0.5f
        }
    }

    private fun defaultProfile() = PreferenceProfileEntity()

    private fun clamp(value: Float) = value.coerceIn(0.05f, 0.95f)
    private fun clamp01(value: Float) = value.coerceIn(0f, 1f)
}
