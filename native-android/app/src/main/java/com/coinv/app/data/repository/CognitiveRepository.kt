package com.coinv.app.data.repository



import com.coinv.app.BuildConfig

import com.coinv.app.data.analytics.CognitiveMetrics

import com.coinv.app.data.analytics.LiveRecommendation

import com.coinv.app.data.insights.InsightEngine

import com.coinv.app.data.local.dao.DecisionDao

import com.coinv.app.data.local.dao.GoalDao

import com.coinv.app.data.local.dao.InsightDao

import com.coinv.app.data.local.dao.LearningDao

import com.coinv.app.data.local.dao.VaultMemoryDao

import com.coinv.app.data.local.dao.MessageDao

import com.coinv.app.data.local.dao.ProfileDao

import com.coinv.app.data.local.dao.TaskDao

import com.coinv.app.data.local.dao.TimelineDao

import com.coinv.app.data.local.dao.VoiceSessionDao

import com.coinv.app.data.local.entity.DecisionEntity

import com.coinv.app.data.local.entity.DecisionStatuses

import com.coinv.app.data.local.entity.GoalEntity

import com.coinv.app.data.local.entity.InsightEntity

import com.coinv.app.data.local.entity.VaultMemoryEntity

import com.coinv.app.data.local.entity.TaskEntity

import com.coinv.app.data.local.entity.TimelineEventEntity

import com.coinv.app.llm.ContextAssembler

import com.coinv.app.llm.DecisionPatternRetriever

import com.coinv.app.llm.analyzeDecisionStructured

import com.coinv.app.llm.encodeStringList

import com.coinv.app.llm.embedText

import com.coinv.app.llm.generateDailyInsight

import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

import java.util.Calendar

import javax.inject.Inject

import javax.inject.Singleton



@OptIn(ExperimentalCoroutinesApi::class)

@Singleton

class CognitiveRepository @Inject constructor(

    private val messageDao: MessageDao,

    private val voiceSessionDao: VoiceSessionDao,

    private val vaultMemoryDao: VaultMemoryDao,

    private val goalDao: GoalDao,

    private val taskDao: TaskDao,

    private val learningDao: LearningDao,

    private val decisionDao: DecisionDao,

    private val insightDao: InsightDao,

    private val timelineDao: TimelineDao,

    private val profileDao: ProfileDao,

    private val insightEngine: InsightEngine,

    private val contextAssembler: ContextAssembler,

    private val semanticMemoryRepository: SemanticMemoryRepository

) {

    fun observeProfile() = profileDao.observeProfile()

    fun observeTimeline() = timelineDao.observeAll()



    fun observeInsightsToday(): Flow<List<InsightEntity>> =
        insightDao.observeToday(startOfTodayMillis())

    fun observeMetricsLive(): Flow<CognitiveMetrics> = combine(
        combine(
            timelineDao.observeAll(),
            goalDao.observeAll(),
            vaultMemoryDao.observeAll(),
            messageDao.observeRecent(5),
            decisionDao.observeAll()
        ) { _, _, _, _, _ -> Unit },
        voiceSessionDao.observeRecent()
    ) { _, _ -> Unit }.flatMapLatest {
        flow {
            try {
                emit(computeMetrics())
            } catch (_: Throwable) {
                emit(CognitiveMetrics())
            }
        }
    }

    suspend fun computeMetrics(): CognitiveMetrics {

        val startOfDay = startOfTodayMillis()

        val sessionsToday = voiceSessionDao.countSince(startOfDay)

        val messagesToday = messageDao.countSince(startOfDay)

        val totalDuration = voiceSessionDao.totalDurationSince(startOfDay)

        val memoriesTotal = vaultMemoryDao.countAll()

        val memoriesToday = vaultMemoryDao.countSince(startOfDay)

        val goalProgress = goalDao.averageProgress()?.toInt() ?: 0

        val learningProgress = learningDao.averageProgress()?.toInt() ?: 0

        val pendingDecisions = decisionDao.countPending()

        val goalsActive = goalDao.countActive()

        val tasksDone = taskDao.countCompletedAll()



        val focusScore = when {

            messagesToday >= 10 -> 100

            messagesToday >= 5 -> 80

            sessionsToday >= 2 -> 70

            sessionsToday >= 1 -> 50

            else -> (messagesToday * 10).coerceIn(0, 40)

        }

        val mentalEnergy = when {

            totalDuration > 30 * 60_000 -> 95

            totalDuration > 15 * 60_000 -> 80

            totalDuration > 5 * 60_000 -> 65

            sessionsToday > 0 -> 50

            else -> 25

        }.coerceIn(0, 100)



        return CognitiveMetrics(

            focusScore = focusScore,

            mentalEnergy = mentalEnergy,

            learningVelocity = learningProgress,

            decisionReadiness = when {

                pendingDecisions > 0 -> 90

                decisionDao.countSince(startOfDay) > 0 -> 75

                else -> (messagesToday * 8).coerceIn(0, 60)

            },

            memoryActivity = (memoriesToday * 30 + memoriesTotal.coerceAtMost(20) * 3).coerceIn(0, 100),

            aiConfidence = if (messagesToday > 0) (70 + messagesToday.coerceAtMost(10) * 2) else 40,

            dailyProgress = goalProgress,

            voiceSessionsToday = sessionsToday,

            messagesToday = messagesToday,

            memoriesTotal = memoriesTotal,

            goalsActive = goalsActive,

            tasksCompleted = tasksDone,

            decisionsPending = pendingDecisions

        )

    }



    suspend fun buildDailySummary(): String {

        val m = computeMetrics()

        val name = profileDao.observeProfile().first()?.name ?: "Shyam"

        return when {

            m.messagesToday == 0 && m.voiceSessionsToday == 0 ->

                "$name, your cognitive loop is quiet today. Start a voice session to activate CoinV."

            m.goalsActive > 0 && m.dailyProgress > 0 ->

                "Welcome back, $name. ${m.voiceSessionsToday} sessions, ${m.messagesToday} messages, goals at ${m.dailyProgress}%."

            else ->

                "Good progress, $name — ${m.voiceSessionsToday} voice sessions and ${m.memoriesTotal} memories in your vault."

        }

    }



    suspend fun buildRecommendations(): List<LiveRecommendation> {
        val metrics = computeMetrics()
        val recs = mutableListOf<LiveRecommendation>()
        if (metrics.voiceSessionsToday == 0) {
            recs.add(LiveRecommendation("Start Listening mode to activate your cognitive loop.", 1, "voice", "productivity"))
        }
        if (metrics.goalsActive == 0) {
            recs.add(LiveRecommendation("Create a goal to track real progress.", 2, "decisions", "goals"))
        }
        if (metrics.decisionsPending > 0) {
            recs.add(LiveRecommendation("You have ${metrics.decisionsPending} pending decision(s) to resolve.", 1, "decisions", "decisions"))
        }
        if (metrics.memoriesTotal == 0) {
            recs.add(LiveRecommendation("Capture your first idea in Memory Vault.", 3, "memory", "learning"))
        }
        if (metrics.focusScore >= 60) {
            recs.add(LiveRecommendation("Focus is high — ideal for deep work.", 2, null, "productivity"))
        }
        return recs.take(5)
    }



    suspend fun updateCognitiveStateFromActivity() {

        val metrics = computeMetrics()

        val state = when {

            metrics.voiceSessionsToday >= 3 -> "Deep Focus"

            metrics.messagesToday >= 5 -> "Active Thinking"

            metrics.learningVelocity >= 50 -> "Learning Mode"

            metrics.decisionReadiness >= 80 -> "Decision Ready"

            metrics.voiceSessionsToday > 0 -> "Engaged"

            else -> "Ready"

        }

        profileDao.updateCognitiveState(state)

        insightEngine.refreshActivityInsights()

    }



    suspend fun maybeGenerateInsight() {

        val metrics = computeMetrics()

        if (metrics.messagesToday == 0) return

        val todayStart = startOfTodayMillis()

        val existing = insightDao.getSince(todayStart)

        val hasLlmToday = existing.any { it.category == "daily" }

        if (hasLlmToday) return



        val summary = buildString {

            append("Sessions: ${metrics.voiceSessionsToday}. ")

            append("Messages: ${metrics.messagesToday}. ")

            append("Memories: ${metrics.memoriesTotal}. ")

            append("Goal progress: ${metrics.dailyProgress}%.")

        }



        val insightText = generateDailyInsight(BuildConfig.ASI_ONE_API_KEY, summary).getOrNull() ?: return

        insightDao.insert(InsightEntity(text = insightText, category = "daily", createdAt = System.currentTimeMillis()))

        timelineDao.insert(TimelineEventEntity(type = "insight", title = "Daily insight", description = insightText))

    }



    /**
     * Structured decision analysis. On LLM/parse failure throws — callers must show an
     * explicit error and must not persist an empty placeholder analysis.
     */
    suspend fun createDecisionWithAnalysis(question: String, context: String = ""): Long {
        val memoryContext = contextAssembler.assembleContext(question)
        val analysis = analyzeDecisionStructured(
            question = question,
            contextNotes = context.ifBlank { null },
            assembledContext = memoryContext,
            apiKey = BuildConfig.ASI_ONE_API_KEY
        ).getOrElse { throw it }

        val createdAt = System.currentTimeMillis()
        val embeddingJson = embedText(BuildConfig.GEMINI_API_KEY, question).getOrNull()?.let {
            DecisionPatternRetriever.encodeEmbedding(it)
        }

        val id = decisionDao.insert(
            DecisionEntity(
                question = question.trim(),
                context = context.trim(),
                pros = encodeStringList(analysis.pros),
                cons = encodeStringList(analysis.cons),
                risks = encodeStringList(analysis.risks),
                opportunities = encodeStringList(analysis.opportunities),
                missingInfo = encodeStringList(analysis.missingInformation),
                recommendation = analysis.recommendation,
                confidenceScore = analysis.confidenceScore,
                status = DecisionStatuses.PENDING_OUTCOME,
                createdAt = createdAt,
                outcomeFollowUpAt = createdAt + DecisionStatuses.FOLLOWUP_MS,
                embedding = embeddingJson
            )
        )

        semanticMemoryRepository.remember(
            content = "Decision: ${question.trim()}. Recommendation: ${analysis.recommendation}",
            layer = "episodic",
            sourceType = "decision",
            sourceId = id,
            importance = 0.7f
        )

        timelineDao.insert(
            TimelineEventEntity(
                type = "decision",
                title = "Decision analyzed",
                description = question.take(120)
            )
        )
        insightEngine.refreshActivityInsights()
        return id
    }



    suspend fun createGoal(title: String, description: String, tasks: List<String> = emptyList()) {

        val goalId = goalDao.insert(GoalEntity(title = title, description = description, progress = 0, status = "active"))

        tasks.filter { it.isNotBlank() }.forEach { taskDao.insert(TaskEntity(goalId = goalId, title = it)) }

        if (tasks.isNotEmpty()) recalculateGoalProgress(goalId)

        timelineDao.insert(TimelineEventEntity(type = "goal", title = "Goal created", description = title))

        insightEngine.refreshActivityInsights()

    }



    suspend fun addTask(goalId: Long, title: String) {

        taskDao.insert(TaskEntity(goalId = goalId, title = title))

        recalculateGoalProgress(goalId)

    }



    suspend fun toggleTask(taskId: Long, goalId: Long, completed: Boolean) {

        taskDao.setCompleted(taskId, completed)

        recalculateGoalProgress(goalId)

    }



    suspend fun recalculateGoalProgress(goalId: Long) {

        val total = taskDao.countByGoal(goalId)

        if (total == 0) return

        val done = taskDao.countCompletedByGoal(goalId)

        val progress = ((done.toFloat() / total) * 100).toInt().coerceIn(0, 100)

        val status = if (progress >= 100) "completed" else "active"

        goalDao.updateStatus(goalId, status, progress)

        if (progress >= 100) {

            timelineDao.insert(TimelineEventEntity(type = "goal", title = "Goal completed", description = "100% progress"))

            insightEngine.refreshActivityInsights()

        }

    }



    suspend fun saveMemory(title: String, content: String, category: String, tags: String) {

        vaultMemoryDao.insert(VaultMemoryEntity(title = title, content = content, category = category, tags = tags))

        timelineDao.insert(TimelineEventEntity(type = "memory", title = title, description = content.take(120)))

        if (category == "learning") {

            learningDao.insert(

                com.coinv.app.data.local.entity.LearningItemEntity(

                    topic = title,

                    summary = content.take(200),

                    progress = 10,

                    flashcardsCount = 0

                )

            )

        }

        insightEngine.refreshActivityInsights()

    }



    suspend fun clearAllMemories() {

        vaultMemoryDao.deleteAll()

    }



    private fun startOfTodayMillis(): Long {

        val cal = Calendar.getInstance().apply {

            set(Calendar.HOUR_OF_DAY, 0)

            set(Calendar.MINUTE, 0)

            set(Calendar.SECOND, 0)

            set(Calendar.MILLISECOND, 0)

        }

        return cal.timeInMillis

    }

}


