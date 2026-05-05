package io.legado.app.ui.book.vectorize

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

class OnnxEmbeddingClient(
    private val context: Context
) : EmbeddingClient {

    override val dimension: Int = 512

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BertTokenizer? = null
    private var initialized = false

    override fun isReady(): Boolean = initialized

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        ortEnv = OrtEnvironment.getEnvironment()

        val modelPath = copyAssetToCache("models/bge-small-zh/model_quantized.onnx")
        val dataPath = copyAssetToCache("models/bge-small-zh/model_quantized.onnx_data")

        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        sessionOptions.setIntraOpNumThreads(4)

        ortSession = ortEnv!!.createSession(modelPath, sessionOptions)
        tokenizer = BertTokenizer(context)

        initialized = true
    }

    override suspend fun encode(text: String): FloatArray = withContext(Dispatchers.IO) {
        check(initialized) { "OnnxEmbeddingClient not initialized" }

        val tokenized = tokenizer!!.encode(text)
        val maxLen = tokenized.actualLength

        val inputIds = LongArray(maxLen) { tokenized.inputIds[it].toLong() }
        val attentionMask = LongArray(maxLen) { tokenized.attentionMask[it].toLong() }
        val tokenTypeIds = LongArray(maxLen) { tokenized.tokenTypeIds[it].toLong() }

        val shape = longArrayOf(1, maxLen.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(ortEnv!!, LongBuffer.wrap(inputIds), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv!!, LongBuffer.wrap(attentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnv!!, LongBuffer.wrap(tokenTypeIds), shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )

        val results = ortSession!!.run(inputs)
        val output = results.get(0).value

        val embedding = when (output) {
            is Array<*> -> {
                if (output.isNotEmpty() && output[0] is Array<*>) {
                    val batch = output[0] as Array<*>
                    if (batch.isNotEmpty() && batch[0] is FloatArray) {
                        meanPooling(batch as Array<FloatArray>, attentionMask)
                    } else {
                        FloatArray(dimension)
                    }
                } else {
                    FloatArray(dimension)
                }
            }
            else -> FloatArray(dimension)
        }

        inputIdsTensor.close()
        attentionMaskTensor.close()
        tokenTypeIdsTensor.close()
        results.close()

        normalize(embedding)
    }

    override suspend fun encodeBatch(texts: List<String>, batchSize: Int): List<FloatArray> {
        return texts.map { encode(it) }
    }

    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val result = FloatArray(dimension)
        var maskSum = 0f

        for (i in tokenEmbeddings.indices) {
            if (i < attentionMask.size && attentionMask[i] == 1L) {
                for (j in 0 until dimension) {
                    if (j < tokenEmbeddings[i].size) {
                        result[j] += tokenEmbeddings[i][j]
                    }
                }
                maskSum += 1f
            }
        }

        if (maskSum > 0f) {
            for (j in result.indices) result[j] /= maskSum
        }

        return result
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = Math.sqrt(norm.toDouble()).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    private fun copyAssetToCache(assetPath: String): String {
        val cacheFile = File(context.cacheDir, assetPath.replace("/", "_"))
        if (cacheFile.exists()) return cacheFile.absolutePath

        context.assets.open(assetPath).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        return cacheFile.absolutePath
    }
}
