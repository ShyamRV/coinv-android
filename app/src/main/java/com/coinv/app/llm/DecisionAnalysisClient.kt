package com.coinv.app.llm

import android.util.Log
import com.coinv.app.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DecisionAnalysis(
    val pros: List<String>,
    val cons: List<String>,
    val risks: List<String>,
    val opportunities: List<String>,
    val missingInformation: List<String>,
    val confidenceScore: Float,
    val recommendation: String
)

private val DECISION_SYSTEM_PROMPT = """
You are analyzing a real decision for someone. Respond with ONLY a valid JSON
object, no markdown formatting, no code fences, no explanation before or
after — your entire response must be parseable as JSON directly. Use exactly
this shape:
{
  "pros": ["short specific point", ...],
  "cons": ["short specific point", ...],
  "risks": ["short specific point", ...],
  "opportunities": ["short specific point", ...],
  "missing_information": ["what would make this analysis better", ...],
  "confidence_score": 0.0 to 1.0,
  "recommendation": "one clear paragraph, direct, acknowledges tradeoffs"
}
Rules:
- 2-5 items per list. Do not pad with generic filler to hit a count.
- confidence_score must genuinely reflect uncertainty — if the decision
  depends heavily on missing information, score below 0.5. Do not default to
  high confidence. A confident-sounding wrong answer is worse than an honest
  low score.
- If you don't have enough context to analyze this meaningfully, say so
  directly in "recommendation" and keep confidence_score low — do not
  fabricate analysis to fill the structure.
""".trimIndent()

suspend fun analyzeDecisionStructured(
    question: String,
    contextNotes: String?,
    assembledContext: String,
    apiKey: String
): Result<DecisionAnalysis> = withContext(Dispatchers.IO) {
    val userMessage = buildString {
        append("Decision question: ").append(question.trim())
        if (!contextNotes.isNullOrBlank()) {
            append("\n\nUser-provided context:\n").append(contextNotes.trim())
        }
        if (assembledContext.isNotBlank()) {
            append("\n\n").append(assembledContext.trim())
        }
    }

    val rawResult = askAsiOne(
        apiKey,
        DECISION_SYSTEM_PROMPT,
        listOf(Message("user", userMessage)),
        maxTokens = 700
    )
    val raw = rawResult.getOrElse { return@withContext Result.failure(it) }

    try {
        Result.success(parseDecisionAnalysisJson(raw))
    } catch (e: Exception) {
        Log.e(TAG, "Could not parse decision analysis. Raw response:\n$raw", e)
        Result.failure(
            Exception("Could not parse decision analysis — raw response logged", e)
        )
    }
}

private fun parseDecisionAnalysisJson(rawResponse: String): DecisionAnalysis {
    val cleaned = rawResponse.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val json = JSONObject(cleaned)

    val pros = requireStringList(json, "pros")
    val cons = requireStringList(json, "cons")
    val risks = requireStringList(json, "risks")
    val opportunities = requireStringList(json, "opportunities")
    val missing = requireStringList(json, "missing_information")
    if (!json.has("confidence_score")) {
        throw IllegalArgumentException("Missing confidence_score")
    }
    val confidence = json.getDouble("confidence_score").toFloat().coerceIn(0f, 1f)
    val recommendation = json.getString("recommendation").trim()
    if (recommendation.isBlank()) {
        throw IllegalArgumentException("Empty recommendation")
    }

    return DecisionAnalysis(
        pros = pros,
        cons = cons,
        risks = risks,
        opportunities = opportunities,
        missingInformation = missing,
        confidenceScore = confidence,
        recommendation = recommendation
    )
}

private fun requireStringList(json: JSONObject, key: String): List<String> {
    if (!json.has(key)) throw IllegalArgumentException("Missing JSON field: $key")
    val array = json.getJSONArray(key)
    return buildList {
        for (i in 0 until array.length()) {
            val value = array.getString(i).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}

fun encodeStringList(items: List<String>): String {
    val array = JSONArray()
    items.forEach { array.put(it) }
    return array.toString()
}

fun formatStoredStringList(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val value = array.getString(i).trim()
                if (value.isNotBlank()) add("• $value")
            }
        }.joinToString("\n")
    } catch (_: Exception) {
        raw
    }
}

private const val TAG = "DecisionAnalysis"
