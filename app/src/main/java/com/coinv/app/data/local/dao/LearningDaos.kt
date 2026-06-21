package com.coinv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coinv.app.data.local.entity.ContextEventEntity
import com.coinv.app.data.local.entity.FeedbackEventEntity
import com.coinv.app.data.local.entity.ModeHistoryEntity
import com.coinv.app.data.local.entity.PreferenceProfileEntity
import com.coinv.app.data.local.entity.SuggestionScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ContextEventEntity): Long

    @Query("SELECT * FROM context_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ContextEventEntity>>

    @Query("SELECT * FROM context_events WHERE createdAt >= :since ORDER BY createdAt DESC")
    suspend fun getSince(since: Long): List<ContextEventEntity>

    @Query("SELECT COUNT(*) FROM context_events WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM context_events WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface FeedbackEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: FeedbackEventEntity): Long

    @Query("SELECT * FROM feedback_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<FeedbackEventEntity>>

    @Query("SELECT COUNT(*) FROM feedback_events WHERE accepted = 1")
    suspend fun countAccepted(): Int

    @Query("SELECT COUNT(*) FROM feedback_events WHERE accepted = 0")
    suspend fun countIgnored(): Int
}

@Dao
interface PreferenceProfileDao {
    @Query("SELECT * FROM preference_profile WHERE id = 1")
    fun observe(): Flow<PreferenceProfileEntity?>

    @Query("SELECT * FROM preference_profile WHERE id = 1")
    suspend fun get(): PreferenceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PreferenceProfileEntity)
}

@Dao
interface ModeHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ModeHistoryEntity): Long

    @Query("SELECT * FROM mode_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<ModeHistoryEntity>>
}

@Dao
interface SuggestionScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: SuggestionScoreEntity): Long

    @Query("SELECT * FROM suggestion_scores ORDER BY score DESC, createdAt DESC LIMIT :limit")
    fun observeTop(limit: Int = 10): Flow<List<SuggestionScoreEntity>>

    @Query("UPDATE suggestion_scores SET accepted = :accepted WHERE id = :id")
    suspend fun markAccepted(id: Long, accepted: Boolean)

    @Query("SELECT * FROM suggestion_scores WHERE surfaced = 0 ORDER BY score DESC LIMIT :limit")
    suspend fun getPending(limit: Int = 5): List<SuggestionScoreEntity>
}
