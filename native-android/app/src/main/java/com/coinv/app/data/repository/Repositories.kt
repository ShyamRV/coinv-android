package com.coinv.app.data.repository

import com.coinv.app.data.Message
import com.coinv.app.data.analytics.CognitiveMetrics
import com.coinv.app.data.analytics.LiveRecommendation
import com.coinv.app.data.local.dao.ConversationDao
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.GoalDao
import com.coinv.app.data.local.dao.LearningDao
import com.coinv.app.data.local.dao.VaultMemoryDao
import com.coinv.app.data.local.dao.MessageDao
import com.coinv.app.data.local.dao.ProfileDao
import com.coinv.app.data.local.dao.TaskDao
import com.coinv.app.data.local.dao.TimelineDao
import com.coinv.app.data.local.dao.VoiceSessionDao
import com.coinv.app.data.local.entity.ConversationEntity
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.InsightEntity
import com.coinv.app.data.local.entity.VaultMemoryEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.TimelineEventEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.data.local.entity.VoiceSessionEntity
import com.coinv.app.engine.ContextEngine
import com.coinv.app.engine.PersonalizationEngine
import com.coinv.app.llm.DecisionPatternRetriever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DashboardRepository @Inject constructor(
    private val cognitiveRepository: CognitiveRepository,
    private val messageDao: MessageDao,
    private val decisionDao: DecisionDao,
    private val vaultMemoryDao: VaultMemoryDao,
    private val conversationDao: ConversationDao,
    private val contextEngine: ContextEngine,
    private val personalizationEngine: PersonalizationEngine
) {
    fun observeProfile(): Flow<UserProfileEntity?> = cognitiveRepository.observeProfile()
    fun observeMetrics(): Flow<CognitiveMetrics> = cognitiveRepository.observeMetricsLive()
    fun observeInsights(): Flow<List<InsightEntity>> = cognitiveRepository.observeInsightsToday()
    fun observeTimeline(): Flow<List<TimelineEventEntity>> = cognitiveRepository.observeTimeline()
    fun observeRecentMessages(): Flow<List<MessageEntity>> = messageDao.observeRecent(8)
    fun observeRecentDecisions(): Flow<List<DecisionEntity>> = decisionDao.observeAll()
    fun observeRecentMemories(): Flow<List<VaultMemoryEntity>> = vaultMemoryDao.observeAll()
    fun observeRecentConversations(): Flow<List<ConversationEntity>> = conversationDao.observeRecent(5)
    fun observeRecentContext() = contextEngine.observeRecentContext(10)
    fun observePersonalization() = personalizationEngine.observePersonalizationStatus()
    fun observeRankedSuggestions() = personalizationEngine.observeTopSuggestions(6)

    fun observeRecommendations(): Flow<List<LiveRecommendation>> =
        cognitiveRepository.observeTimeline().flatMapLatest {
            flow {
                try {
                    val raw = cognitiveRepository.buildRecommendations()
                    val ranked = personalizationEngine.rankRecommendations(raw)
                    emit(ranked.map { LiveRecommendation(it.text, 1, null, it.category) })
                } catch (_: Throwable) {
                    emit(emptyList())
                }
            }
        }

    suspend fun dailySummary(): String = cognitiveRepository.buildDailySummary()
}

@Singleton
class VaultMemoryRepository @Inject constructor(
    private val vaultMemoryDao: VaultMemoryDao,
    private val cognitiveRepository: CognitiveRepository
) {
    fun observeAll() = vaultMemoryDao.observeAll()
    fun search(query: String) = vaultMemoryDao.search(query)
    suspend fun save(title: String, content: String, category: String, tags: String) =
        cognitiveRepository.saveMemory(title, content, category, tags)
    suspend fun delete(id: Long) = vaultMemoryDao.delete(id)
    suspend fun clearAll() = cognitiveRepository.clearAllMemories()
}

@Singleton
class DecisionRepository @Inject constructor(
    private val decisionDao: DecisionDao,
    private val goalDao: GoalDao,
    private val taskDao: TaskDao,
    private val cognitiveRepository: CognitiveRepository,
    private val patternRetriever: DecisionPatternRetriever
) {
    fun observeDecisions() = decisionDao.observeAll()
    fun observeGoals() = goalDao.observeAll()
    fun observeTasks(goalId: Long) = taskDao.observeByGoal(goalId)

    suspend fun findSimilarPastDecisions(question: String): List<DecisionEntity> =
        patternRetriever.findSimilarPastDecisions(question)

    suspend fun recordOutcome(id: Long, status: String, notes: String? = null) {
        decisionDao.updateOutcome(
            id = id,
            outcomeNotes = notes,
            status = status,
            askedAt = System.currentTimeMillis()
        )
    }

    suspend fun createDecision(question: String, context: String = "") =
        cognitiveRepository.createDecisionWithAnalysis(question, context)

    suspend fun createGoal(title: String, description: String, tasks: List<String> = emptyList()) =
        cognitiveRepository.createGoal(title, description, tasks)

    suspend fun addTask(goalId: Long, title: String) = cognitiveRepository.addTask(goalId, title)

    suspend fun toggleTask(taskId: Long, goalId: Long, completed: Boolean) =
        cognitiveRepository.toggleTask(taskId, goalId, completed)
}

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val learningDao: LearningDao
) {
    fun observeProfile() = profileDao.observeProfile()
    fun observeLearning() = learningDao.observeAll()
    suspend fun updateCoach(coach: String) = profileDao.updateCoach(coach)
    suspend fun updateName(name: String) = profileDao.updateName(name)
    suspend fun updateListening(mode: String, wakeWord: Boolean, continuous: Boolean) =
        profileDao.updateListeningSettings(mode, wakeWord, continuous)
}

@Singleton
class VoiceRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val voiceSessionDao: VoiceSessionDao,
    private val cognitiveRepository: CognitiveRepository
) {
    private var activeConversationId: Long? = null

    fun observeRecentSessions() = voiceSessionDao.observeRecent()
    fun observeConversations() = conversationDao.observeAll()
    fun observeRecentMessages() = messageDao.observeRecent(50)

    suspend fun ensureConversation(): Long {
        activeConversationId?.let { return it }
        val existing = conversationDao.getLatest()
        if (existing != null) {
            activeConversationId = existing.id
            return existing.id
        }
        val id = conversationDao.insert(ConversationEntity(title = "Voice Session"))
        activeConversationId = id
        return id
    }

    suspend fun startNewConversation(title: String): Long {
        val id = conversationDao.insert(ConversationEntity(title = title))
        activeConversationId = id
        return id
    }

    suspend fun getMessages(conversationId: Long): List<Message> {
        return messageDao.getByConversation(conversationId).map {
            Message(role = it.role, text = it.text, timestamp = it.timestamp)
        }
    }

    suspend fun saveMessage(role: String, text: String) {
        val convId = ensureConversation()
        messageDao.insert(MessageEntity(conversationId = convId, role = role, text = text))
        conversationDao.touch(convId)
    }

    suspend fun saveVoiceSession(transcript: String, durationMs: Long, mode: String) {
        voiceSessionDao.insert(
            VoiceSessionEntity(transcript = transcript, durationMs = durationMs, mode = mode)
        )
        cognitiveRepository.updateCognitiveStateFromActivity()
        cognitiveRepository.maybeGenerateInsight()
    }

    suspend fun captureIdea(text: String) {
        cognitiveRepository.saveMemory(
            title = "Captured Idea",
            content = text,
            category = "idea",
            tags = "voice,captured"
        )
    }
}

@Singleton
class TimelineRepository @Inject constructor(
    private val cognitiveRepository: CognitiveRepository
) {
    fun observeAll() = cognitiveRepository.observeTimeline()
}

