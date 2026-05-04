package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book

class BookProgressTool(private val book: Book) {

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "getReadingProgress",
            "获取当前书籍的详细阅读进度信息，包括当前章节、已读/未读章节数、阅读时间等。",
            emptyList()
        )
    )

    fun executeTool(name: String, arguments: String): String {
        return when (name) {
            "getReadingProgress" -> getReadingProgress()
            else -> "未知工具: $name"
        }
    }

    private fun getReadingProgress(): String {
        val chapterCount = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        val unread = book.getUnreadChapterNum()
        val progress = if (chapterCount > 0) {
            ((book.durChapterIndex.toFloat() / chapterCount) * 100).toInt()
        } else 0

        val lastReadTime = if (book.durChapterTime > 0) {
            val diff = System.currentTimeMillis() - book.durChapterTime
            when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000}分钟前"
                diff < 86400_000 -> "${diff / 3600_000}小时前"
                else -> "${diff / 86400_000}天前"
            }
        } else "未知"

        return """
阅读进度:
- 当前章节: 第${book.durChapterIndex + 1}章「${book.durChapterTitle ?: "未知"}」
- 总章节数: $chapterCount
- 已读章节: ${book.durChapterIndex}章
- 未读章节: ${unread}章
- 阅读进度: ${progress}%
- 最后阅读: $lastReadTime
- 最新章节: ${book.latestChapterTitle ?: "未知"}
        """.trimIndent()
    }
}
