package io.legado.app.ui.book.vectorize

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter

class VectorizeViewModel(application: Application) : BaseViewModel(application) {

    val progressData = MutableLiveData<VectorizeProgress>()
    val chaptersData = MutableLiveData<List<BookChapter>>()
    val statusMessage = MutableLiveData<String>()

    var book: Book? = null
    private var vectorizer: BookVectorizer? = null
    private val embeddingClient = MockEmbeddingClient()

    fun initBook(bookUrl: String) {
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            book?.let { b ->
                vectorizer = BookVectorizer(b, embeddingClient)
                loadChapters()
                updateProgress()
            }
        }
    }

    fun loadChapters() {
        val b = book ?: return
        execute {
            val chapters = appDb.bookChapterDao.getChapterList(b.bookUrl)
            chaptersData.postValue(chapters)
        }
    }

    fun updateProgress() {
        val v = vectorizer ?: return
        execute {
            val progress = v.getProgress()
            progressData.postValue(progress)
        }
    }

    fun startVectorize() {
        val b = book ?: return
        val v = vectorizer ?: return

        execute {
            statusMessage.postValue("向量化开始...")
            v.vectorize(object : BookVectorizer.Callback {
                override fun onProgress(chapterIndex: Int, totalChapters: Int, status: String) {
                    if (status == "processing") {
                        statusMessage.postValue("正在处理第 ${chapterIndex + 1}/$totalChapters 章...")
                    }
                }

                override fun onChapterComplete(chapterIndex: Int, chunkCount: Int) {
                    loadChapters()
                    updateProgress()
                }

                override fun onChapterFailed(chapterIndex: Int, error: String) {
                    statusMessage.postValue("第 ${chapterIndex + 1} 章失败: $error")
                    loadChapters()
                    updateProgress()
                }
            })

            if (v.isCancelled) {
                statusMessage.postValue("向量化已停止")
            } else {
                statusMessage.postValue("向量化完成！")
            }
            loadChapters()
            updateProgress()
        }
    }

    fun stopVectorize() {
        vectorizer?.cancel()
        statusMessage.postValue("正在停止...")
    }

    fun clearVectorize() {
        val v = vectorizer ?: return
        execute {
            v.clearAll()
            loadChapters()
            updateProgress()
            statusMessage.postValue("已清除所有向量数据")
        }
    }
}
