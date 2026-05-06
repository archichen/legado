package io.legado.app.ui.book.vectorize

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookEmbedding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class BookVectorizer(
    private val book: Book,
    private val embeddingClient: EmbeddingClient
) {

    interface Callback {
        fun onProgress(chapterIndex: Int, totalChapters: Int, status: String)
        fun onChapterComplete(chapterIndex: Int, chunkCount: Int)
        fun onChapterFailed(chapterIndex: Int, error: String)
        fun onEncodingProgress(chapterIndex: Int, currentChunk: Int, totalChunks: Int)
    }

    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    suspend fun vectorize(callback: Callback? = null) = withContext(Dispatchers.IO) {
        AppLog.put("Vectorize: 开始向量化《${book.name}》")

        if (!embeddingClient.isReady()) {
            AppLog.put("Vectorize: 初始化 embedding 客户端...")
            embeddingClient.initialize()
            AppLog.put("Vectorize: embedding 客户端初始化完成, ready=${embeddingClient.isReady()}")
        }

        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) {
            AppLog.put("Vectorize: 章节列表为空，退出")
            return@withContext
        }

        val totalChapters = chapters.size
        AppLog.put("Vectorize: 共 $totalChapters 章待处理")

        for ((idx, chapter) in chapters.withIndex()) {
            coroutineContext.ensureActive()
            if (isCancelled) {
                AppLog.put("Vectorize: 用户取消，停止于第${idx}章")
                return@withContext
            }

            val existingStatus = chapter.vectorizeStatus
            if (existingStatus == "completed") {
                continue
            }

            AppLog.put("Vectorize: 处理第${idx+1}/${totalChapters}章「${chapter.title}」")
            appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "processing")
            callback?.onProgress(idx, totalChapters, "processing")

            try {
                coroutineContext.ensureActive()
                if (isCancelled) {
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "pending")
                    return@withContext
                }

                var content = BookHelp.getContent(book, chapter)

                if (content.isNullOrBlank() && !book.isLocal) {
                    AppLog.put("Vectorize: 第${idx+1}章内容未缓存，尝试下载...")
                    val source = appDb.bookSourceDao.getBookSource(book.origin)
                    if (source != null) {
                        try {
                            content = CacheBook.getOrCreate(source, book).downloadAwait(chapter)
                            if (!content.isNullOrBlank()) {
                                BookHelp.saveText(book, chapter, content)
                                AppLog.put("Vectorize: 第${idx+1}章下载成功, ${content.length}字")
                            }
                        } catch (e: Exception) {
                            AppLog.put("Vectorize: 第${idx+1}章下载失败: ${e.message}")
                            appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "failed")
                            callback?.onChapterFailed(idx, "下载失败: ${e.message}")
                            continue
                        }
                    } else {
                        AppLog.put("Vectorize: 无书源，跳过第${idx+1}章")
                    }
                }

                if (content.isNullOrBlank()) {
                    AppLog.put("Vectorize: 第${idx+1}章内容为空，标记完成")
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "completed")
                    callback?.onChapterComplete(idx, 0)
                    continue
                }

                val chunks = TextChunker.chunkChapter(content, chapter.index, chapter.title)
                AppLog.put("Vectorize: 第${idx+1}章分块完成, ${chunks.size}个chunk")

                if (chunks.isEmpty()) {
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "completed")
                    callback?.onChapterComplete(idx, 0)
                    continue
                }

                appDb.bookEmbeddingDao.deleteByChapter(book.bookUrl, chapter.index)

                val embeddings = mutableListOf<BookEmbedding>()
                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    coroutineContext.ensureActive()
                    if (isCancelled) {
                        appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "pending")
                        AppLog.put("Vectorize: 用户取消于第${idx+1}章 chunk $chunkIdx")
                        return@withContext
                    }

                    callback?.onEncodingProgress(idx, chunkIdx + 1, chunks.size)

                    val startTime = System.currentTimeMillis()
                    val vector = embeddingClient.encode(chunk.text)
                    val elapsed = System.currentTimeMillis() - startTime

                    embeddings.add(
                        BookEmbedding(
                            bookUrl = book.bookUrl,
                            chapterIndex = chunk.chapterIndex,
                            chunkIndex = chunk.chunkIndex,
                            chapterTitle = chunk.chapterTitle,
                            text = chunk.text,
                            vector = floatArrayToByteArray(vector)
                        )
                    )
                }

                appDb.bookEmbeddingDao.insertAll(embeddings)
                appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "completed")
                AppLog.put("Vectorize: 第${idx+1}章完成, ${chunks.size}个chunk已存储")
                callback?.onChapterComplete(idx, chunks.size)

            } catch (e: Exception) {
                coroutineContext.ensureActive()
                if (isCancelled) {
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "pending")
                    return@withContext
                }
                AppLog.put("Vectorize: 第${idx+1}章异常: ${e.message}", e)
                appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "failed")
                callback?.onChapterFailed(idx, e.message ?: "Unknown error")
            }
        }

        AppLog.put("Vectorize: 向量化完成《${book.name}》")
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        appDb.bookEmbeddingDao.deleteByBook(book.bookUrl)
        appDb.bookChapterDao.clearVectorizeStatus(book.bookUrl)
    }

    fun getProgress(): VectorizeProgress {
        val total = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        val completed = appDb.bookChapterDao.getVectorizedCount(book.bookUrl)
        val pending = appDb.bookChapterDao.getByVectorizeStatus(book.bookUrl, "pending").size
        val processing = appDb.bookChapterDao.getByVectorizeStatus(book.bookUrl, "processing").size
        val failed = appDb.bookChapterDao.getByVectorizeStatus(book.bookUrl, "failed").size
        return VectorizeProgress(total, completed, pending, processing, failed)
    }

    companion object {
        fun floatArrayToByteArray(floats: FloatArray): ByteArray {
            val bytes = ByteArray(floats.size * 4)
            for (i in floats.indices) {
                val bits = floats[i].toRawBits()
                bytes[i * 4] = (bits and 0xFF).toByte()
                bytes[i * 4 + 1] = (bits shr 8 and 0xFF).toByte()
                bytes[i * 4 + 2] = (bits shr 16 and 0xFF).toByte()
                bytes[i * 4 + 3] = (bits shr 24 and 0xFF).toByte()
            }
            return bytes
        }

        fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                val bits = (bytes[i * 4].toInt() and 0xFF) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
                floats[i] = Float.fromBits(bits)
            }
            return floats
        }
    }
}

data class VectorizeProgress(
    val total: Int,
    val completed: Int,
    val pending: Int,
    val processing: Int,
    val failed: Int
) {
    val percentage: Int get() = if (total > 0) (completed * 100 / total) else 0
    val isComplete: Boolean get() = completed == total && total > 0
    val isRunning: Boolean get() = processing > 0
}
