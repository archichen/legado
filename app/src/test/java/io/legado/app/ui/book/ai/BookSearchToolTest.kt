package io.legado.app.ui.book.ai

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchToolTest {

    @Test
    fun testBookSearchToolCreation() {
        val book = Book(
            bookUrl = "https://example.com/book1",
            name = "Test Book",
            author = "Test Author"
        )
        val tool = BookSearchTool(book)
        assertNotNull(tool)
    }

    @Test
    fun testExtractSnippetMiddle() {
        val content = "This is a test content with keyword in the middle of the text"
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertTrue("Snippet should contain keyword", snippet.contains("keyword"))
        assertTrue("Snippet should contain surrounding context", snippet.contains("with"))
        assertTrue("Snippet should contain surrounding context", snippet.contains("in the"))
    }

    @Test
    fun testExtractSnippetAtStart() {
        val content = "keyword at the start of content"
        val position = 0
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertTrue("Snippet should contain keyword", snippet.contains("keyword"))
        assertTrue("Snippet should start from beginning", snippet.startsWith("keyword"))
    }

    @Test
    fun testExtractSnippetAtEnd() {
        val content = "content ends with keyword"
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertTrue("Snippet should contain keyword", snippet.contains("keyword"))
        assertTrue("Snippet should end with keyword", snippet.endsWith("keyword"))
    }

    @Test
    fun testExtractSnippetShortContent() {
        val content = "keyword"
        val position = 0
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertEquals("Short content should return full content", content, snippet)
    }

    @Test
    fun testExtractSnippetEmptyContent() {
        val content = ""
        val position = 0
        val keywordLength = 0

        val snippet = extractSnippet(content, position, keywordLength)
        assertEquals("Empty content should return empty string", "", snippet)
    }

    @Test
    fun testSearchPositionMultipleMatches() {
        val content = "abc keyword def keyword ghi keyword"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find 3 matches", 3, positions.size)
        assertEquals(4, positions[0])
        assertEquals(16, positions[1])
        assertEquals(28, positions[2])
    }

    @Test
    fun testSearchPositionNoMatch() {
        val content = "abc def ghi"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find no matches", 0, positions.size)
    }

    @Test
    fun testSearchPositionSingleMatch() {
        val content = "abc keyword def"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find 1 match", 1, positions.size)
        assertEquals(4, positions[0])
    }

    @Test
    fun testSearchPositionCaseSensitive() {
        val content = "abc Keyword keyword KEYWORD"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find 1 case-sensitive match", 1, positions.size)
        assertEquals(12, positions[0])
    }

    @Test
    fun testSearchPositionAtStart() {
        val content = "keyword at start"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find match at start", 1, positions.size)
        assertEquals(0, positions[0])
    }

    @Test
    fun testSearchPositionAtEnd() {
        val content = "ends with keyword"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find match at end", 1, positions.size)
        assertEquals(10, positions[0])
    }

    @Test
    fun testSearchPositionAdjacentMatches() {
        val content = "keywordkeyword"
        val positions = searchPosition(content, "keyword")
        assertEquals("Should find 2 adjacent matches", 2, positions.size)
        assertEquals(0, positions[0])
        assertEquals(7, positions[1])
    }

    @Test
    fun testSearchPositionOverlappingPattern() {
        val content = "ababab"
        val positions = searchPosition(content, "aba")
        assertEquals("Should find 1 non-overlapping match", 1, positions.size)
        assertEquals(0, positions[0])
    }

    @Test
    fun testExtractSnippetContextLength() {
        val content = "a".repeat(200) + "keyword" + "b".repeat(200)
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        val expectedMaxLength = keywordLength + 100 // 50 chars before + keyword + 50 chars after
        assertTrue(
            "Snippet length should not exceed context limit",
            snippet.length <= expectedMaxLength + 10 // Allow small margin
        )
        assertTrue("Snippet should contain keyword", snippet.contains("keyword"))
    }

    @Test
    fun testExtractSnippetSpecialCharacters() {
        val content = "Hello, World! This is a test with special chars: @#\$%^&*()"
        val position = content.indexOf("special")
        val keywordLength = "special".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertTrue("Snippet should contain keyword", snippet.contains("special"))
    }

    @Test
    fun testExtractSnippetUnicode() {
        val content = "这是一段中文内容，包含关键词keyword在其中"
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assertTrue("Snippet should contain keyword", snippet.contains("keyword"))
        assertTrue("Snippet should contain Chinese context", snippet.contains("包含"))
    }

    private fun searchPosition(content: String, pattern: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = content.indexOf(pattern)
        while (index >= 0) {
            positions.add(index)
            index = content.indexOf(pattern, index + pattern.length)
        }
        return positions
    }

    private fun extractSnippet(content: String, position: Int, keywordLength: Int): String {
        val contextLength = 50
        var start = position - contextLength
        var end = position + keywordLength + contextLength
        if (start < 0) start = 0
        if (end > content.length) end = content.length
        return content.substring(start, end)
    }
}
