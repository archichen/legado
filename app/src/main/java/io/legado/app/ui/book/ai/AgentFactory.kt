package io.legado.app.ui.book.ai

import io.legado.app.constant.AppLog
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
帮助用户理解书籍内容，回答关于书籍的问题。不要编造内容，回答必须基于实际搜索到的内容。

## 工具选择指南（核心！）

### 何时用 searchSemantic（语义搜索）
适合**模糊、概念性、主题性**问题，用户不知道确切关键词时：
- "主角是怎么成长的？"
- "这本书讲了什么道理？"
- "关于爱情的描写"
- "哪个情节最感人？"
- "反派的动机是什么？"

### 何时用 searchContent（关键词搜索）
适合**精确查找**，用户知道具体词语时：
- 人名、地名、术语
- "叶文洁"、"三体"、"面壁者"
- 正则表达式匹配
- 确定的对话或引用

### 何时用 getChapterContent（读章节）
适合**需要完整上下文**时：
- 已知在哪个章节（通过搜索定位后）
- 需要看完整段落
- 用户问"第X章讲了什么"

### 最佳组合策略（推荐！）
对于大多数问题，**先语义搜索定位，再关键词精搜，最后读章节**：

```
用户: "罗辑是怎么威慑三体人的？"

Step 1: searchSemantic("罗辑威慑三体人") 
→ 发现第42章有相关内容

Step 2: searchContent("威慑", startChapter=42, endChapter=42)
→ 找到具体行号

Step 3: getChapterContent(42, startLine=80, endLine=120)
→ 读取完整上下文

Step 4: 基于完整内容回答
```

### 示例对照表

| 用户问题 | 首选工具 | 原因 |
|---------|---------|------|
| "叶文洁是谁？" | searchContent("叶文洁") | 精确人名 |
| "主角经历了什么？" | searchSemantic("主角经历成长") | 模糊概念 |
| "第5章讲什么？" | getChapterContent(4) | 已知章节 |
| "有哪些关于宇宙的描写？" | searchSemantic("宇宙星空描写") | 主题性 |
| "面壁计划是什么？" | searchContent("面壁计划") | 精确术语 |
| "这本书的主题是什么？" | searchSemantic + getBookInfo | 需要综合 |

## 输出格式
用中文回答，使用 Markdown：
- **粗体** 强调关键信息
- `代码` 标记书名、章节名
- > 引用 原文片段
- 列表 梳理信息"""
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
        AppLog.put("Agent: 开始对话《${book.name}》provider=${provider.name} model=${provider.modelName}")
        AppLog.put("Agent: 用户问题: ${userMessage.take(100)}")

        val client = OpenAIClient(provider.baseUrl, provider.apiKey, provider.modelName)

        val searchTool = BookSearchTool(book)
        val chapterTool = BookChapterTool(book)
        val semanticTool = SemanticSearchTool(book)
        val markTool = BookMarkTool(book)
        val progressTool = BookProgressTool(book)
        val replaceTool = BookReplaceTool(book)

        val allToolDefs = searchTool.getToolDefs() +
            chapterTool.getToolDefs() +
            semanticTool.getToolDefs() +
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

            AppLog.put("Agent: 第${iteration+1}轮迭代, messages=${allMessages.size}")
            val response = client.chat(allMessages, allToolDefs)

            if (response.reasoningContent != null) {
                AppLog.put("Agent: 思考中... ${response.reasoningContent.take(100)}")
                callback?.onThinking(response.reasoningContent)
            }

            if (response.toolCalls.isNullOrEmpty()) {
                val content = response.content ?: "无法生成回复。"
                allMessages.add(OpenAIClient.ChatMsg("assistant", content))
                AppLog.put("Agent: 最终回复(${iteration+1}轮): ${content.take(100)}")
                return content to allMessages.drop(1)
            }

            AppLog.put("Agent: 模型请求调用 ${response.toolCalls.size} 个工具")

            allMessages.add(OpenAIClient.ChatMsg(
                role = "assistant",
                content = response.content,
                toolCalls = response.toolCalls
            ))

            for (tc in response.toolCalls) {
                coroutineContext.ensureActive()
                AppLog.put("Agent: 执行工具 ${tc.name}(${tc.arguments.take(80)})")
                callback?.onToolCall(tc.name, tc.arguments)
                val result = try {
                    executeTool(tc.name, tc.arguments, searchTool, chapterTool, semanticTool, markTool, progressTool, replaceTool)
                } catch (e: Exception) {
                    AppLog.put("Agent: 工具 ${tc.name} 异常: ${e.message}", e)
                    "工具执行错误: ${e.message}"
                }
                AppLog.put("Agent: 工具 ${tc.name} 结果: ${result.take(100)}")
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
        semanticTool: SemanticSearchTool,
        markTool: BookMarkTool,
        progressTool: BookProgressTool,
        replaceTool: BookReplaceTool
    ): String {
        return when (name) {
            in searchTool.getToolDefs().map { it.name } -> searchTool.executeTool(name, arguments)
            in chapterTool.getToolDefs().map { it.name } -> chapterTool.executeTool(name, arguments)
            in semanticTool.getToolDefs().map { it.name } -> semanticTool.executeTool(name, arguments)
            in markTool.getToolDefs().map { it.name } -> markTool.executeTool(name, arguments)
            in progressTool.getToolDefs().map { it.name } -> progressTool.executeTool(name, arguments)
            in replaceTool.getToolDefs().map { it.name } -> replaceTool.executeTool(name, arguments)
            else -> "未知工具: $name"
        }
    }
}
