package com.coinv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coinv.app.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemanticMemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM semantic_memories WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: Long): MemoryEntity?

    @Query("SELECT * FROM semantic_memories WHERE layer = :layer ORDER BY timestamp DESC")
    suspend fun getByLayer(layer: String): List<MemoryEntity>

    @Query("SELECT * FROM semantic_memories ORDER BY timestamp DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM semantic_memories WHERE layer = 'value' ORDER BY timestamp DESC")
    suspend fun getValueMemories(): List<MemoryEntity>

    @Query("SELECT * FROM semantic_memories WHERE layer = 'value' ORDER BY timestamp DESC")
    fun observeValueMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT COUNT(*) FROM semantic_memories")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM semantic_memories")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM semantic_memories")
    suspend fun clearAll()
}
