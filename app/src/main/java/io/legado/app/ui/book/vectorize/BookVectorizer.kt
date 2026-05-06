package io.legado.app.ui.book.vectorize

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
        if (!embeddingClient.isReady()) {
            embeddingClient.initialize()
        }

        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) return@withContext

        val totalChapters = chapters.size

        for ((idx, chapter) in chapters.withIndex()) {
            coroutineContext.ensureActive()
            if (isCancelled) return@withContext

            val existingStatus = chapter.vectorizeStatus
            if (existingStatus == "completed") {
                callback?.onProgress(idx, totalChapters, "skipped")
                continue
            }

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
                    val source = appDb.bookSourceDao.getBookSource(book.origin)
                    if (source != null) {
                        try {
                            content = CacheBook.getOrCreate(source, book).downloadAwait(chapter)
                            if (!content.isNullOrBlank()) {
                                BookHelp.saveText(book, chapter, content)
                            }
                        } catch (e: Exception) {
                            appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "failed")
                            callback?.onChapterFailed(idx, "下载失败: ${e.message}")
                            continue
                        }
                    }
                }

                if (content.isNullOrBlank()) {
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "completed")
                    callback?.onChapterComplete(idx, 0)
                    continue
                }

                val chunks = TextChunker.chunkChapter(content, chapter.index, chapter.title)
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
                        return@withContext
                    }

                    callback?.onEncodingProgress(idx, chunkIdx + 1, chunks.size)

                    val vector = embeddingClient.encode(chunk.text)
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
                callback?.onChapterComplete(idx, chunks.size)

            } catch (e: Exception) {
                coroutineContext.ensureActive()
                if (isCancelled) {
                    appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "pending")
                    return@withContext
                }
                appDb.bookChapterDao.upVectorizeStatus(book.bookUrl, chapter.index, "failed")
                callback?.onChapterFailed(idx, e.message ?: "Unknown error")
            }
        }
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
