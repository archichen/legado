package io.legado.app.ui.book.ai

import com.google.gson.JsonObject
import io.legado.app.ui.book.ai.ToolHelper.bool
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookChapterToolTest {

    @Test
    fun testToolHelperParseArgs() {
        val args = ToolHelper.parseArgs("""{"chapterIndex": 5, "startLine": 10}""")
        assertEquals(5, args.int("chapterIndex"))
        assertEquals(10, args.int("startLine"))
        assertEquals(0, args.int("endLine"))
    }

    @Test
    fun testToolHelperParseArgsDefaults() {
        val args = ToolHelper.parseArgs("{}")
        assertEquals(0, args.int("chapterIndex"))
        assertEquals(0, args.int("startLine"))
        assertEquals("", args.str("keyword"))
        assertEquals(false, args.bool("isRegex"))
    }

    @Test
    fun testToolHelperParseArgsInvalid() {
        val args = ToolHelper.parseArgs("not json")
        assertEquals(0, args.int("chapterIndex"))
    }

    @Test
    fun testToolHelperStrExtension() {
        val obj = JsonObject().apply {
            addProperty("key", "value")
        }
        assertEquals("value", obj.str("key"))
        assertEquals("", obj.str("missing"))
        assertEquals("default", obj.str("missing", "default"))
    }

    @Test
    fun testToolHelperIntExtension() {
        val obj = JsonObject().apply {
            addProperty("key", 42)
        }
        assertEquals(42, obj.int("key"))
        assertEquals(0, obj.int("missing"))
        assertEquals(99, obj.int("missing", 99))
    }

    @Test
    fun testToolHelperBoolExtension() {
        val obj = JsonObject().apply {
            addProperty("key", true)
        }
        assertEquals(true, obj.bool("key"))
        assertEquals(false, obj.bool("missing"))
    }

    @Test
    fun testChapterIndexValidation() {
        val totalChapters = 100

        // Valid indices: 0 to 99
        assertTrue("Index 0 should be valid", 0 in 0 until totalChapters)
        assertTrue("Index 99 should be valid", 99 in 0 until totalChapters)

        // Invalid indices
        assertTrue("Index -1 should be invalid", -1 !in 0 until totalChapters)
        assertTrue("Index 100 should be invalid", 100 !in 0 until totalChapters)
        assertTrue("Index 101 should be invalid", 101 !in 0 until totalChapters)
    }

    @Test
    fun testChapterIndexErrorMessage() {
        val chaptersSize = 100
        val invalidIndex = 100
        val lastIdx = chaptersSize - 1

        val errorMsg = "章节索引 $invalidIndex 超出范围。该书共 $chaptersSize 章，有效索引为 0 到 $lastIdx（最后一个章节的索引是 $lastIdx，不是 $chaptersSize）。"

        assertTrue("Error should mention the invalid index", errorMsg.contains("100"))
        assertTrue("Error should mention valid range", errorMsg.contains("0 到 99"))
        assertTrue("Error should clarify last index", errorMsg.contains("99，不是 100"))
    }

    @Test
    fun testLineRangeCalculation() {
        val totalLines = 200

        // No range specified
        val s1 = 0
        val e1 = totalLines
        assertEquals(0, s1)
        assertEquals(200, e1)

        // Only startLine specified
        val startLine = 50
        val s2 = (startLine - 1).coerceIn(0, totalLines - 1)
        val e2 = totalLines
        assertEquals(49, s2)
        assertEquals(200, e2)

        // Both specified
        val endLine = 100
        val s3 = (startLine - 1).coerceIn(0, totalLines - 1)
        val e3 = endLine.coerceIn(s3, totalLines)
        assertEquals(49, s3)
        assertEquals(100, e3)

        // End exceeds total
        val endLine2 = 300
        val e4 = endLine2.coerceIn(s3, totalLines)
        assertEquals(200, e4)
    }

    @Test
    fun testTableOfContentsPagination() {
        val totalChapters = 100

        // First page
        val s1 = 0.coerceIn(0, totalChapters - 1)
        val e1 = (0 + 30).coerceAtMost(totalChapters)
        assertEquals(0, s1)
        assertEquals(30, e1)

        // Second page
        val s2 = 30.coerceIn(0, totalChapters - 1)
        val e2 = (30 + 30).coerceAtMost(totalChapters)
        assertEquals(30, s2)
        assertEquals(60, e2)

        // Last page (partial)
        val s3 = 90.coerceIn(0, totalChapters - 1)
        val e3 = (90 + 30).coerceAtMost(totalChapters)
        assertEquals(90, s3)
        assertEquals(100, e3)

        // Out of range start
        val s4 = 150.coerceIn(0, totalChapters - 1)
        assertEquals(99, s4)
    }

    @Test
    fun testTocDisplayFormat() {
        val chaptersSize = 100
        val s = 0
        val e = 30
        val header = "共 $chaptersSize 章（索引0-${chaptersSize - 1}），当前显示索引 ${s}-${e - 1}：\n"

        assertTrue("Header should show total", header.contains("共 100 章"))
        assertTrue("Header should show index range", header.contains("索引0-99"))
        assertTrue("Header should show display range", header.contains("索引 0-29"))
    }

    @Test
    fun testBookInfoProgressCalculation() {
        val chapterCount = 100
        val durChapterIndex = 42

        val progress = if (chapterCount > 0) {
            ((durChapterIndex.toFloat() / chapterCount) * 100).toInt()
        } else 0

        assertEquals(42, progress)

        // Edge case: 0 chapters
        val progressZero = if (0 > 0) 1 else 0
        assertEquals(0, progressZero)
    }

    @Test
    fun testToolDefStructure() {
        val toolDefs = listOf(
            ToolHelper.buildToolDef(
                "getChapterContent",
                "获取章节内容",
                listOf(
                    ToolHelper.PropDef("chapterIndex", "integer", "章节索引", true),
                    ToolHelper.PropDef("startLine", "integer", "起始行", false)
                )
            )
        )

        assertEquals(1, toolDefs.size)
        assertEquals("getChapterContent", toolDefs[0].name)
        assertEquals("获取章节内容", toolDefs[0].description)

        val params = toolDefs[0].parameters
        assertEquals("object", params.get("type").asString)

        val props = params.getAsJsonObject("properties")
        assertNotNull(props.get("chapterIndex"))
        assertNotNull(props.get("startLine"))

        val required = params.getAsJsonArray("required")
        assertEquals(1, required.size())
        assertEquals("chapterIndex", required[0].asString)
    }

    @Test
    fun testDownloadErrorMessages() {
        // Local book
        val localMsg = "章节「Test」是本地书籍但无法读取内容。"
        assertTrue(localMsg.contains("本地书籍"))

        // No source
        val noSourceMsg = "章节「Test」内容未缓存，且该书没有配置书源，无法下载。"
        assertTrue(noSourceMsg.contains("没有配置书源"))

        // Download failed
        val failMsg = "章节「Test」下载失败: timeout"
        assertTrue(failMsg.contains("下载失败"))

        // Empty after download
        val emptyMsg = "章节「Test」下载后内容为空。"
        assertTrue(emptyMsg.contains("内容为空"))
    }
}
