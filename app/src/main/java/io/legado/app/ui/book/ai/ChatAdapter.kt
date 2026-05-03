package io.legado.app.ui.book.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.ChatMessage
import io.legado.app.databinding.ItemChatMessageBinding

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
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
            when (item.role) {
                ChatMessage.ROLE_USER -> {
                    binding.tvUserMessage.text = item.content
                    binding.tvUserMessage.visibility = android.view.View.VISIBLE
                    binding.tvAssistantMessage.visibility = android.view.View.GONE
                }
                ChatMessage.ROLE_ASSISTANT -> {
                    binding.tvAssistantMessage.text = item.content
                    binding.tvAssistantMessage.visibility = android.view.View.VISIBLE
                    binding.tvUserMessage.visibility = android.view.View.GONE
                }
                else -> {
                    binding.tvUserMessage.visibility = android.view.View.GONE
                    binding.tvAssistantMessage.visibility = android.view.View.GONE
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
