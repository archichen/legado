package io.legado.app.ui.book.ai

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.ChatMessage
import io.legado.app.databinding.ItemChatMessageBinding
import io.noties.markwon.Markwon

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback()) {

    private var markwon: Markwon? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (markwon == null) {
            markwon = Markwon.create(parent.context)
        }
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage) {
            binding.tvUserMessage.visibility = android.view.View.GONE
            binding.tvAssistantMessage.visibility = android.view.View.GONE

            when (item.role) {
                ChatMessage.ROLE_USER -> {
                    binding.tvUserMessage.text = item.content
                    binding.tvUserMessage.visibility = android.view.View.VISIBLE
                }
                ChatMessage.ROLE_ASSISTANT -> {
                    binding.tvAssistantMessage.setTextColor(Color.parseColor("#212121"))
                    markwon?.setMarkdown(binding.tvAssistantMessage, item.content)
                        ?: run { binding.tvAssistantMessage.text = item.content }
                    binding.tvAssistantMessage.visibility = android.view.View.VISIBLE
                }
                ChatMessage.ROLE_THINKING -> {
                    binding.tvAssistantMessage.text = "💭 思考中: ${item.content}"
                    binding.tvAssistantMessage.setTextColor(Color.parseColor("#757575"))
                    binding.tvAssistantMessage.visibility = android.view.View.VISIBLE
                }
                ChatMessage.ROLE_TOOL_CALL -> {
                    binding.tvAssistantMessage.text = "🔍 搜索: ${item.content}"
                    binding.tvAssistantMessage.setTextColor(Color.parseColor("#1976D2"))
                    binding.tvAssistantMessage.visibility = android.view.View.VISIBLE
                }
                ChatMessage.ROLE_TOOL_RESULT -> {
                    binding.tvAssistantMessage.text = "📋 结果: ${item.content}"
                    binding.tvAssistantMessage.setTextColor(Color.parseColor("#388E3C"))
                    binding.tvAssistantMessage.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
