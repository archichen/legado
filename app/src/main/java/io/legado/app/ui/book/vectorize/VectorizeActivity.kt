package io.legado.app.ui.book.vectorize

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityVectorizeBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.EmbeddingService
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VectorizeActivity :
    VMBaseActivity<ActivityVectorizeBinding, VectorizeViewModel>() {

    override val binding by viewBinding(ActivityVectorizeBinding::inflate)
    override val viewModel by viewModels<VectorizeViewModel>()
    private val adapter = VectorizeAdapter { chapter ->
        toastOnUi("第${chapter.index + 1}章「${chapter.title}」状态: ${chapter.vectorizeStatus ?: "待处理"}")
    }
    private var bookUrl: String = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookUrl = intent.getStringExtra("bookUrl") ?: run {
            finish()
            return
        }

        initRecyclerView()
        initButtons()
        observeData()
        observeServiceStatus()

        viewModel.initBook(bookUrl)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setEdgeEffectColor(primaryColor)
    }

    private fun initButtons() {
        binding.btnStart.setOnClickListener {
            EmbeddingService.start(this, bookUrl)
        }
        binding.btnStop.setOnClickListener {
            EmbeddingService.stop(this, bookUrl)
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

            val isRunning = EmbeddingService.isRunning(bookUrl)
            binding.btnStart.isEnabled = !isRunning && !progress.isComplete
            binding.btnStop.isEnabled = isRunning
        }

        viewModel.chaptersData.observe(this) { chapters ->
            adapter.submitList(chapters)
        }
    }

    private fun observeServiceStatus() {
        lifecycleScope.launch {
            while (isActive) {
                val isRunning = EmbeddingService.isRunning(bookUrl)
                binding.btnStart.isEnabled = !isRunning
                binding.btnStop.isEnabled = isRunning
                viewModel.loadChapters()
                viewModel.updateProgress()
                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
