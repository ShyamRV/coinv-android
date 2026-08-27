package com.coinv.app.data.repository

import android.util.Log
import com.coinv.app.BuildConfig
import com.coinv.app.data.local.dao.SemanticMemoryDao
import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.llm.embedText
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ScoredMemory(
    val memory: MemoryEntity,
    val similarity: Float
)

@Singleton
class SemanticMemoryRepository @Inject constructor(
    private val semanticMemoryDao: SemanticMemoryDao
) {
    fun observeCount(): Flow<Int> = semanticMemoryDao.observeCount()

    /**
     * Best-effort write — never throws; embedding failures are logged and skipped so
     * the voice/chat pipeline is never blocked.
     */
    suspend fun remember(
        content: String,
        layer: String,
        sourceType: String,
        sourceId: Long? = null,
        importance: Float = 0.5f
    ) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        embedText(BuildConfig.GEMINI_API_KEY, trimmed).fold(
            onSuccess = { embedding ->
                semanticMemoryDao.insert(
                    MemoryEntity(
                        content = trimmed,
                        layer = layer,
                        embedding = encodeEmbedding(embedding),
                        sourceType = sourceType,
                        sourceId = sourceId,
                        timestamp = System.currentTimeMillis(),
                        importance = importance.coerceIn(0f, 1f)
                    )
                )
            },
            onFailure = { error ->
                Log.w(TAG, "Semantic memory write skipped: ${error.message}")
            }
        )
    }

    /**
     * On embedding failure returns empty list — LLM proceeds with no injected memory
     * rather than failing the whole interaction.
     */
    suspend fun recall(query: String, topK: Int = 6): List<ScoredMemory> {
        val queryEmbedding = embedText(BuildConfig.GEMINI_API_KEY, query).getOrElse {
            Log.w(TAG, "Semantic recall degraded (no context): ${it.message}")
            return emptyList()
        }

        val all = semanticMemoryDao.getAll()
        if (all.isEmpty()) return emptyList()

        return all.mapNotNull { memory ->
            decodeEmbedding(memory.embedding)?.let { stored ->
                ScoredMemory(memory, cosineSimilarity(queryEmbedding, stored))
            }
        }.sortedWith { a, b ->
            // Within 0.05 similarity, prefer recency then importance; otherwise rank by similarity.
            val simDiff = abs(a.similarity - b.similarity)
            when {
                simDiff <= SIMILARITY_TIEBREAK_DELTA -> {
                    val timeCmp = b.memory.timestamp.compareTo(a.memory.timestamp)
                    if (timeCmp != 0) timeCmp
                    else b.memory.importance.compareTo(a.memory.importance)
                }
                else -> b.similarity.compareTo(a.similarity)
            }
        }.take(topK)
    }

    suspend fun getValueMemories(): List<MemoryEntity> = semanticMemoryDao.getValueMemories()

    fun observeValueMemories(): Flow<List<MemoryEntity>> = semanticMemoryDao.observeValueMemories()

    suspend fun clearAll() = semanticMemoryDao.clearAll()

    suspend fun getProfileField(sourceId: Long): MemoryEntity? =
        semanticMemoryDao.getBySourceId(sourceId)

    /**
     * Upserts a profile About Me field by fixed [sourceId] sentinel so re-saves update
     * the same row instead of accumulating stale duplicates.
     */
    suspend fun rememberProfileField(
        sourceId: Long,
        content: String,
        importance: Float = 1.0f
    ): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return false

        val embedding = embedText(BuildConfig.GEMINI_API_KEY, trimmed).getOrElse { error ->
            Log.w(TAG, "Profile field write skipped: ${error.message}")
            return false
        }

        val existing = semanticMemoryDao.getBySourceId(sourceId)
        val entity = MemoryEntity(
            id = existing?.id ?: 0,
            content = trimmed,
            layer = "value",
            embedding = encodeEmbedding(embedding),
            sourceType = "user_stated",
            sourceId = sourceId,
            timestamp = System.currentTimeMillis(),
            importance = importance.coerceIn(0f, 1f)
        )
        if (existing != null) {
            semanticMemoryDao.update(entity.copy(id = existing.id))
        } else {
            semanticMemoryDao.insert(entity)
        }
        return true
    }

    private fun encodeEmbedding(values: FloatArray): String {
        val array = JSONArray()
        values.forEach { array.put(it.toDouble()) }
        return array.toString()
    }

    private fun decodeEmbedding(json: String): FloatArray? = try {
        val array = JSONArray(json)
        FloatArray(array.length()) { i -> array.getDouble(i).toFloat() }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode stored embedding: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "SemanticMemory"
        private const val SIMILARITY_TIEBREAK_DELTA = 0.05f
    }
}
