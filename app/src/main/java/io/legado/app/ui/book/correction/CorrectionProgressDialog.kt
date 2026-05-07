package io.legado.app.ui.book.correction

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
import io.legado.app.data.entities.CorrectionTask
import io.legado.app.databinding.DialogCorrectionProgressBinding
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CorrectionProgressDialog : BottomSheetDialogFragment() {

    private var _binding: DialogCorrectionProgressBinding? = null
    private val binding get() = _binding!!
    private var bookUrl: String = ""

    companion object {
        private const val TAG = "CorrectionProgressDialog"
        private const val ARG_BOOK_URL = "book_url"

        fun show(fragmentManager: FragmentManager, bookUrl: String) {
            val fragment = CorrectionProgressDialog().apply {
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
        _binding = DialogCorrectionProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initButtons()
        observeProgress()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    private fun initButtons() {
        binding.btnStart.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val provider = withContext(Dispatchers.IO) {
                    appDb.llmProviderDao.getDefault()
                }
                if (provider == null) {
                    toastOnUi(R.string.llm_no_provider)
                    return@launch
                }
                CorrectionService.startCorrection(bookUrl, provider)
            }
        }

        binding.btnStop.setOnClickListener {
            CorrectionService.stopCorrection()
            toastOnUi("已停止修正")
        }

        binding.btnClear.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    appDb.correctionTaskDao.deletePendingByBook(bookUrl)
                }
                toastOnUi("已清除待处理任务")
            }
        }
    }

    private fun observeProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                updateProgress()
                delay(2000)
            }
        }
    }

    private suspend fun updateProgress() {
        val completed = withContext(Dispatchers.IO) {
            appDb.correctionTaskDao.getCompletedCount(bookUrl)
        }
        val pending = withContext(Dispatchers.IO) {
            appDb.correctionTaskDao.getPendingCount(bookUrl)
        }
        val processing = withContext(Dispatchers.IO) {
            appDb.correctionTaskDao.getProcessingCount(bookUrl)
        }
        val failed = withContext(Dispatchers.IO) {
            appDb.correctionTaskDao.getFailedCount(bookUrl)
        }
        val total = withContext(Dispatchers.IO) {
            appDb.correctionTaskDao.getTotalCount(bookUrl)
        }

        _binding?.let { b ->
            b.tvCompleted.text = "已完成: $completed"
            b.tvPending.text = "待处理: $pending"
            b.tvProcessing.text = "进行中: $processing"
            b.tvFailed.text = "失败: $failed"
            b.tvTotal.text = "总计: $total"

            val isRunning = CorrectionService.isActive()
            b.btnStart.isEnabled = !isRunning
            b.btnStop.isEnabled = isRunning
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
