package com.coinv.app.llm

import android.util.Log
import com.coinv.app.BuildConfig
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.repository.cosineSimilarity
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DecisionPatternRetriever @Inject constructor(
    private val decisionDao: DecisionDao
) {
    /**
     * Embeds [currentQuestion], scores against resolved decisions that already have
     * stored embeddings, returns top matches above [ContextAssembler.MIN_RELEVANCE_THRESHOLD].
     */
    suspend fun findSimilarPastDecisions(
        currentQuestion: String,
        topK: Int = 3
    ): List<DecisionEntity> {
        val queryEmbedding = embedText(BuildConfig.GEMINI_API_KEY, currentQuestion).getOrElse {
            Log.w(TAG, "Pattern retrieval skipped (embed failed): ${it.message}")
            return emptyList()
        }

        val resolved = decisionDao.getResolvedWithEmbeddings()
        if (resolved.isEmpty()) return emptyList()

        return resolved.mapNotNull { decision ->
            val stored = decodeEmbedding(decision.embedding) ?: return@mapNotNull null
            val score = cosineSimilarity(queryEmbedding, stored)
            if (score >= ContextAssembler.MIN_RELEVANCE_THRESHOLD) {
                decision to score
            } else {
                null
            }
        }
            .sortedWith { a, b ->
                val simDiff = abs(a.second - b.second)
                when {
                    simDiff <= 0.05f -> b.first.createdAt.compareTo(a.first.createdAt)
                    else -> b.second.compareTo(a.second)
                }
            }
            .take(topK)
            .map { it.first }
    }

    companion object {
        private const val TAG = "DecisionPatterns"

        fun encodeEmbedding(values: FloatArray): String {
            val array = JSONArray()
            values.forEach { array.put(it.toDouble()) }
            return array.toString()
        }

        fun decodeEmbedding(json: String?): FloatArray? {
            if (json.isNullOrBlank()) return null
            return try {
                val array = JSONArray(json)
                FloatArray(array.length()) { i -> array.getDouble(i).toFloat() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode decision embedding: ${e.message}")
                null
            }
        }
    }
}
