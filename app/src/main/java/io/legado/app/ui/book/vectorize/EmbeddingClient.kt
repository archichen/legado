package io.legado.app.ui.book.vectorize

import kotlin.math.sqrt

interface EmbeddingClient {

    val dimension: Int

    suspend fun encode(text: String): FloatArray

    suspend fun encodeBatch(texts: List<String>, batchSize: Int = 16): List<FloatArray>

    fun isReady(): Boolean

    suspend fun initialize()
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denominator = sqrt(normA) * sqrt(normB)
    return if (denominator == 0f) 0f else dotProduct / denominator
}
