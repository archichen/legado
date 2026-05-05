package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.vectorize.BookVectorizer
import io.legado.app.ui.book.vectorize.EmbeddingClient
import io.legado.app.ui.book.vectorize.EmbeddingManager
import io.legado.app.ui.book.vectorize.cosineSimilarity
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

class SemanticSearchTool(private val book: Book) {

    private val embeddingClient: EmbeddingClient
        get() = EmbeddingManager.getClientOrNull() ?: runBlocking {
            EmbeddingManager.getClient(appCtx)
        }

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "searchSemantic",
            "语义搜索：通过含义（而非精确关键词）搜索书籍内容。返回最相关的文本片段及其所在章节。" +
                "注意：返回的是片段（chunk），可能缺少上下文。建议用返回结果中的关键词再调用 searchContent 或 getChapterContent 获取完整上下文。",
            listOf(
                ToolHelper.PropDef("query", "string", "搜索查询，描述你要找的内容含义", true),
                ToolHelper.PropDef("limit", "integer", "返回结果数量，默认5", false)
            )
        )
    )

    fun executeTool(name: String, arguments: String): String {
        val args = ToolHelper.parseArgs(arguments)
        return when (name) {
            "searchSemantic" -> searchSemantic(
                query = args.str("query"),
                limit = args.int("limit", 5)
            )
            else -> "未知工具: $name"
        }
    }

    private fun searchSemantic(query: String, limit: Int): String {
        if (query.isEmpty()) return "请提供搜索查询。"

        val allEmbeddings = appDb.bookEmbeddingDao.getByBook(book.bookUrl)
        if (allEmbeddings.isEmpty()) {
            return "该书尚未完成向量化，无法进行语义搜索。请先在书籍详情页（点击AI图标）启动向量化。"
        }

        val queryVector = runCatching { runBlocking { embeddingClient.encode(query) } }.getOrElse {
            return "查询编码失败: ${it.message}"
        }

        val results = allEmbeddings
            .map { emb ->
                val embVector = BookVectorizer.byteArrayToFloatArray(emb.vector)
                emb to cosineSimilarity(queryVector, embVector)
            }
            .sortedByDescending { it.second }
            .take(limit)

        if (results.isEmpty()) return "未找到与「$query」相关的内容。"

        val header = "语义搜索「$query」找到 ${results.size} 个相关片段：\n\n"
        val body = results.mapIndexed { i, (emb, score) ->
            "${i + 1}. [相似度:${"%.2f".format(score)}] 第${emb.chapterIndex + 1}章「${emb.chapterTitle}」\n" +
                "   ${emb.text.take(200)}"
        }.joinToString("\n\n")

        val footer = "\n\n提示：这些是文本片段，可能缺少前后文。建议：" +
            "\n- 用返回的章节索引调用 getChapterContent 获取完整章节" +
            "\n- 用片段中的关键词调用 searchContent 在相关章节范围内搜索"

        return header + body + footer
    }
}
