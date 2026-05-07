package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.ui.book.ai.ToolHelper.bool
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class BookSearchTool(private val book: Book) {

    private val contentProcessor by lazy {
        ContentProcessor.get(book.name, book.origin)
    }

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "searchContent",
            "搜索书籍正文内容。支持单关键词、多关键词（空格分隔，匹配任意一个）、正则表达式。返回匹配结果包含行号和章节名。",
            listOf(
                ToolHelper.PropDef("keyword", "string", "搜索关键词。多个关键词用空格分隔（如'帝国 联邦'会匹配包含任意一个词的行）。也支持正则表达式。", true),
                ToolHelper.PropDef("isRegex", "boolean", "是否使用正则表达式，默认false", false),
                ToolHelper.PropDef("startChapter", "integer", "搜索起始章节索引（从0开始），默认搜索全部", false),
                ToolHelper.PropDef("endChapter", "integer", "搜索结束章节索引（包含），默认搜索全部", false),
                ToolHelper.PropDef("limit", "integer", "最大返回结果数量，默认10", false)
            )
        )
    )

    fun executeTool(name: String, arguments: String): String {
        val args = ToolHelper.parseArgs(arguments)
        return when (name) {
            "searchContent" -> searchContent(
                keyword = args.str("keyword"),
                isRegex = args.bool("isRegex"),
                startChapter = args.int("startChapter", -1),
                endChapter = args.int("endChapter", -1),
                limit = args.int("limit", 10)
            )
            else -> "未知工具: $name"
        }
    }

    private fun searchContent(
        keyword: String,
        isRegex: Boolean,
        startChapter: Int,
        endChapter: Int,
        limit: Int
    ): String {
        if (keyword.isEmpty()) return "请提供搜索关键词。"

        val allChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (allChapters.isEmpty()) return "该书暂无章节内容。"

        val chapters = when {
            startChapter >= 0 && endChapter >= 0 -> {
                val s = startChapter.coerceIn(0, allChapters.size - 1)
                val e = endChapter.coerceIn(s, allChapters.size - 1)
                allChapters.subList(s, e + 1)
            }
            startChapter >= 0 -> {
                val s = startChapter.coerceIn(0, allChapters.size - 1)
                allChapters.subList(s, allChapters.size)
            }
            else -> allChapters
        }

        val regex = if (isRegex) {
            try { Regex(keyword) } catch (e: Exception) { return "正则表达式格式错误: ${e.message}" }
        } else null

        val results = runBlocking {
            val semaphore = Semaphore(8)
            val resultsList = mutableListOf<Pair<Int, String>>()

            val jobs = chapters.map { chapter ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        searchChapter(chapter, keyword, regex)
                    }
                }
            }

            val allResults = jobs.awaitAll().flatten()
            resultsList.addAll(allResults.sortedBy { it.first }.take(limit))
            resultsList
        }

        if (results.isEmpty()) return "未找到包含「$keyword」的内容。"
        return "找到 ${results.size} 个匹配结果：\n\n" + results.joinToString("\n") { it.second }
    }

    private fun searchChapter(
        chapter: BookChapter,
        keyword: String,
        regex: Regex?
    ): List<Pair<Int, String>> {
        val results = mutableListOf<Pair<Int, String>>()
        val content = BookHelp.getContent(book, chapter) ?: return results
        val processed = try {
            contentProcessor?.getContent(book, chapter, content, useReplace = true)?.toString() ?: content
        } catch (e: Exception) { content }

        val keywords = if (regex == null) keyword.split(" ").filter { it.isNotBlank() } else emptyList()

        val lines = processed.lines()
        for ((lineIdx, line) in lines.withIndex()) {
            val matched = when {
                regex != null -> regex.containsMatchIn(line)
                keywords.size > 1 -> keywords.any { line.contains(it, ignoreCase = true) }
                else -> line.contains(keyword, ignoreCase = true)
            }
            if (matched) {
                val lineNum = lineIdx + 1
                val snippet = line.trim().take(100)
                results.add(
                    chapter.index to "[第${chapter.index + 1}章「${chapter.title}」 第${lineNum}行] $snippet"
                )
            }
        }
        return results
    }
}
