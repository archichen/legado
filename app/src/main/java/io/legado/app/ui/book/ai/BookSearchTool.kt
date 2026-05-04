package io.legado.app.ui.book.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor

class BookSearchTool(
    private val book: Book
) {
    private val contentProcessor by lazy {
        ContentProcessor.get(book.name, book.origin)
    }

    fun getToolDefs(): List<OpenAIClient.ToolDef> {
        return listOf(
            buildToolDef(
                "searchBookContent",
                "搜索当前书籍的正文内容，返回匹配的文本片段。当用户询问关于书籍内容的问题时使用此工具。",
                listOf(
                    PropDef("keyword", "string", "搜索关键词，用于匹配书籍正文内容", true),
                    PropDef("limit", "integer", "最大返回结果数量，默认为5", false)
                )
            ),
            buildToolDef(
                "getBookInfo",
                "获取当前书籍的基本信息，包括书名、作者、简介、章节数等",
                emptyList()
            ),
            buildToolDef(
                "getTableOfContents",
                "获取指定范围的章节目录",
                listOf(
                    PropDef("startIndex", "integer", "起始章节索引（从0开始），默认为0", false),
                    PropDef("count", "integer", "返回章节数量，默认为20", false)
                )
            )
        )
    }

    fun executeTool(name: String, arguments: String): String {
        val args = try {
            com.google.gson.JsonParser.parseString(arguments).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }
        return when (name) {
            "searchBookContent" -> {
                val keyword = args.get("keyword")?.asString ?: ""
                val limit = args.get("limit")?.asInt ?: 5
                searchBookContent(keyword, limit)
            }
            "getBookInfo" -> getBookInfo()
            "getTableOfContents" -> {
                val startIndex = args.get("startIndex")?.asInt ?: 0
                val count = args.get("count")?.asInt ?: 20
                getTableOfContents(startIndex, count)
            }
            else -> "未知工具: $name"
        }
    }

    private fun searchBookContent(keyword: String, limit: Int): String {
        if (keyword.isEmpty()) return "请提供搜索关键词。"
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val results = mutableListOf<String>()
        var count = 0
        for (chapter in chapters) {
            if (count >= limit) break
            val content = BookHelp.getContent(book, chapter) ?: continue
            val processedContent = try {
                contentProcessor?.getContent(book, chapter, content, useReplace = true)?.toString() ?: content
            } catch (e: Exception) { content }
            val positions = searchPosition(processedContent, keyword)
            if (positions.isNotEmpty()) {
                for (pos in positions.take(3)) {
                    if (count >= limit) break
                    val snippet = extractSnippet(processedContent, pos, keyword.length)
                    results.add("${chapter.title}\n...$snippet...")
                    count++
                }
            }
        }
        if (results.isEmpty()) return "未找到包含「$keyword」的内容。"
        return results.joinToString("\n\n---\n\n")
    }

    private fun getBookInfo(): String {
        val chapterCount = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        return "书名: ${book.name}\n作者: ${book.author}\n简介: ${book.intro ?: "暂无简介"}\n章节数: $chapterCount"
    }

    private fun getTableOfContents(startIndex: Int, count: Int): String {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) return "该书暂无目录信息。"
        val end = minOf(startIndex + count, chapters.size)
        if (startIndex >= chapters.size) return "起始索引超出范围，该书共有${chapters.size}章。"
        return chapters.subList(startIndex, end).joinToString("\n") { "第${it.index + 1}章: ${it.title}" }
    }

    private fun searchPosition(content: String, pattern: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = content.indexOf(pattern)
        while (index >= 0) { positions.add(index); index = content.indexOf(pattern, index + pattern.length) }
        return positions
    }

    private fun extractSnippet(content: String, position: Int, keywordLength: Int): String {
        val ctx = 50; var s = position - ctx; var e = position + keywordLength + ctx
        if (s < 0) s = 0; if (e > content.length) e = content.length
        return content.substring(s, e)
    }

    private fun buildToolDef(name: String, desc: String, props: List<PropDef>): OpenAIClient.ToolDef {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val propsObj = JsonObject()
            for (p in props) {
                val propObj = JsonObject().apply {
                    addProperty("type", p.type)
                    addProperty("description", p.desc)
                }
                propsObj.add(p.name, propObj)
            }
            add("properties", propsObj)
            val required = JsonArray()
            props.filter { it.required }.forEach { required.add(it.name) }
            add("required", required)
        }
        return OpenAIClient.ToolDef(name, desc, params)
    }

    private data class PropDef(val name: String, val type: String, val desc: String, val required: Boolean)
}
