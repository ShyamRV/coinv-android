package com.coinv.app.llm

import com.coinv.app.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private val httpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

const val VOICE_SYSTEM_PROMPT =
    "You are CoinV, a calm, concise voice assistant speaking through the user's earphones. " +
        "Keep every reply under 3 sentences — it will be read aloud by text-to-speech. " +
        "Be warm and direct."

fun coachSystemPrompt(coach: String): String {
    val style = when (coach) {
        "Founder Coach" -> "Focus on startups, product-market fit, and execution."
        "Productivity Coach" -> "Focus on focus, time blocking, and eliminating friction."
        "Learning Coach" -> "Focus on retention, spaced repetition, and curiosity."
        "Career Coach" -> "Focus on growth, networking, and strategic moves."
        "Thinking Coach" -> "Focus on first principles, mental models, and clarity."
        "Decision Coach" -> "Focus on tradeoffs, reversibility, and expected value."
        else -> "Be warm, concise, and actionable."
    }
    return "$VOICE_SYSTEM_PROMPT $style"
}

suspend fun sendToAsiOne(apiKey: String, conversation: List<Message>): Result<String> =
    askAsiOne(apiKey, VOICE_SYSTEM_PROMPT, conversation)

suspend fun askAsiOne(
    apiKey: String,
    systemPrompt: String,
    userMessage: String,
    maxTokens: Int = 300
): Result<String> = askAsiOne(
    apiKey,
    systemPrompt,
    listOf(Message("user", userMessage)),
    maxTokens
)

suspend fun askAsiOne(
    apiKey: String,
    systemPrompt: String,
    conversation: List<Message>,
    maxTokens: Int = 300
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        conversation.forEach { message ->
            messagesArray.put(JSONObject().apply {
                put("role", message.role)
                put("content", message.text)
            })
        }

        val body = JSONObject().apply {
            put("model", "asi1-mini")
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("stream", false)
            put("max_tokens", maxTokens)
        }

        val request = Request.Builder()
            .url("https://api.asi1.ai/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            if (response.code == 429) {
                return@withContext Result.failure(
                    Exception("Rate limited — wait a moment and try again")
                )
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("API error ${response.code}: $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            Result.success(text)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Legacy prose-line decision analysis — superseded by [analyzeDecisionStructured]. */
suspend fun analyzeDecision(
    apiKey: String,
    question: String,
    memoryContext: String = ""
): Result<ProseDecisionAnalysis> {
    val prompt = """
        Analyze this decision for the user. Respond in EXACTLY this format (one field per line):
        PROS: <comma-separated pros>
        CONS: <comma-separated cons>
        RISKS: <comma-separated risks>
        OPPORTUNITIES: <comma-separated opportunities>
        MISSING: <what info is missing>
        RECOMMENDATION: <one clear recommendation>
        CONFIDENCE: <number 0-100>
        
        Decision: $question
    """.trimIndent()

    val systemPrompt = buildString {
        append("You are a decision analysis expert. Be concise and practical.")
        if (memoryContext.isNotEmpty()) {
            append("\n\n")
            append(memoryContext)
        }
    }

    return askAsiOne(
        apiKey,
        systemPrompt,
        prompt,
        maxTokens = 500
    ).map { text -> parseProseDecisionAnalysis(text) }
}

data class ProseDecisionAnalysis(
    val pros: String,
    val cons: String,
    val risks: String,
    val opportunities: String,
    val missingInfo: String,
    val recommendation: String,
    val confidenceScore: Int
)

private fun parseProseDecisionAnalysis(text: String): ProseDecisionAnalysis {
    fun field(label: String): String {
        val regex = Regex("(?i)${label}:\\s*(.+)", RegexOption.MULTILINE)
        return regex.find(text)?.groupValues?.get(1)?.trim()?.take(500) ?: ""
    }
    val confidence = Regex("(?i)CONFIDENCE:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 70
    return ProseDecisionAnalysis(
        pros = field("PROS"),
        cons = field("CONS"),
        risks = field("RISKS"),
        opportunities = field("OPPORTUNITIES"),
        missingInfo = field("MISSING"),
        recommendation = field("RECOMMENDATION").ifBlank { text.take(200) },
        confidenceScore = confidence.coerceIn(0, 100)
    )
}

suspend fun generateDailyInsight(apiKey: String, summary: String): Result<String> =
    askAsiOne(
        apiKey,
        "Generate one concise daily cognitive insight under 20 words based on user activity. No preamble.",
        summary,
        maxTokens = 80
    )
