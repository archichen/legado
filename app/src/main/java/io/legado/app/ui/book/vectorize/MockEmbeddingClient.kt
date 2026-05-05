package io.legado.app.ui.book.vectorize

import kotlin.math.abs
import kotlin.math.sin

class MockEmbeddingClient : EmbeddingClient {

    override val dimension: Int = 384

    override suspend fun encode(text: String): FloatArray {
        return textToVector(text)
    }

    override suspend fun encodeBatch(texts: List<String>, batchSize: Int): List<FloatArray> {
        return texts.map { textToVector(it) }
    }

    override fun isReady(): Boolean = true

    override suspend fun initialize() {}

    private fun textToVector(text: String): FloatArray {
        val vector = FloatArray(dimension)
        val chars = text.toCharArray()
        for (i in 0 until dimension) {
            var sum = 0f
            for ((j, c) in chars.withIndex()) {
                sum += sin((c.code * (i + 1) + j * 31).toFloat()) * 0.1f
            }
            vector[i] = sum
        }
        return normalize(vector)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm == 0f) return vector
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    private fun sqrt(x: Float): Float {
        if (x <= 0f) return 0f
        var guess = x / 2f
        repeat(10) { guess = (guess + x / guess) / 2f }
        return guess
    }
}
