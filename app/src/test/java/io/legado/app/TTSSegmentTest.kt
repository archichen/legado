package io.legado.app

import io.legado.app.data.entities.TTSSegment
import org.junit.Assert.*
import org.junit.Test

class TTSSegmentTest {

    @Test
    fun aggregateEmptyList() {
        val result = TTSSegment.aggregate(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun aggregateSingleShortParagraph() {
        val result = TTSSegment.aggregate(listOf("短段落"), maxLength = 100)
        assertEquals(1, result.size)
        assertEquals("短段落", result[0].text)
        assertEquals(0, result[0].startIndex)
        assertEquals(0, result[0].endIndex)
    }

    @Test
    fun aggregateMergesShortParagraphs() {
        val paragraphs = listOf("你好", "世界", "测试")
        val result = TTSSegment.aggregate(paragraphs, maxLength = 100)
        assertEquals(1, result.size)
        assertEquals(0, result[0].startIndex)
        assertEquals(2, result[0].endIndex)
        assertTrue(result[0].text.contains("你好"))
        assertTrue(result[0].text.contains("世界"))
        assertTrue(result[0].text.contains("测试"))
    }

    @Test
    fun aggregateSplitsAtMaxLength() {
        val paragraphs = listOf(
            "a".repeat(60),
            "b".repeat(60),
            "c".repeat(10)
        )
        val result = TTSSegment.aggregate(paragraphs, maxLength = 100)
        assertTrue(result.size >= 2)
        assertEquals(0, result[0].startIndex)
        assertEquals(result.last().endIndex, paragraphs.lastIndex)
    }

    @Test
    fun aggregateEachSegmentWithinMaxLength() {
        val paragraphs = (1..20).map { "段落$it" + "x".repeat(30) }
        val maxLength = 80
        val result = TTSSegment.aggregate(paragraphs, maxLength = maxLength)
        for (segment in result) {
            assertTrue(
                "Segment text length ${segment.text.length} exceeds maxLength $maxLength",
                segment.text.length <= maxLength || segment.paragraphCount == 1
            )
        }
    }

    @Test
    fun aggregateCoversAllParagraphs() {
        val paragraphs = listOf("aaa", "bbb", "ccc", "ddd", "eee")
        val result = TTSSegment.aggregate(paragraphs, maxLength = 8)
        assertEquals(0, result.first().startIndex)
        assertEquals(paragraphs.lastIndex, result.last().endIndex)
        for (i in 0 until result.size - 1) {
            assertEquals(result[i].endIndex + 1, result[i + 1].startIndex)
        }
    }

    @Test
    fun containsParagraph() {
        val segment = TTSSegment(
            startIndex = 2, endIndex = 5,
            text = "test", paragraphLengths = listOf(4, 4, 4, 4)
        )
        assertFalse(segment.containsParagraph(1))
        assertTrue(segment.containsParagraph(2))
        assertTrue(segment.containsParagraph(4))
        assertTrue(segment.containsParagraph(5))
        assertFalse(segment.containsParagraph(6))
    }

    @Test
    fun getParagraphOffset() {
        val segment = TTSSegment(
            startIndex = 0, endIndex = 2,
            text = "aaa\nbbb\nccc",
            paragraphLengths = listOf(4, 4, 4)
        )
        assertEquals(0, segment.getParagraphOffset(0))
        assertEquals(4, segment.getParagraphOffset(1))
        assertEquals(8, segment.getParagraphOffset(2))
        assertEquals(-1, segment.getParagraphOffset(3))
    }

    @Test
    fun getParagraphLength() {
        val segment = TTSSegment(
            startIndex = 1, endIndex = 3,
            text = "test", paragraphLengths = listOf(10, 20, 30)
        )
        assertEquals(0, segment.getParagraphLength(0))
        assertEquals(10, segment.getParagraphLength(1))
        assertEquals(20, segment.getParagraphLength(2))
        assertEquals(30, segment.getParagraphLength(3))
        assertEquals(0, segment.getParagraphLength(4))
    }

    @Test
    fun findSegment() {
        val segments = TTSSegment.aggregate(
            listOf("aaa", "bbb", "ccc", "ddd", "eee"),
            maxLength = 8
        )
        for (segment in segments) {
            for (i in segment.startIndex..segment.endIndex) {
                val found = TTSSegment.findSegment(segments, i)
                assertTrue(found >= 0)
                assertTrue(segments[found].containsParagraph(i))
            }
        }
        assertEquals(-1, TTSSegment.findSegment(segments, 99))
    }

    @Test
    fun paragraphCount() {
        val segment = TTSSegment(
            startIndex = 3, endIndex = 7,
            text = "test", paragraphLengths = listOf(1, 2, 3, 4, 5)
        )
        assertEquals(5, segment.paragraphCount)
    }
}
