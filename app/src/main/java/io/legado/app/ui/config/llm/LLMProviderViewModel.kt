package io.legado.app.ui.config.llm

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.LLMProvider

class LLMProviderViewModel(application: Application) : BaseViewModel(application) {

    fun insert(provider: LLMProvider, success: () -> Unit) {
        execute {
            if (provider.isDefault) {
                appDb.llmProviderDao.all.forEach {
                    if (it.isDefault && it.id != provider.id) {
                        it.isDefault = false
                        appDb.llmProviderDao.update(it)
                    }
                }
            }
            appDb.llmProviderDao.insert(provider)
        }.onSuccess {
            success.invoke()
        }
    }

    fun update(provider: LLMProvider, success: () -> Unit) {
        execute {
            if (provider.isDefault) {
                appDb.llmProviderDao.all.forEach {
                    if (it.isDefault && it.id != provider.id) {
                        it.isDefault = false
                        appDb.llmProviderDao.update(it)
                    }
                }
            }
            appDb.llmProviderDao.update(provider)
        }.onSuccess {
            success.invoke()
        }
    }

    fun delete(provider: LLMProvider, success: () -> Unit) {
        execute {
            appDb.llmProviderDao.delete(provider)
        }.onSuccess {
            success.invoke()
        }
    }

    fun setDefault(provider: LLMProvider, success: () -> Unit) {
        execute {
            appDb.llmProviderDao.all.forEach {
                if (it.isDefault) {
                    it.isDefault = false
                    appDb.llmProviderDao.update(it)
                }
            }
            provider.isDefault = true
            appDb.llmProviderDao.update(provider)
        }.onSuccess {
            success.invoke()
        }
    }
}
