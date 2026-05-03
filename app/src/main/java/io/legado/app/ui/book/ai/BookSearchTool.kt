package io.legado.app.ui.book.ai

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor

class BookSearchTool(
    private val book: Book
) {
    private val contentProcessor by lazy {
        ContentProcessor.get(book.name, book.origin)
    }

    @Tool("搜索当前书籍的章节标题，返回匹配的章节列表")
    fun searchChapters(
        @P("搜索关键词，用于匹配章节标题")
        keyword: String
    ): String {
        val chapters = appDb.bookChapterDao.search(book.bookUrl, keyword)
        if (chapters.isEmpty()) {
            return "未找到包含「$keyword」的章节。"
        }
        return chapters.take(10).joinToString("\n") { chapter ->
            "第${chapter.index + 1}章: ${chapter.title}"
        }
    }

    @Tool("搜索当前书籍的正文内容，返回匹配的文本片段")
    fun searchBookContent(
        @P("搜索关键词，用于匹配书籍正文内容")
        keyword: String,
        @P(value = "最大返回结果数量，默认为5", required = false)
        limit: Int = 5
    ): String {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val results = mutableListOf<String>()
        var count = 0

        for (chapter in chapters) {
            if (count >= limit) break
            val content = BookHelp.getContent(book, chapter) ?: continue
            val processedContent = contentProcessor?.getContent(
                book, chapter, content, useReplace = true
            )?.toString() ?: content

            val positions = searchPosition(processedContent, keyword)
            if (positions.isNotEmpty()) {
                for (pos in positions.take(3)) {
                    if (count >= limit) break
                    val snippet = extractSnippet(processedContent, pos, keyword.length)
                    results.add("📖 ${chapter.title}\n...$snippet...")
                    count++
                }
            }
        }

        if (results.isEmpty()) {
            return "未找到包含「$keyword」的内容。"
        }
        return results.joinToString("\n\n---\n\n")
    }

    @Tool("获取当前书籍的基本信息，包括书名、作者、简介、章节数等")
    fun getBookInfo(): String {
        val chapterCount = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        return """
            📚 书名: ${book.name}
            ✍️ 作者: ${book.author}
            📖 简介: ${book.intro ?: "暂无简介"}
            📑 章节数: $chapterCount
            📂 分类: ${book.kind ?: "未分类"}
        """.trimIndent()
    }

    @Tool("获取指定范围的章节目录")
    fun getTableOfContents(
        @P(value = "起始章节索引（从0开始），默认为0", required = false)
        startIndex: Int = 0,
        @P(value = "返回章节数量，默认为20", required = false)
        count: Int = 20
    ): String {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) {
            return "该书暂无目录信息。"
        }
        val end = minOf(startIndex + count, chapters.size)
        if (startIndex >= chapters.size) {
            return "起始索引超出范围，该书共有${chapters.size}章。"
        }
        return chapters.subList(startIndex, end).joinToString("\n") { chapter ->
            "第${chapter.index + 1}章: ${chapter.title}"
        }
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
