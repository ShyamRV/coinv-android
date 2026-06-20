package com.coinv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 5): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): ConversationEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countAll(): Int

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<MessageEntity>>
}
@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long

    @Query("SELECT AVG(progress) FROM goals WHERE status = 'active'")
    suspend fun averageProgress(): Float?

    @Query("UPDATE goals SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query("UPDATE goals SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, progress: Int)

    @Query("SELECT COUNT(*) FROM goals WHERE status = 'active'")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM goals WHERE status = 'completed'")
    suspend fun countCompleted(): Int

    @Query("SELECT COUNT(*) FROM goals WHERE status = 'completed' AND createdAt >= :since")
    suspend fun countCompletedSince(since: Long): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE goalId = :goalId")
    fun observeByGoal(goalId: Long): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("SELECT COUNT(*) FROM tasks WHERE goalId = :goalId")
    suspend fun countByGoal(goalId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE goalId = :goalId AND completed = 1")
    suspend fun countCompletedByGoal(goalId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 1")
    suspend fun countCompletedAll(): Int
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decisions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: DecisionEntity): Long

    @Query("UPDATE decisions SET outcome = :outcome, status = 'resolved' WHERE id = :id")
    suspend fun updateOutcome(id: Long, outcome: String)

    @Query("SELECT COUNT(*) FROM decisions WHERE status != 'resolved'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM decisions WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM insights ORDER BY createdAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE createdAt >= :since ORDER BY createdAt DESC")
    suspend fun getSince(since: Long): List<InsightEntity>

    @Query("SELECT * FROM insights WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun observeToday(since: Long): Flow<List<InsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: InsightEntity): Long
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM learning_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LearningItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LearningItemEntity): Long

    @Query("SELECT AVG(progress) FROM learning_items")
    suspend fun averageProgress(): Float?
}

@Dao
interface VoiceSessionDao {
    @Query("SELECT * FROM voice_sessions ORDER BY createdAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<VoiceSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: VoiceSessionEntity): Long

    @Query("SELECT COUNT(*) FROM voice_sessions WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM voice_sessions WHERE createdAt >= :since")
    suspend fun totalDurationSince(since: Long): Long
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET cognitiveState = :state WHERE id = 1")
    suspend fun updateCognitiveState(state: String)

    @Query("UPDATE user_profile SET activeCoach = :coach WHERE id = 1")
    suspend fun updateCoach(coach: String)

    @Query("UPDATE user_profile SET name = :name WHERE id = 1")
    suspend fun updateName(name: String)

    @Query("UPDATE user_profile SET listeningMode = :mode, wakeWordEnabled = :wakeWord, continuousListening = :continuous WHERE id = 1")
    suspend fun updateListeningSettings(mode: String, wakeWord: Boolean, continuous: Boolean)
}

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics WHERE id = 1")
    fun observe(): Flow<AnalyticsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analytics: AnalyticsEntity)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TimelineEventEntity): Long
}

@Dao
interface RecommendationDao {
    @Query("SELECT * FROM recommendations ORDER BY priority ASC, createdAt DESC LIMIT 6")
    fun observeAll(): Flow<List<RecommendationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recommendation: RecommendationEntity): Long
}

@Dao
interface SeedDao {
    @Query("SELECT COUNT(*) FROM user_profile")
    suspend fun profileCount(): Int
}
