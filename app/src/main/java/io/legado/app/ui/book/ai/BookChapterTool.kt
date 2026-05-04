package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BookChapterTool(private val book: Book) {

    private val contentProcessor by lazy {
        ContentProcessor.get(book.name, book.origin)
    }

    private val bookSource by lazy {
        if (book.isLocal) null else appDb.bookSourceDao.getBookSource(book.origin)
    }

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "getChapterContent",
            "获取指定章节的正文内容。chapterIndex 是0-based索引（第一个章节=0，最后一个章节=章节数-1）。如果章节未下载会自动尝试下载。",
            listOf(
                ToolHelper.PropDef("chapterIndex", "integer", "章节索引（0-based，第一个=0，最后一个=总章节数-1）", true),
                ToolHelper.PropDef("startLine", "integer", "起始行号（从1开始），默认从头", false),
                ToolHelper.PropDef("endLine", "integer", "结束行号（包含），默认到末尾", false)
            )
        ),
        ToolHelper.buildToolDef(
            "getTableOfContents",
            "获取书籍目录。返回章节列表。注意：章节索引是0-based（第一个章节=0）。",
            listOf(
                ToolHelper.PropDef("startIndex", "integer", "起始章节索引（0-based），默认0", false),
                ToolHelper.PropDef("count", "integer", "返回章节数量，默认30", false)
            )
        ),
        ToolHelper.buildToolDef(
            "getBookInfo",
            "获取当前书籍的完整元信息，包括书名、作者、简介、总字数、总章节数、当前阅读进度等。",
            emptyList()
        )
    )

    fun executeTool(name: String, arguments: String): String {
        val args = ToolHelper.parseArgs(arguments)
        return when (name) {
            "getChapterContent" -> getChapterContent(
                chapterIndex = args.int("chapterIndex"),
                startLine = args.int("startLine"),
                endLine = args.int("endLine")
            )
            "getTableOfContents" -> getTableOfContents(
                startIndex = args.int("startIndex"),
                count = args.int("count", 30)
            )
            "getBookInfo" -> getBookInfo()
            else -> "未知工具: $name"
        }
    }

    private fun getChapterContent(chapterIndex: Int, startLine: Int, endLine: Int): String {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) return "该书暂无章节内容。"
        if (chapterIndex < 0 || chapterIndex >= chapters.size) {
            val lastIdx = chapters.size - 1
            return "章节索引 $chapterIndex 超出范围。该书共 ${chapters.size} 章，有效索引为 0 到 $lastIdx（最后一个章节的索引是 $lastIdx，不是 ${chapters.size}）。"
        }

        val chapter = chapters[chapterIndex]

        var content = BookHelp.getContent(book, chapter)

        if (content == null) {
            if (book.isLocal) {
                return "章节「${chapter.title}」是本地书籍但无法读取内容。"
            }

            val source = bookSource
            if (source == null) {
                return "章节「${chapter.title}」内容未缓存，且该书没有配置书源，无法下载。"
            }

            try {
                content = runBlocking {
                    CacheBook.getOrCreate(source, book).downloadAwait(chapter)
                }
            } catch (e: Exception) {
                return "章节「${chapter.title}」下载失败: ${e.message}"
            }

            if (content.isNullOrBlank()) {
                return "章节「${chapter.title}」下载后内容为空。"
            }

            BookHelp.saveText(book, chapter, content)
        }

        val processed = try {
            contentProcessor?.getContent(book, chapter, content, useReplace = true)?.toString() ?: content
        } catch (e: Exception) { content }

        val lines = processed.lines()
        val s = if (startLine > 0) (startLine - 1).coerceIn(0, lines.size - 1) else 0
        val e = if (endLine > 0) endLine.coerceIn(s, lines.size) else lines.size

        val header = "第${chapter.index + 1}章「${chapter.title}」（共 ${lines.size} 行，显示 ${s + 1}-${e}）\n"
        val body = lines.subList(s, e).mapIndexed { i, line ->
            "${s + i + 1}: $line"
        }.joinToString("\n")

        return header + body
    }

    private fun getTableOfContents(startIndex: Int, count: Int): String {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) return "该书暂无目录信息。"
        val s = startIndex.coerceIn(0, chapters.size - 1)
        val e = (s + count).coerceAtMost(chapters.size)
        val header = "共 ${chapters.size} 章（索引0-${chapters.size - 1}），当前显示索引 ${s}-${e - 1}：\n"
        val body = chapters.subList(s, e).joinToString("\n") { ch ->
            "[${ch.index}] ${ch.title}"
        }
        return header + body
    }

    private fun getBookInfo(): String {
        val chapterCount = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        val unread = book.getUnreadChapterNum()
        val progress = if (chapterCount > 0) {
            ((book.durChapterIndex.toFloat() / chapterCount) * 100).toInt()
        } else 0

        return """
书名: ${book.name}
作者: ${book.author}
分类: ${book.kind ?: "未分类"}
总章节数: $chapterCount
总字数: ${book.wordCount ?: "未知"}
最新章节: ${book.latestChapterTitle ?: "未知"}
当前阅读: 第${book.durChapterIndex + 1}章「${book.durChapterTitle ?: "未知"}」
阅读进度: ${book.durChapterIndex}/$chapterCount（${progress}%）
剩余未读: ${unread}章
简介: ${book.intro ?: "暂无简介"}
        """.trimIndent()
    }
}
