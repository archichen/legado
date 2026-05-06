package io.legado.app.ui.book.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAIClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val modelName: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(messages: List<ChatMsg>, tools: List<ToolDef>? = null): ChatResponse {
        val body = buildRequestBody(messages, tools)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        AppLog.put("API: 请求 $modelName, messages=${messages.size}, tools=${tools?.size ?: 0}")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startTime = System.currentTimeMillis()
        val responseBody = suspendCancellableCoroutine<String> { cont ->
            val call = client.newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            cont.resumeWithException(
                                Exception("API error ${response.code}: $errorBody")
                            )
                            return
                        }
                        val body = response.body?.string()
                            ?: throw Exception("Empty response body")
                        cont.resume(body)
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resumeWithException(e)
                        }
                    }
                }
            })
        }

        val elapsed = System.currentTimeMillis() - startTime
        AppLog.put("API: 响应耗时 ${elapsed}ms, 长度=${responseBody.length}")

        return parseResponse(responseBody)
    }

    private fun buildRequestBody(messages: List<ChatMsg>, tools: List<ToolDef>?): JsonObject {
        val body = JsonObject()
        body.addProperty("model", modelName)
        body.addProperty("temperature", 0.3)

        val messagesArray = JsonArray()
        for (msg in messages) {
            val msgObj = JsonObject()
            msgObj.addProperty("role", msg.role)
            msgObj.addProperty("content", msg.content)
            if (msg.toolCallId != null) {
                msgObj.addProperty("tool_call_id", msg.toolCallId)
            }
            if (msg.toolCalls != null) {
                val toolCallsArray = JsonArray()
                for (tc in msg.toolCalls) {
                    val tcObj = JsonObject()
                    tcObj.addProperty("id", tc.id)
                    tcObj.addProperty("type", "function")
                    val funcObj = JsonObject()
                    funcObj.addProperty("name", tc.name)
                    funcObj.addProperty("arguments", tc.arguments)
                    tcObj.add("function", funcObj)
                    toolCallsArray.add(tcObj)
                }
                msgObj.add("tool_calls", toolCallsArray)
            }
            messagesArray.add(msgObj)
        }
        body.add("messages", messagesArray)

        if (tools != null && tools.isNotEmpty()) {
            val toolsArray = JsonArray()
            for (tool in tools) {
                val toolObj = JsonObject()
                toolObj.addProperty("type", "function")
                val funcObj = JsonObject()
                funcObj.addProperty("name", tool.name)
                funcObj.addProperty("description", tool.description)
                funcObj.add("parameters", tool.parameters)
                toolObj.add("function", funcObj)
                toolsArray.add(toolObj)
            }
            body.add("tools", toolsArray)
        }

        return body
    }

    private fun parseResponse(responseBody: String): ChatResponse {
        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                throw Exception("No choices in response")
            }

            val message = choices[0].asJsonObject.getAsJsonObject("message")
                ?: throw Exception("No message in response")
            val content = message.get("content")?.let { if (it.isJsonNull) null else it.asString }
            val role = message.get("role")?.asString ?: "assistant"
            val reasoningContent = message.get("reasoning_content")?.let { if (it.isJsonNull) null else it.asString }

            var toolCalls: List<ToolCallResult>? = null
            if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull) {
                val tcArray = message.getAsJsonArray("tool_calls")
                if (tcArray != null && tcArray.size() > 0) {
                    toolCalls = tcArray.mapNotNull { tc ->
                        try {
                            val tcObj = tc.asJsonObject
                            val func = tcObj.getAsJsonObject("function")
                            ToolCallResult(
                                id = tcObj.get("id")?.asString ?: "",
                                name = func.get("name")?.asString ?: "",
                                arguments = func.get("arguments")?.asString ?: "{}"
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }

            return ChatResponse(
                content = content,
                role = role,
                toolCalls = toolCalls,
                reasoningContent = reasoningContent
            )
        } catch (e: Exception) {
            throw Exception("Failed to parse response: ${e.message}\nResponse: ${responseBody.take(500)}")
        }
    }

    data class ChatMsg(
        val role: String,
        val content: String?,
        val toolCallId: String? = null,
        val toolCalls: List<ToolCallResult>? = null
    )

    data class ChatResponse(
        val content: String?,
        val role: String,
        val toolCalls: List<ToolCallResult>?,
        val reasoningContent: String? = null
    )

    data class ToolCallResult(
        val id: String,
        val name: String,
        val arguments: String
    )

    data class ToolDef(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )
}
