package io.legado.app.ui.book.vectorize

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityVectorizeBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VectorizeActivity :
    VMBaseActivity<ActivityVectorizeBinding, VectorizeViewModel>() {

    override val binding by viewBinding(ActivityVectorizeBinding::inflate)
    override val viewModel by viewModels<VectorizeViewModel>()
    private val adapter = VectorizeAdapter { chapter ->
        toastOnUi("第${chapter.index + 1}章「${chapter.title}」状态: ${chapter.vectorizeStatus ?: "待处理"}")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val bookUrl = intent.getStringExtra("bookUrl") ?: run {
            finish()
            return
        }

        initRecyclerView()
        initButtons()
        observeData()

        viewModel.initBook(bookUrl)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setEdgeEffectColor(primaryColor)
    }

    private fun initButtons() {
        binding.btnStart.setOnClickListener {
            viewModel.startVectorize()
        }
        binding.btnStop.setOnClickListener {
            viewModel.stopVectorize()
        }
        binding.btnClear.setOnClickListener {
            viewModel.clearVectorize()
        }
    }

    private fun observeData() {
        viewModel.progressData.observe(this) { progress ->
            binding.tvProgress.text = "进度: ${progress.completed}/${progress.total} (${progress.percentage}%) " +
                "| 待处理: ${progress.pending} | 进行中: ${progress.processing} | 失败: ${progress.failed}"
            binding.progressBar.progress = progress.percentage

            binding.btnStart.isEnabled = !progress.isRunning && !progress.isComplete
            binding.btnStop.isEnabled = progress.isRunning
        }

        viewModel.chaptersData.observe(this) { chapters ->
            adapter.submitList(chapters)
        }

        viewModel.statusMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) toastOnUi(msg)
        }
    }
}
