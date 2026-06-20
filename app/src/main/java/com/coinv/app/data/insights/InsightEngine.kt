package com.coinv.app.data.insights

import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.GoalDao
import com.coinv.app.data.local.dao.InsightDao
import com.coinv.app.data.local.dao.MemoryDao
import com.coinv.app.data.local.dao.MessageDao
import com.coinv.app.data.local.dao.TaskDao
import com.coinv.app.data.local.dao.VoiceSessionDao
import com.coinv.app.data.local.entity.InsightEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightEngine @Inject constructor(
    private val insightDao: InsightDao,
    private val messageDao: MessageDao,
    private val voiceSessionDao: VoiceSessionDao,
    private val memoryDao: MemoryDao,
    private val goalDao: GoalDao,
    private val taskDao: TaskDao,
    private val decisionDao: DecisionDao
) {
    suspend fun refreshActivityInsights() {
        val startOfDay = startOfTodayMillis()

        val sessionsToday = voiceSessionDao.countSince(startOfDay)
        val messagesToday = messageDao.countSince(startOfDay)
        val memoriesToday = memoryDao.countSince(startOfDay)
        val goalsCompleted = goalDao.countCompleted()
        val tasksCompleted = taskDao.countCompletedAll()
        val decisionsToday = decisionDao.countSince(startOfDay)
        val activeGoals = goalDao.countActive()
        val avgProgress = goalDao.averageProgress()?.toInt() ?: 0

        val candidates = mutableListOf<String>()

        if (sessionsToday > 0) {
            candidates.add("You completed $sessionsToday voice session${if (sessionsToday == 1) "" else "s"} today.")
        }
        if (messagesToday > 0) {
            candidates.add("You exchanged $messagesToday messages with CoinV today.")
        }
        if (memoriesToday > 0) {
            candidates.add("You captured $memoriesToday new idea${if (memoriesToday == 1) "" else "s"} today.")
        }
        if (goalsCompleted > 0) {
            candidates.add("You have completed $goalsCompleted goal${if (goalsCompleted == 1) "" else "s"}.")
        }
        if (tasksCompleted > 0) {
            candidates.add("You finished $tasksCompleted task${if (tasksCompleted == 1) "" else "s"} total.")
        }
        if (decisionsToday > 0) {
            candidates.add("You analyzed $decisionsToday decision${if (decisionsToday == 1) "" else "s"} today.")
        }
        if (activeGoals > 0 && avgProgress > 0) {
            candidates.add("Your active goals are $avgProgress% complete on average.")
        }

        val existingToday = insightDao.getSince(startOfDay).map { it.text }.toSet()
        candidates
            .filter { it !in existingToday }
            .take(3)
            .forEach { text ->
                insightDao.insert(
                    InsightEntity(text = text, category = "activity", createdAt = System.currentTimeMillis())
                )
            }
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

    private fun startOfWeekMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
