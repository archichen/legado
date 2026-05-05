package io.legado.app.ui.book.vectorize

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EmbeddingManager {

    private var client: EmbeddingClient? = null
    private var isInitializing = false

    suspend fun getClient(context: Context): EmbeddingClient {
        client?.let { return it }

        synchronized(this) {
            client?.let { return it }
            if (isInitializing) {
                while (isInitializing) {
                    Thread.sleep(100)
                }
                return client!!
            }
            isInitializing = true
        }

        return withContext(Dispatchers.IO) {
            try {
                val c = OnnxEmbeddingClient(context.applicationContext)
                c.initialize()
                client = c
                c
            } catch (e: Exception) {
                val c = MockEmbeddingClient()
                c.initialize()
                client = c
                c
            } finally {
                isInitializing = false
            }
        }
    }

    fun getClientOrNull(): EmbeddingClient? = client
}
