package io.legado.app.ui.book.ai

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ChatMessage
import io.legado.app.data.entities.LLMProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AgentEvent(
    val bookUrl: String,
    val type: String,
    val content: String
)

object ChatAgentManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events

    private var currentJob: Job? = null

    fun sendMessage(
        bookUrl: String,
        content: String,
        provider: LLMProvider
    ) {
        if (currentJob?.isActive == true) {
            AppLog.put("ChatAgent: 已有任务在运行，忽略新消息")
            return
        }

        currentJob = scope.launch {
            _isRunning.value = true
            _status.value = "发送中..."

            try {
                appDb.chatMessageDao.insert(
                    ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_USER, content = content)
                )

                val book = appDb.bookDao.getBook(bookUrl)
                if (book == null) {
                    _status.value = "书籍未找到"
                    return@launch
                }

                val history = appDb.chatMessageDao.getByBook(bookUrl)
                    .filter { it.role == ChatMessage.ROLE_USER || it.role == ChatMessage.ROLE_ASSISTANT }
                    .map { OpenAIClient.ChatMsg(role = it.role, content = it.content) }

                _status.value = "Agent 处理中..."

                val (response, _) = AgentFactory.chat(provider, book, history, content,
                    object : AgentFactory.Callback {
                        override fun onThinking(thinking: String) {
                            _events.tryEmit(AgentEvent(bookUrl, "thinking", thinking.take(200)))
                        }

                        override fun onToolCall(toolName: String, arguments: String) {
                            _events.tryEmit(AgentEvent(bookUrl, "tool_call", "$toolName($arguments)"))
                        }

                        override fun onToolResult(toolName: String, result: String) {
                            _events.tryEmit(AgentEvent(bookUrl, "tool_result", result.take(200)))
                        }
                    }
                )

                appDb.chatMessageDao.insert(
                    ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_ASSISTANT, content = response)
                )

                _status.value = "完成"
                AppLog.put("ChatAgent: 对话完成, 回复长度=${response.length}")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLog.put("ChatAgent: 任务被取消")
                    return@launch
                }
                AppLog.put("ChatAgent: 异常 ${e.message}", e)
                appDb.chatMessageDao.insert(
                    ChatMessage(
                        bookUrl = bookUrl,
                        role = ChatMessage.ROLE_ASSISTANT,
                        content = "错误: ${e.message}"
                    )
                )
                _status.value = "错误: ${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun cancel() {
        AppLog.put("ChatAgent: 用户取消")
        currentJob?.cancel()
        currentJob = null
        _isRunning.value = false
        _status.value = "已取消"
    }

    fun isActive(): Boolean = currentJob?.isActive == true
}
