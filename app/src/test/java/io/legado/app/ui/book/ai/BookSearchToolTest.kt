package io.legado.app.ui.book.ai

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun testExtractSnippet() {
        val content = "This is a test content with keyword in the middle of the text"
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assert(snippet.contains("keyword"))
    }

    @Test
    fun testExtractSnippetAtStart() {
        val content = "keyword at the start of content"
        val position = 0
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assert(snippet.contains("keyword"))
    }

    @Test
    fun testExtractSnippetAtEnd() {
        val content = "content ends with keyword"
        val position = content.indexOf("keyword")
        val keywordLength = "keyword".length

        val snippet = extractSnippet(content, position, keywordLength)
        assert(snippet.contains("keyword"))
    }

    @Test
    fun testSearchPosition() {
        val content = "abc keyword def keyword ghi"
        val positions = searchPosition(content, "keyword")
        assertEquals(2, positions.size)
        assertEquals(4, positions[0])
        assertEquals(16, positions[1])
    }

    @Test
    fun testSearchPositionNoMatch() {
        val content = "abc def ghi"
        val positions = searchPosition(content, "keyword")
        assertEquals(0, positions.size)
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
