package com.coinv.app.data.repository

import kotlin.math.sqrt

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return if (normA == 0f || normB == 0f) 0f else dot / (sqrt(normA) * sqrt(normB))
}
