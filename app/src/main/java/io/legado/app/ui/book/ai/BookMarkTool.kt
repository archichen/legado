package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str

class BookMarkTool(private val book: Book) {

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "createBookmark",
            "为当前书籍创建书签。可以标记重要段落并添加笔记。",
            listOf(
                ToolHelper.PropDef("chapterIndex", "integer", "章节索引（从0开始）", true),
                ToolHelper.PropDef("bookText", "string", "书签处的文本内容", true),
                ToolHelper.PropDef("note", "string", "书签笔记/注释", false)
            )
        ),
        ToolHelper.buildToolDef(
            "getBookmarks",
            "获取当前书籍的书签列表。可按关键词搜索书签内容。",
            listOf(
                ToolHelper.PropDef("keyword", "string", "搜索关键词，搜索书签的章节名和内容", false)
            )
        )
    )

    fun executeTool(name: String, arguments: String): String {
        val args = ToolHelper.parseArgs(arguments)
        return when (name) {
            "createBookmark" -> createBookmark(
                chapterIndex = args.int("chapterIndex"),
                bookText = args.str("bookText"),
                note = args.str("note")
            )
            "getBookmarks" -> getBookmarks(
                keyword = args.str("keyword")
            )
            else -> "未知工具: $name"
        }
    }

    private fun createBookmark(chapterIndex: Int, bookText: String, note: String): String {
        if (bookText.isEmpty()) return "请提供书签处的文本内容。"

        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapterIndex < 0 || chapterIndex >= chapters.size) {
            return "章节索引超出范围。"
        }
        val chapter = chapters[chapterIndex]

        val bookmark = Bookmark(
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = chapterIndex,
            chapterPos = 0,
            chapterName = chapter.title,
            bookText = bookText.take(200),
            content = note
        )

        return try {
            appDb.bookmarkDao.insert(bookmark)
            "书签已创建：第${chapterIndex + 1}章「${chapter.title}」\n文本: ${bookText.take(100)}\n笔记: ${note.ifEmpty { "无" }}"
        } catch (e: Exception) {
            "创建书签失败: ${e.message}"
        }
    }

    private fun getBookmarks(keyword: String): String {
        val bookmarks = if (keyword.isNotEmpty()) {
            appDb.bookmarkDao.search(book.name, book.author, keyword)
        } else {
            appDb.bookmarkDao.getByBook(book.name, book.author)
        }

        if (bookmarks.isEmpty()) {
            return if (keyword.isNotEmpty()) "未找到包含「$keyword」的书签。" else "该书暂无书签。"
        }

        val header = "共 ${bookmarks.size} 个书签：\n"
        val body = bookmarks.joinToString("\n---\n") { bm ->
            """
第${bm.chapterIndex + 1}章「${bm.chapterName}」
文本: ${bm.bookText.take(100)}
笔记: ${bm.content.ifEmpty { "无" }}
            """.trimIndent()
        }
        return header + body
    }
}
