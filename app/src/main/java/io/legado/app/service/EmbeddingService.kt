package io.legado.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.vectorize.BookVectorizer
import io.legado.app.ui.book.vectorize.EmbeddingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class EmbeddingService : Service() {

    companion object {
        private const val CHANNEL_ID = "embedding_channel"
        private const val NOTIFICATION_ID = 9001

        private val activeJobs = ConcurrentHashMap<String, Job>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start(context: Context, bookUrl: String) {
            val intent = Intent(context, EmbeddingService::class.java).apply {
                action = "START"
                putExtra("bookUrl", bookUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, bookUrl: String) {
            val intent = Intent(context, EmbeddingService::class.java).apply {
                action = "STOP"
                putExtra("bookUrl", bookUrl)
            }
            context.startService(intent)
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, EmbeddingService::class.java).apply {
                action = "STOP_ALL"
            }
            context.startService(intent)
        }

        fun isRunning(bookUrl: String): Boolean {
            return activeJobs[bookUrl]?.isActive == true
        }

        fun getRunningBooks(): Set<String> {
            return activeJobs.keys.toSet()
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification("向量化服务运行中"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("向量化服务运行中"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val bookUrl = intent.getStringExtra("bookUrl") ?: return START_NOT_STICKY
                startVectorize(bookUrl)
            }
            "STOP" -> {
                val bookUrl = intent.getStringExtra("bookUrl") ?: return START_NOT_STICKY
                stopVectorize(bookUrl)
            }
            "STOP_ALL" -> {
                stopAllVectorize()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVectorize(bookUrl: String) {
        if (activeJobs.containsKey(bookUrl)) {
            AppLog.put("EmbeddingService: 《$bookUrl》已在运行")
            return
        }

        val job = scope.launch {
            try {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@launch
                val client = EmbeddingManager.getClient(this@EmbeddingService)
                val vectorizer = BookVectorizer(book, client)

                AppLog.put("EmbeddingService: 开始向量化《${book.name}》")

                vectorizer.vectorize(object : BookVectorizer.Callback {
                    override fun onProgress(chapterIndex: Int, totalChapters: Int, status: String) {
                        if (status == "processing") {
                            updateNotification("《${book.name}》第${chapterIndex + 1}/${totalChapters}章")
                        }
                    }

                    override fun onChapterComplete(chapterIndex: Int, chunkCount: Int) {
                        updateNotification("《${book.name}》第${chapterIndex + 1}章完成")
                    }

                    override fun onChapterFailed(chapterIndex: Int, error: String) {
                        AppLog.put("EmbeddingService: 第${chapterIndex + 1}章失败: $error")
                    }

                    override fun onEncodingProgress(chapterIndex: Int, currentChunk: Int, totalChunks: Int) {
                        updateNotification("《${book.name}》第${chapterIndex + 1}章: $currentChunk/$totalChunks")
                    }
                })

                AppLog.put("EmbeddingService: 《${book.name}》向量化完成")

            } catch (e: Exception) {
                AppLog.put("EmbeddingService: 异常 ${e.message}", e)
            } finally {
                activeJobs.remove(bookUrl)
                if (activeJobs.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification("${activeJobs.size}本书向量化中")
                }
            }
        }

        activeJobs[bookUrl] = job
    }

    private fun stopVectorize(bookUrl: String) {
        activeJobs[bookUrl]?.cancel()
        activeJobs.remove(bookUrl)
        AppLog.put("EmbeddingService: 停止 $bookUrl")

        if (activeJobs.isEmpty()) {
            stopSelf()
        }
    }

    private fun stopAllVectorize() {
        activeJobs.forEach { (url, job) ->
            job.cancel()
            AppLog.put("EmbeddingService: 停止 $url")
        }
        activeJobs.clear()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "向量化服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示书籍向量化进度"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Legado 向量化")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_smart_toy)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        activeJobs.forEach { (_, job) -> job.cancel() }
        activeJobs.clear()
        super.onDestroy()
    }
}
