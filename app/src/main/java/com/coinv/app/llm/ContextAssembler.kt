package com.coinv.app.llm

import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.data.repository.ScoredMemory
import com.coinv.app.data.repository.SemanticMemoryRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the memory context block prepended to the system prompt before ASI:One calls.
 * Weakly related memories (below [MIN_RELEVANCE_THRESHOLD]) are excluded — silence is
 * better than confidently wrong context injection.
 */
@Singleton
class ContextAssembler @Inject constructor(
    private val semanticMemoryRepository: SemanticMemoryRepository
) {
    suspend fun assembleContext(userQuery: String): String {
        val recalled = semanticMemoryRepository.recall(userQuery, topK = 6)
        val valueMemories = semanticMemoryRepository.getValueMemories()

        val relevant = recalled.filter { it.similarity >= MIN_RELEVANCE_THRESHOLD }
        if (relevant.isEmpty() && valueMemories.isEmpty()) return ""

        val valueIds = valueMemories.map { it.id }.toSet()
        val merged = buildList {
            valueMemories.forEach { add(ScoredMemory(it, 1f)) }
            relevant.forEach { scored ->
                if (scored.memory.id !in valueIds) add(scored)
            }
        }

        if (merged.isEmpty()) return ""

        val lines = merged.map { formatMemoryLine(it.memory) }
        return buildString {
            append("Relevant context from what you know about this user:")
            lines.forEach { append("\n- $it") }
        }
    }

    private fun formatMemoryLine(memory: MemoryEntity): String {
        val age = formatRelativeTime(memory.timestamp)
        return "[${memory.layer}, $age] ${memory.content}"
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} hours ago"
            minutes < 48 * 60 -> "1 day ago"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
            minutes < 30 * 24 * 60 -> "${minutes / (7 * 24 * 60)} weeks ago"
            else -> "${minutes / (30 * 24 * 60)} months ago"
        }
    }

    companion object {
        /** Below this cosine similarity, memories are treated as noise, not signal. */
        const val MIN_RELEVANCE_THRESHOLD = 0.4f
    }
}
