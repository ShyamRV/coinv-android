package com.coinv.app.engine

import com.coinv.app.data.local.dao.ContextEventDao
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.GoalDao
import com.coinv.app.data.local.dao.VaultMemoryDao
import com.coinv.app.data.local.dao.MessageDao
import com.coinv.app.data.local.entity.ContextEventEntity
import com.coinv.app.domain.AppMode
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextEngine @Inject constructor(
    private val contextEventDao: ContextEventDao,
    private val messageDao: MessageDao,
    private val vaultMemoryDao: VaultMemoryDao,
    private val goalDao: GoalDao,
    private val decisionDao: DecisionDao
) {
    fun observeRecentContext(limit: Int = 12): Flow<List<ContextEventEntity>> =
        contextEventDao.observeRecent(limit)

    suspend fun recordSpeech(
        text: String,
        appMode: AppMode,
        source: String = "voice"
    ) {
        val topics = extractTopics(text)
        contextEventDao.insert(
            ContextEventEntity(
                type = "speech_topic",
                payload = text.take(500),
                source = source,
                appMode = appMode.name.lowercase(),
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            )
        )
        topics.forEach { topic ->
            contextEventDao.insert(
                ContextEventEntity(
                    type = "intent_pattern",
                    payload = topic,
                    source = source,
                    appMode = appMode.name.lowercase(),
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                )
            )
        }
    }

    suspend fun recordNavigation(route: String, appMode: AppMode) {
        contextEventDao.insert(
            ContextEventEntity(
                type = "navigation",
                payload = route,
                source = "app",
                appMode = appMode.name.lowercase(),
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            )
        )
    }

    suspend fun recordInteraction(action: String, detail: String, appMode: AppMode) {
        contextEventDao.insert(
            ContextEventEntity(
                type = "interaction",
                payload = "$action:$detail",
                source = "ui",
                appMode = appMode.name.lowercase(),
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            )
        )
    }

    suspend fun buildContextSummary(): String {
        val since = System.currentTimeMillis() - 86_400_000L
        val events = contextEventDao.getSince(since)
        val topics = events.filter { it.type == "intent_pattern" }
            .map { it.payload }
            .distinct()
            .take(8)
        val goals = goalDao.countActive()
        val pendingDecisions = decisionDao.countPending()
        val memories = vaultMemoryDao.countAll()
        val messages = messageDao.countSince(since)

        return buildString {
            if (topics.isNotEmpty()) append("Recent topics: ${topics.joinToString(", ")}. ")
            append("Active goals: $goals. Pending decisions: $pendingDecisions. ")
            append("Memories: $memories. Messages today: $messages.")
        }
    }

    fun isExplicitUserRequest(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isBlank()) return false
        if (lower.contains("coinv") || lower.contains("hey coin")) return true
        if (lower.endsWith("?")) return true
        val questionStarts = listOf(
            "what", "how", "why", "when", "where", "who", "can you", "could you",
            "help me", "tell me", "explain", "should i", "do i", "is it"
        )
        return questionStarts.any { lower.startsWith(it) }
    }

    private fun extractTopics(text: String): List<String> {
        val lower = text.lowercase()
        val keywords = listOf(
            "goal" to "goals",
            "decision" to "decisions",
            "business" to "business",
            "startup" to "business",
            "learn" to "learning",
            "memory" to "memory",
            "focus" to "productivity",
            "career" to "career",
            "meeting" to "schedule",
            "plan" to "planning"
        )
        return keywords.filter { lower.contains(it.first) }.map { it.second }.distinct()
    }
}
