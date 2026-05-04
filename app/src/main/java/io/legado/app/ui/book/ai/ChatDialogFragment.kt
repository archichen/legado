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

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.chatMessageDao.observeByBook(bookUrl).collectLatest { messages ->
                adapter.submitList(messages)
                chatHistory = messages.map { msg ->
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

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appDb.chatMessageDao.insert(
                    ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_USER, content = content)
                )
            }

            binding.etInput.isEnabled = false
            binding.btnSend.isEnabled = false

            try {
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(bookUrl)
                } ?: return@launch

                val (response, newHistory) = withContext(Dispatchers.IO) {
                    AgentFactory.chat(currentProvider, book, chatHistory, content)
                }

                chatHistory = newHistory

                withContext(Dispatchers.IO) {
                    appDb.chatMessageDao.insert(
                        ChatMessage(bookUrl = bookUrl, role = ChatMessage.ROLE_ASSISTANT, content = response)
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    appDb.chatMessageDao.insert(
                        ChatMessage(
                            bookUrl = bookUrl,
                            role = ChatMessage.ROLE_ASSISTANT,
                            content = getString(R.string.ai_error, e.message)
                        )
                    )
                }
            } finally {
                binding.etInput.isEnabled = true
                binding.btnSend.isEnabled = true
            }
        }
    }

    private fun clearHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appDb.chatMessageDao.deleteByBook(bookUrl)
            }
            chatHistory = emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
