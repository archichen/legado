package io.legado.app.ui.book.vectorize

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ItemVectorizeChapterBinding

class VectorizeAdapter(
    private val onClick: (BookChapter) -> Unit = {}
) : ListAdapter<BookChapter, VectorizeAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVectorizeChapterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemVectorizeChapterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookChapter) {
            binding.tvIndex.text = "[${item.index}]"
            binding.tvTitle.text = item.title

            when (item.vectorizeStatus) {
                "completed" -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_check)
                    binding.tvChunkCount.text = "✓"
                }
                "processing" -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_auto_page)
                    binding.tvChunkCount.text = "⏳"
                }
                "failed" -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_bug_report)
                    binding.tvChunkCount.text = "✗"
                }
                else -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_outline_cloud_24)
                    binding.tvChunkCount.text = ""
                }
            }

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BookChapter>() {
        override fun areItemsTheSame(oldItem: BookChapter, newItem: BookChapter): Boolean {
            return oldItem.bookUrl == newItem.bookUrl && oldItem.index == newItem.index
        }

        override fun areContentsTheSame(oldItem: BookChapter, newItem: BookChapter): Boolean {
            return oldItem.vectorizeStatus == newItem.vectorizeStatus &&
                oldItem.title == newItem.title
        }
    }
}
