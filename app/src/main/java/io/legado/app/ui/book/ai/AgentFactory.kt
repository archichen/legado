package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.LLMProvider

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
4. 如果搜索结果为空，如实告知用户没有找到相关内容
5. 不要编造内容，回答必须基于实际搜索到的内容

## 工具使用指南
- 搜索内容时优先使用 searchContent 工具（支持正则表达式）
- 需要查看具体章节时使用 getChapterContent 工具
- 需要了解目录结构时使用 getTableOfContents 工具
- 用户想标记重要内容时使用 createBookmark 工具
- 用户询问阅读进度时使用 getReadingProgress 工具
- 用户想了解内容净化规则时使用 getReplaceRules 工具

请用中文回答用户的问题。"""
    }

    interface Callback {
        fun onThinking(thinking: String)
        fun onToolCall(toolName: String, arguments: String)
        fun onToolResult(toolName: String, result: String)
    }

    fun chat(
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

        for (iteration in 0 until 8) {
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

        val fallback = client.chat(allMessages, null)
        val content = fallback.content ?: "无法生成回复。"
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
