package io.legado.app.ui.book.ai

import com.google.gson.JsonParser
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.LLMProvider

object AgentFactory {

    private const val SYSTEM_PROMPT = """你是一个专业的书籍助手，运行在 Legado 阅读器中。
你的职责是帮助用户理解书籍内容，回答关于书籍的问题。

当用户询问关于书籍的问题时，你应该：
1. 先使用工具搜索相关内容
2. 基于搜索结果回答问题
3. 如果搜索结果为空，如实告知用户没有找到相关内容
4. 不要编造内容，回答必须基于实际搜索到的内容

请用中文回答用户的问题。"""

    fun chat(
        provider: LLMProvider,
        book: Book,
        history: List<OpenAIClient.ChatMsg>,
        userMessage: String
    ): Pair<String, List<OpenAIClient.ChatMsg>> {
        val client = OpenAIClient(provider.baseUrl, provider.apiKey, provider.modelName)
        val tool = BookSearchTool(book)
        val toolDefs = tool.getToolDefs()

        val messages = mutableListOf<OpenAIClient.ChatMsg>()
        messages.add(OpenAIClient.ChatMsg("system", SYSTEM_PROMPT))
        messages.addAll(history)
        messages.add(OpenAIClient.ChatMsg("user", userMessage))

        val allMessages = messages.toMutableList()

        for (iteration in 0 until 5) {
            val response = client.chat(allMessages, toolDefs)

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
                val result = try {
                    tool.executeTool(tc.name, tc.arguments)
                } catch (e: Exception) {
                    "工具执行错误: ${e.message}"
                }
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
}
