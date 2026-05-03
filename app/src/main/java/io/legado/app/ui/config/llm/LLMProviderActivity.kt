package io.legado.app.ui.config.llm

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.LLMProvider
import io.legado.app.databinding.ActivityLlmProviderBinding
import io.legado.app.databinding.DialogLlmProviderEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LLMProviderActivity :
    VMBaseActivity<ActivityLlmProviderBinding, LLMProviderViewModel>(),
    LLMProviderAdapter.CallBack {

    override val binding by viewBinding(ActivityLlmProviderBinding::inflate)
    override val viewModel by viewModels<LLMProviderViewModel>()
    private val adapter by lazy { LLMProviderAdapter(this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initFAB()
        observeData()
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setEdgeEffectColor(primaryColor)
    }

    private fun initFAB() {
        binding.fabAdd.setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            appDb.llmProviderDao.observeAll().collectLatest { providers ->
                adapter.submitList(providers)
            }
        }
    }

    override fun editProvider(provider: LLMProvider) {
        showEditDialog(provider)
    }

    override fun deleteProvider(provider: LLMProvider) {
        alert(getString(R.string.delete)) {
            setMessage("确定删除 ${provider.name}？")
            okButton {
                viewModel.delete(provider) {
                    toastOnUi("已删除")
                }
            }
            noButton()
        }
    }

    override fun setDefault(provider: LLMProvider) {
        viewModel.setDefault(provider) {
            toastOnUi("已设为默认")
        }
    }

    private fun showEditDialog(provider: LLMProvider?) {
        val dialogBinding = DialogLlmProviderEditBinding.inflate(LayoutInflater.from(this))
        val isEdit = provider != null

        provider?.let {
            dialogBinding.etName.setText(it.name)
            dialogBinding.etBaseUrl.setText(it.baseUrl)
            dialogBinding.etApiKey.setText(it.apiKey)
            dialogBinding.etModelName.setText(it.modelName)
            dialogBinding.switchDefault.isChecked = it.isDefault
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isEdit) R.string.edit else R.string.add)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val baseUrl = dialogBinding.etBaseUrl.text.toString().trim()
                val apiKey = dialogBinding.etApiKey.text.toString().trim()
                val modelName = dialogBinding.etModelName.text.toString().trim()
                val isDefault = dialogBinding.switchDefault.isChecked

                if (name.isEmpty() || baseUrl.isEmpty() || modelName.isEmpty()) {
                    toastOnUi("请填写完整信息")
                    return@setPositiveButton
                }

                val newProvider = (provider ?: LLMProvider()).copy(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelName = modelName,
                    isDefault = isDefault
                )

                if (isEdit) {
                    viewModel.update(newProvider) {
                        toastOnUi("已更新")
                    }
                } else {
                    viewModel.insert(newProvider) {
                        toastOnUi("已添加")
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
