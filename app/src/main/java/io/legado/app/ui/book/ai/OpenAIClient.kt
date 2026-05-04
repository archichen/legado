package io.legado.app.ui.book.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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

    fun chat(messages: List<ChatMsg>, tools: List<ToolDef>? = null): ChatResponse {
        val body = buildRequestBody(messages, tools)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $responseBody")
        }

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
        val json = JsonParser.parseString(responseBody).asJsonObject
        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw Exception("No choices in response: $responseBody")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val content = message.get("content")?.asString
        val role = message.get("role")?.asString ?: "assistant"

        var toolCalls: List<ToolCallResult>? = null
        if (message.has("tool_calls")) {
            val tcArray = message.getAsJsonArray("tool_calls")
            toolCalls = tcArray.map { tc ->
                val tcObj = tc.asJsonObject
                val func = tcObj.getAsJsonObject("function")
                ToolCallResult(
                    id = tcObj.get("id").asString,
                    name = func.get("name").asString,
                    arguments = func.get("arguments").asString
                )
            }
        }

        return ChatResponse(content = content, role = role, toolCalls = toolCalls)
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
        val toolCalls: List<ToolCallResult>?
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
