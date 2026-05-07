package io.legado.app.ui.book.correction

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.CorrectionTask
import io.legado.app.data.entities.LLMProvider
import io.legado.app.help.book.BookHelp
import io.legado.app.ui.book.ai.OpenAIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class CorrectionResult(
    val correctedText: String,
    val corrections: List<Correction>,
    val hasCorrections: Boolean
)

data class Correction(
    val original: String,
    val corrected: String,
    val reason: String
)

object CorrectionService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val gson = Gson()

    fun startCorrection(bookUrl: String, provider: LLMProvider) {
        if (currentJob?.isActive == true) {
            AppLog.put("CorrectionService: 已有任务在运行")
            return
        }

        currentJob = scope.launch {
            AppLog.put("CorrectionService: 开始修正 bookUrl=$bookUrl")

            val book = appDb.bookDao.getBook(bookUrl) ?: return@launch
            val chapters = appDb.bookChapterDao.getChapterList(bookUrl)
            val maxConcurrent = provider.maxConcurrent.coerceAtLeast(1)
            val semaphore = Semaphore(maxConcurrent)

            val pendingTasks = appDb.correctionTaskDao.getByStatus(bookUrl, CorrectionTask.STATUS_PENDING)
            if (pendingTasks.isEmpty()) {
                AppLog.put("CorrectionService: 没有待处理任务")
                return@launch
            }

            val jobs = pendingTasks.map { task ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        processTask(book, task, provider)
                    }
                }
            }

            jobs.forEach { it.join() }
            AppLog.put("CorrectionService: 全部完成")
        }
    }

    fun stopCorrection() {
        currentJob?.cancel()
        currentJob = null
        AppLog.put("CorrectionService: 已停止")
    }

    fun isActive(): Boolean = currentJob?.isActive == true

    private suspend fun processTask(book: Book, task: CorrectionTask, provider: LLMProvider) {
        coroutineContext.ensureActive()

        appDb.correctionTaskDao.update(task.copy(
            status = CorrectionTask.STATUS_PROCESSING,
            updatedAt = System.currentTimeMillis()
        ))

        try {
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, task.chapterIndex)
            if (chapter == null) {
                appDb.correctionTaskDao.update(task.copy(
                    status = CorrectionTask.STATUS_FAILED,
                    errorMessage = "章节不存在",
                    updatedAt = System.currentTimeMillis()
                ))
                return
            }

            val content = BookHelp.getContent(book, chapter)
            if (content.isNullOrBlank()) {
                appDb.correctionTaskDao.update(task.copy(
                    status = CorrectionTask.STATUS_COMPLETED,
                    updatedAt = System.currentTimeMillis()
                ))
                return
            }

            val maxChars = provider.maxCharsPerRequest
            val result = if (maxChars > 0 && content.length > maxChars) {
                processLongContent(content, provider, maxChars)
            } else {
                correctContent(content, provider)
            }

            if (result.hasCorrections) {
                BookHelp.saveText(book, chapter, result.correctedText)
                AppLog.put("CorrectionService: 第${task.chapterIndex + 1}章修正完成, ${result.corrections.size}处修改")
            }

            appDb.correctionTaskDao.update(task.copy(
                status = CorrectionTask.STATUS_COMPLETED,
                updatedAt = System.currentTimeMillis()
            ))

        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val retryCount = task.retryCount + 1
            if (retryCount >= CorrectionTask.MAX_RETRY) {
                appDb.correctionTaskDao.update(task.copy(
                    status = CorrectionTask.STATUS_FAILED,
                    retryCount = retryCount,
                    errorMessage = e.message,
                    updatedAt = System.currentTimeMillis()
                ))
                AppLog.put("CorrectionService: 第${task.chapterIndex + 1}章失败: ${e.message}")
            } else {
                appDb.correctionTaskDao.update(task.copy(
                    retryCount = retryCount,
                    status = CorrectionTask.STATUS_PENDING,
                    updatedAt = System.currentTimeMillis()
                ))
                val delayMs = 1000L * (1 shl retryCount)
                delay(delayMs)
                AppLog.put("CorrectionService: 第${task.chapterIndex + 1}章重试 $retryCount/${CorrectionTask.MAX_RETRY}")
            }
        }
    }

    private suspend fun correctContent(content: String, provider: LLMProvider): CorrectionResult {
        val client = OpenAIClient(provider.baseUrl, provider.apiKey, provider.modelName)

        val messages = listOf(
            OpenAIClient.ChatMsg(role = "system", content = CorrectionPrompt.SYSTEM_PROMPT),
            OpenAIClient.ChatMsg(role = "user", content = CorrectionPrompt.buildUserMessage(content))
        )

        val response = client.chat(messages, null)
        val responseText = response.content ?: throw Exception("空响应")

        return parseCorrectionResult(responseText, content)
    }

    private suspend fun processLongContent(
        content: String,
        provider: LLMProvider,
        maxChars: Int
    ): CorrectionResult {
        val segments = content.chunked(maxChars)
        val correctedSegments = mutableListOf<String>()
        val allCorrections = mutableListOf<Correction>()

        for (segment in segments) {
            val result = correctContent(segment, provider)
            correctedSegments.add(result.correctedText)
            allCorrections.addAll(result.corrections)
        }

        return CorrectionResult(
            correctedText = correctedSegments.joinToString(""),
            corrections = allCorrections,
            hasCorrections = allCorrections.isNotEmpty()
        )
    }

    private fun parseCorrectionResult(responseText: String, originalContent: String): CorrectionResult {
        return try {
            val jsonStr = extractJson(responseText)
            val json = JsonParser.parseString(jsonStr).asJsonObject

            val correctedText = json.get("corrected_text")?.asString ?: originalContent
            val hasCorrections = json.get("has_corrections")?.asBoolean ?: false

            val corrections = mutableListOf<Correction>()
            val correctionsArray = json.getAsJsonArray("corrections")
            correctionsArray?.forEach { item ->
                val obj = item.asJsonObject
                corrections.add(
                    Correction(
                        original = obj.get("original")?.asString ?: "",
                        corrected = obj.get("corrected")?.asString ?: "",
                        reason = obj.get("reason")?.asString ?: ""
                    )
                )
            }

            CorrectionResult(
                correctedText = correctedText,
                corrections = corrections,
                hasCorrections = hasCorrections
            )
        } catch (e: Exception) {
            AppLog.put("CorrectionService: JSON解析失败: ${e.message}")
            CorrectionResult(
                correctedText = originalContent,
                corrections = emptyList(),
                hasCorrections = false
            )
        }
    }

    private fun extractJson(text: String): String {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex >= 0 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1)
        }
        return text
    }
}
