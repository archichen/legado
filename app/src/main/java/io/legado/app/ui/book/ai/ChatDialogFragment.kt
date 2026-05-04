package io.legado.app.ui.book.ai

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.ChatMessage
import io.legado.app.data.entities.LLMProvider
import io.legado.app.databinding.DialogChatBinding
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogChatBinding? = null
    private val binding get() = _binding!!
    private var bookUrl: String = ""
    private val adapter = ChatAdapter()
    private var provider: LLMProvider? = null
    private var chatHistory = listOf<OpenAIClient.ChatMsg>()
    private var currentJob: Job? = null

    companion object {
        private const val TAG = "ChatDialogFragment"
        private const val ARG_BOOK_URL = "book_url"

        fun show(fragmentManager: FragmentManager, bookUrl: String) {
            val fragment = ChatDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOOK_URL, bookUrl)
                }
            }
            fragment.show(fragmentManager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookUrl = arguments?.getString(ARG_BOOK_URL) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initInput()
        observeMessages()
        loadProvider()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
    }

    private fun initInput() {
        binding.btnSend.setOnClickListener {
            val message = binding.etInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.etInput.text?.clear()
            }
        }
        binding.btnClear.setOnClickListener {
            clearHistory()
        }
    }

    private fun setSendMode() {
        binding.btnSend.text = getString(R.string.ai_chat_send)
        binding.btnSend.setOnClickListener {
            val message = binding.etInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.etInput.text?.clear()
            }
        }
    }

    private fun setStopMode() {
        binding.btnSend.text = getString(R.string.ai_chat_stop)
        binding.btnSend.setOnClickListener {
            currentJob?.cancel()
            currentJob = null
            setSendMode()
            binding.etInput.isEnabled = true
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                appDb.chatMessageDao.insert(
                    ChatMessage(
                        bookUrl = bookUrl,
                        role = ChatMessage.ROLE_ASSISTANT,
                        content = "（用户已终止）"
                    )
                )
            }
        }
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.chatMessageDao.observeByBook(bookUrl).collectLatest { messages ->
                adapter.submitList(messages)
                chatHistory = messages
                    .filter { it.role == ChatMessage.ROLE_USER || it.role == ChatMessage.ROLE_ASSISTANT }
                    .map { msg ->
                        OpenAIClient.ChatMsg(
                            role = msg.role,
                            content = msg.content
                        )
                    }
                if (messages.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun loadProvider() {
        viewLifecycleOwner.lifecycleScope.launch {
            provider = withContext(Dispatchers.IO) {
                appDb.llmProviderDao.getDefault()
            }
            if (provider == null) {
                toastOnUi(R.string.llm_no_provider)
            }
        }
    }

    private fun sendMessage(content: String) {
        val currentProvider = provider
        if (currentProvider == null) {
            toastOnUi(R.string.llm_no_provider)
            return
        }

        currentJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appDb.chatMessageDao.insert(
                    ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_USER, content = content)
                )
            }

            binding.etInput.isEnabled = false
            setStopMode()

            try {
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(bookUrl)
                } ?: return@launch

                val (response, newHistory) = withContext(Dispatchers.IO) {
                    AgentFactory.chat(currentProvider, book, chatHistory, content,
                        object : AgentFactory.Callback {
                            override fun onThinking(thinking: String) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    val thinkingMsg = ChatMessage(
                                        bookUrl = bookUrl,
                                        role = ChatMessage.ROLE_THINKING,
                                        content = thinking.take(200)
                                    )
                                    adapter.submitList(adapter.currentList + thinkingMsg)
                                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                                }
                            }

                            override fun onToolCall(toolName: String, arguments: String) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    val toolCallMsg = ChatMessage(
                                        bookUrl = bookUrl,
                                        role = ChatMessage.ROLE_TOOL_CALL,
                                        content = "$toolName($arguments)"
                                    )
                                    adapter.submitList(adapter.currentList + toolCallMsg)
                                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                                }
                            }

                            override fun onToolResult(toolName: String, result: String) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    val resultMsg = ChatMessage(
                                        bookUrl = bookUrl,
                                        role = ChatMessage.ROLE_TOOL_RESULT,
                                        content = result.take(200)
                                    )
                                    adapter.submitList(adapter.currentList + resultMsg)
                                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }
                    )
                }

                chatHistory = newHistory

                withContext(Dispatchers.IO) {
                    appDb.chatMessageDao.insert(
                        ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_ASSISTANT, content = response)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val errorMsg = e.message ?: "Unknown error"
                withContext(Dispatchers.IO) {
                    appDb.chatMessageDao.insert(
                        ChatMessage(
                            bookUrl = bookUrl,
                            role = ChatMessage.ROLE_ASSISTANT,
                            content = "错误: $errorMsg"
                        )
                    )
                }
            } finally {
                currentJob = null
                binding.etInput.isEnabled = true
                setSendMode()
            }
        }
    }

    private fun clearHistory() {
        currentJob?.cancel()
        currentJob = null
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appDb.chatMessageDao.deleteByBook(bookUrl)
            }
            chatHistory = emptyList()
            setSendMode()
            binding.etInput.isEnabled = true
        }
    }

    override fun onDestroyView() {
        currentJob?.cancel()
        currentJob = null
        super.onDestroyView()
        _binding = null
    }
}
