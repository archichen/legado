package io.legado.app.ui.book.vectorize

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.legado.app.constant.AppLog
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

        AppLog.put("ONNX: 开始初始化 embedding 模型...")
        val startTime = System.currentTimeMillis()

        ortEnv = OrtEnvironment.getEnvironment()

        val modelPath = copyAssetToCache("models/bge-small-zh/model_quantized.onnx")
        val dataPath = copyAssetToCache("models/bge-small-zh/model_quantized.onnx_data")
        AppLog.put("ONNX: 模型文件已复制到 cache, model=${File(modelPath).length()}bytes")

        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        sessionOptions.setIntraOpNumThreads(4)

        ortSession = ortEnv!!.createSession(modelPath, sessionOptions)
        AppLog.put("ONNX: OrtSession 创建完成, 耗时 ${System.currentTimeMillis() - startTime}ms")

        tokenizer = BertTokenizer(context)
        AppLog.put("ONNX: Tokenizer 加载完成")

        initialized = true
        AppLog.put("ONNX: 初始化完成, 总耗时 ${System.currentTimeMillis() - startTime}ms, dimension=$dimension")
    }

    override suspend fun encode(text: String): FloatArray = withContext(Dispatchers.IO) {
        check(initialized) { "OnnxEmbeddingClient not initialized" }

        val startTime = System.currentTimeMillis()
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
                        AppLog.put("ONNX: 意外的输出格式 (batch[0]不是FloatArray)")
                        FloatArray(dimension)
                    }
                } else {
                    AppLog.put("ONNX: 意外的输出格式 (output[0]不是Array)")
                    FloatArray(dimension)
                }
            }
            else -> {
                AppLog.put("ONNX: 意外的输出类型: ${output?.javaClass?.name}")
                FloatArray(dimension)
            }
        }

        inputIdsTensor.close()
        attentionMaskTensor.close()
        tokenTypeIdsTensor.close()
        results.close()

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > 100) {
            AppLog.put("ONNX: encode 耗时 ${elapsed}ms, tokens=$maxLen, text=${text.take(30)}...")
        }

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
