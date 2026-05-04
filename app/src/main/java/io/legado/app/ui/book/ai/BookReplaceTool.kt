package io.legado.app.ui.book.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book

class BookReplaceTool(private val book: Book) {

    fun getToolDefs(): List<OpenAIClient.ToolDef> = listOf(
        ToolHelper.buildToolDef(
            "getReplaceRules",
            "获取当前书籍生效的内容替换/净化规则列表。这些规则用于过滤广告、修正错字等。",
            emptyList()
        )
    )

    fun executeTool(name: String, arguments: String): String {
        return when (name) {
            "getReplaceRules" -> getReplaceRules()
            else -> "未知工具: $name"
        }
    }

    private fun getReplaceRules(): String {
        val rules = appDb.replaceRuleDao.findEnabledByContentScope(book.name, book.origin)

        if (rules.isEmpty()) {
            return "该书暂无生效的替换规则。"
        }

        val header = "当前生效的替换规则（共 ${rules.size} 条）：\n"
        val body = rules.joinToString("\n---\n") { rule ->
            val type = if (rule.isRegex) "正则" else "文本"
            "规则: ${rule.name.ifEmpty { "未命名" }}（$type）\n匹配: ${rule.pattern}\n替换: ${rule.replacement}"
        }
        return header + body
    }
}
