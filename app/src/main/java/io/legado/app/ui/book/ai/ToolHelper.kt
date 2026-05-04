package io.legado.app.ui.book.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object ToolHelper {

    data class PropDef(
        val name: String,
        val type: String,
        val desc: String,
        val required: Boolean = false
    )

    fun buildToolDef(name: String, desc: String, props: List<PropDef>): OpenAIClient.ToolDef {
        val params = JsonObject().apply {
            addProperty("type", "object")
            val propsObj = JsonObject()
            for (p in props) {
                val propObj = JsonObject().apply {
                    addProperty("type", p.type)
                    addProperty("description", p.desc)
                }
                propsObj.add(p.name, propObj)
            }
            add("properties", propsObj)
            val required = JsonArray()
            props.filter { it.required }.forEach { required.add(it.name) }
            add("required", required)
        }
        return OpenAIClient.ToolDef(name, desc, params)
    }

    fun parseArgs(arguments: String): JsonObject {
        return try {
            JsonParser.parseString(arguments).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }
    }

    fun JsonObject.str(key: String, default: String = ""): String =
        get(key)?.let { if (it.isJsonNull) default else it.asString } ?: default

    fun JsonObject.int(key: String, default: Int = 0): Int =
        get(key)?.let { if (it.isJsonNull) default else it.asInt } ?: default

    fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        get(key)?.let { if (it.isJsonNull) default else it.asBoolean } ?: default
}
