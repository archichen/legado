package io.legado.app.ui.book.vectorize

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorizeTest {

    @Test
    fun testTextChunkerShortText() {
        val text = "这是一段简短的文本。"
        val chunks = TextChunker.chunkChapter(text, 0, "第一章")
        assertTrue("Short text should produce at least one chunk", chunks.isNotEmpty())
        assertEquals(0, chunks[0].chapterIndex)
        assertEquals("第一章", chunks[0].chapterTitle)
        assertEquals(0, chunks[0].chunkIndex)
    }

    @Test
    fun testTextChunkerEmptyText() {
        val chunks = TextChunker.chunkChapter("", 0, "第一章")
        assertEquals(0, chunks.size)
    }

    @Test
    fun testTextChunkerBlankText() {
        val chunks = TextChunker.chunkChapter("   \n  ", 0, "第一章")
        assertEquals(0, chunks.size)
    }

    @Test
    fun testTextChunkerMultipleSentences() {
        val sb = StringBuilder()
        for (i in 1..20) {
            sb.append("这是第${i}个句子，包含一些内容用来测试分块逻辑。")
        }
        val text = sb.toString()
        val chunks = TextChunker.chunkChapter(text, 5, "第十章")
        assertTrue("Long text should produce multiple chunks", chunks.size > 1)
        for (chunk in chunks) {
            assertEquals(5, chunk.chapterIndex)
            assertEquals("第十章", chunk.chapterTitle)
        }
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(1, chunks[1].chunkIndex)
    }

    @Test
    fun testTextChunkerPreservesContent() {
        val text = "第一句话。第二句话。第三句话。"
        val chunks = TextChunker.chunkChapter(text, 0, "测试章")
        val allText = chunks.joinToString("") { it.text }
        assertTrue("Chunks should contain original content", allText.contains("第一句话"))
        assertTrue("Chunks should contain original content", allText.contains("第二句话"))
        assertTrue("Chunks should contain original content", allText.contains("第三句话"))
    }

    @Test
    fun testCosineSimilarityIdentical() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val sim = cosineSimilarity(a, b)
        assertEquals(1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityOrthogonal() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val sim = cosineSimilarity(a, b)
        assertEquals(0.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityOpposite() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val sim = cosineSimilarity(a, b)
        assertEquals(-1.0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityDifferentSize() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val sim = cosineSimilarity(a, b)
        assertEquals(0f, sim, 0.001f)
    }

    @Test
    fun testCosineSimilarityZeroVector() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val sim = cosineSimilarity(a, b)
        assertEquals(0f, sim, 0.001f)
    }

    @Test
    fun testMockEmbeddingClient() {
        val client = MockEmbeddingClient()
        assertTrue("Mock client should be ready", client.isReady())
        assertEquals(384, client.dimension)
    }

    @Test
    fun testMockEmbeddingDeterministic() {
        val client = MockEmbeddingClient()
        val text = "测试文本"
        val vec1 = runBlocking { client.encode(text) }
        val vec2 = runBlocking { client.encode(text) }
        assertEquals(vec1.size, vec2.size)
        for (i in vec1.indices) {
            assertEquals("Vector should be deterministic", vec1[i], vec2[i], 0.0001f)
        }
    }

    @Test
    fun testMockEmbeddingNormalized() {
        val client = MockEmbeddingClient()
        val vec = runBlocking { client.encode("测试归一化") }
        var norm = 0f
        for (v in vec) norm += v * v
        assertEquals(1.0f, kotlin.math.sqrt(norm), 0.01f)
    }

    @Test
    fun testMockEmbeddingDifferentTexts() {
        val client = MockEmbeddingClient()
        val vec1 = runBlocking { client.encode("完全不同的文本A") }
        val vec2 = runBlocking { client.encode("完全不同的文本B") }
        val sim = cosineSimilarity(vec1, vec2)
        assertTrue("Different texts should have similarity < 1", sim < 1.0f)
    }

    @Test
    fun testMockEmbeddingBatch() {
        val client = MockEmbeddingClient()
        val texts = listOf("文本一", "文本二", "文本三")
        val vectors = runBlocking { client.encodeBatch(texts) }
        assertEquals(3, vectors.size)
        for (v in vectors) {
            assertEquals(384, v.size)
        }
    }

    @Test
    fun testFloatByteArrayConversion() {
        val original = floatArrayOf(1.5f, -2.3f, 0.0f, 3.14159f, -0.001f)
        val bytes = BookVectorizer.floatArrayToByteArray(original)
        val restored = BookVectorizer.byteArrayToFloatArray(bytes)
        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals("Float should survive byte conversion", original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun testFloatByteArrayEmpty() {
        val original = floatArrayOf()
        val bytes = BookVectorizer.floatArrayToByteArray(original)
        assertEquals(0, bytes.size)
        val restored = BookVectorizer.byteArrayToFloatArray(bytes)
        assertEquals(0, restored.size)
    }

    @Test
    fun testVectorizeProgress() {
        val progress = VectorizeProgress(
            total = 100,
            completed = 60,
            pending = 30,
            processing = 5,
            failed = 5
        )
        assertEquals(60, progress.percentage)
        assertEquals(false, progress.isComplete)
        assertEquals(true, progress.isRunning)
    }

    @Test
    fun testVectorizeProgressComplete() {
        val progress = VectorizeProgress(100, 100, 0, 0, 0)
        assertEquals(100, progress.percentage)
        assertEquals(true, progress.isComplete)
        assertEquals(false, progress.isRunning)
    }

    @Test
    fun testVectorizeProgressZero() {
        val progress = VectorizeProgress(0, 0, 0, 0, 0)
        assertEquals(0, progress.percentage)
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun testVectorizeProgressNotRunning() {
        val progress = VectorizeProgress(100, 50, 50, 0, 0)
        assertEquals(false, progress.isRunning)
    }
}
