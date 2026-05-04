package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.LLMProvider
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

object AgentFactory {

    private fun buildSystemPrompt(book: Book): String {
        val chapterCount = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        val unread = book.getUnreadChapterNum()
        val progress = if (chapterCount > 0) {
            ((book.durChapterIndex.toFloat() / chapterCount) * 100).toInt()
        } else 0

        return """你是一个专业的书籍助手，运行在 Legado 阅读器中。

## 当前书籍信息
- 书名: 《${book.name}》
- 作者: ${book.author}
- 分类: ${book.kind ?: "未分类"}
- 总章节数: $chapterCount
- 总字数: ${book.wordCount ?: "未知"}
- 最新章节: ${book.latestChapterTitle ?: "未知"}
- 当前阅读: 第${book.durChapterIndex + 1}章「${book.durChapterTitle ?: "未知"}」
- 阅读进度: ${book.durChapterIndex}/$chapterCount（${progress}%）
- 剩余未读: ${unread}章
- 简介: ${book.intro ?: "暂无简介"}

## 你的职责
1. 帮助用户理解书籍内容，回答关于书籍的问题
2. 使用工具搜索、检索书籍内容，基于实际内容回答
3. 可以帮助用户创建书签、查看阅读进度、管理替换规则
4. 不要编造内容，回答必须基于实际搜索到的内容

## 工具使用策略（重要！）
你有多种工具可用，请根据问题类型选择合适的工具，不要反复使用同一个工具搜索相同内容：

**内容搜索类：**
- searchContent: 搜索正文关键词/正则。如果搜不到，尝试换关键词、用正则、或扩大章节范围
- getChapterContent: 直接读取指定章节全文或部分内容。当你知道大概在哪个章节时，直接读章节比搜索更高效
- getTableOfContents: 查看目录结构，帮助定位章节

**信息查询类：**
- getBookInfo: 获取书籍元信息（书名、作者、简介、进度等）
- getReadingProgress: 获取详细阅读进度
- getBookmarks: 查看书签列表
- getReplaceRules: 查看内容净化规则

**操作类：**
- createBookmark: 创建书签

## 关键原则
1. **不要反复搜索相同内容**：如果 searchContent 没找到，换关键词或用 getChapterContent 直接读章节
2. **合理组合工具**：先 getTableOfContents 了解结构，再 getChapterContent 读具体章节
3. **搜索无果时如实告知**：如果多种方式都找不到，直接告诉用户"未找到相关内容"，并说明你尝试了哪些方法
4. **基于已有信息回答**：如果找不到确切答案，可以根据书籍简介、章节标题等已有信息给出推测，但要明确标注"根据已有信息推测"

## 输出格式
请使用 Markdown 格式回答，善用：
- **粗体** 强调关键信息
- `代码` 标记书名、章节名、关键词
- > 引用 原文片段
- 有序/无序列表 梳理信息
- 分隔线 --- 区分不同部分

请用中文回答用户的问题。"""
    }

    interface Callback {
        fun onThinking(thinking: String)
        fun onToolCall(toolName: String, arguments: String)
        fun onToolResult(toolName: String, result: String)
    }

    suspend fun chat(
        provider: LLMProvider,
        book: Book,
        history: List<OpenAIClient.ChatMsg>,
        userMessage: String,
        callback: Callback? = null
    ): Pair<String, List<OpenAIClient.ChatMsg>> {
        val client = OpenAIClient(provider.baseUrl, provider.apiKey, provider.modelName)

        val searchTool = BookSearchTool(book)
        val chapterTool = BookChapterTool(book)
        val markTool = BookMarkTool(book)
        val progressTool = BookProgressTool(book)
        val replaceTool = BookReplaceTool(book)

        val allToolDefs = searchTool.getToolDefs() +
            chapterTool.getToolDefs() +
            markTool.getToolDefs() +
            progressTool.getToolDefs() +
            replaceTool.getToolDefs()

        val messages = mutableListOf<OpenAIClient.ChatMsg>()
        messages.add(OpenAIClient.ChatMsg("system", buildSystemPrompt(book)))
        messages.addAll(history)
        messages.add(OpenAIClient.ChatMsg("user", userMessage))

        val allMessages = messages.toMutableList()

        for (iteration in 0 until 20) {
            coroutineContext.ensureActive()

            val response = client.chat(allMessages, allToolDefs)

            if (response.reasoningContent != null) {
                callback?.onThinking(response.reasoningContent)
            }

            if (response.toolCalls.isNullOrEmpty()) {
                val content = response.content ?: "无法生成回复。"
                allMessages.add(OpenAIClient.ChatMsg("assistant", content))
                return content to allMessages.drop(1)
            }

            allMessages.add(OpenAIClient.ChatMsg(
                role = "assistant",
                content = response.content,
                toolCalls = response.toolCalls
            ))

            for (tc in response.toolCalls) {
                coroutineContext.ensureActive()
                callback?.onToolCall(tc.name, tc.arguments)
                val result = try {
                    executeTool(tc.name, tc.arguments, searchTool, chapterTool, markTool, progressTool, replaceTool)
                } catch (e: Exception) {
                    "工具执行错误: ${e.message}"
                }
                callback?.onToolResult(tc.name, result)
                allMessages.add(OpenAIClient.ChatMsg(
                    role = "tool",
                    content = result,
                    toolCallId = tc.id
                ))
            }
        }

        allMessages.add(OpenAIClient.ChatMsg(
            role = "user",
            content = "你已经进行了多轮工具调用但仍未得出结论。请根据你目前已获取到的所有信息，直接给出你的回答。如果确实找不到相关内容，请如实告知用户，并说明你尝试了哪些搜索方式。不要再调用工具，直接回答。"
        ))
        val fallback = client.chat(allMessages, null)
        val content = fallback.content ?: "抱歉，经过多次尝试未能找到确切答案。请尝试换个关键词或更具体地描述您的问题。"
        allMessages.add(OpenAIClient.ChatMsg("assistant", content))
        return content to allMessages.drop(1)
    }

    private fun executeTool(
        name: String,
        arguments: String,
        searchTool: BookSearchTool,
        chapterTool: BookChapterTool,
        markTool: BookMarkTool,
        progressTool: BookProgressTool,
        replaceTool: BookReplaceTool
    ): String {
        return when (name) {
            in searchTool.getToolDefs().map { it.name } -> searchTool.executeTool(name, arguments)
            in chapterTool.getToolDefs().map { it.name } -> chapterTool.executeTool(name, arguments)
            in markTool.getToolDefs().map { it.name } -> markTool.executeTool(name, arguments)
            in progressTool.getToolDefs().map { it.name } -> progressTool.executeTool(name, arguments)
            in replaceTool.getToolDefs().map { it.name } -> replaceTool.executeTool(name, arguments)
            else -> "未知工具: $name"
        }
    }
}
