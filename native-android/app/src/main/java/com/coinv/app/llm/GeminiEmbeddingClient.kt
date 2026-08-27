package com.coinv.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private val embeddingHttpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

private const val EMBED_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent"

suspend fun embedText(apiKey: String, text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply {
            put("model", "models/gemini-embedding-001")
            put("content", JSONObject().apply {
                put("parts", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("text", text) })
                })
            })
        }

        val request = Request.Builder()
            .url(EMBED_URL)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        embeddingHttpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    when (response.code) {
                        404 -> Exception(
                            "Embedding model not found — check https://ai.google.dev/gemini-api/docs/embeddings " +
                                "for the current model name, Google has renamed this before"
                        )
                        429 -> Exception("Embedding rate limit hit — free tier is 100/min, 1000/day")
                        else -> Exception("Embedding API error ${response.code}: $rawBody")
                    }
                )
            }
            val json = JSONObject(rawBody)
            if (!json.has("embedding")) {
                return@withContext Result.failure(
                    Exception("Unexpected embedding response shape: $rawBody")
                )
            }
            val values = json.getJSONObject("embedding").getJSONArray("values")
            val array = FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
            Result.success(array)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
