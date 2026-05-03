package io.legado.app.ui.config.llm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.LLMProvider
import io.legado.app.databinding.ItemLlmProviderBinding
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class LLMProviderAdapter(
    private val callBack: CallBack
) : ListAdapter<LLMProvider, LLMProviderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLlmProviderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemLlmProviderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LLMProvider) {
            binding.tvName.text = item.name
            binding.tvModel.text = item.modelName
            binding.tvBaseUrl.text = item.baseUrl

            if (item.isDefault) {
                binding.ivDefault.visible()
            } else {
                binding.ivDefault.gone()
            }

            binding.ivEdit.setOnClickListener {
                callBack.editProvider(item)
            }

            binding.ivDelete.setOnClickListener {
                callBack.deleteProvider(item)
            }

            binding.root.setOnClickListener {
                callBack.setDefault(item)
            }
        }
    }

    interface CallBack {
        fun editProvider(provider: LLMProvider)
        fun deleteProvider(provider: LLMProvider)
        fun setDefault(provider: LLMProvider)
    }

    private class DiffCallback : DiffUtil.ItemCallback<LLMProvider>() {
        override fun areItemsTheSame(oldItem: LLMProvider, newItem: LLMProvider): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LLMProvider, newItem: LLMProvider): Boolean {
            return oldItem == newItem
        }
    }
}
