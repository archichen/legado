package io.legado.app.ui.book.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import io.legado.app.ui.book.ai.ToolHelper.bool
import io.legado.app.ui.book.ai.ToolHelper.int
import io.legado.app.ui.book.ai.ToolHelper.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolHelperAndroidTest {

    @Test
    fun testBuildToolDef() {
        val toolDef = ToolHelper.buildToolDef(
            "testTool",
            "A test tool",
            listOf(
                ToolHelper.PropDef("param1", "string", "First parameter", true),
                ToolHelper.PropDef("param2", "integer", "Second parameter", false)
            )
        )

        assertEquals("testTool", toolDef.name)
        assertEquals("A test tool", toolDef.description)
        assertNotNull(toolDef.parameters)

        val params = toolDef.parameters
        assertEquals("object", params.get("type").asString)

        val props = params.getAsJsonObject("properties")
        assertNotNull(props.get("param1"))
        assertNotNull(props.get("param2"))

        val required = params.getAsJsonArray("required")
        assertEquals(1, required.size())
        assertEquals("param1", required[0].asString)
    }

    @Test
    fun testParseArgs() {
        val args = ToolHelper.parseArgs("""{"key1": "value1", "key2": 42}""")
        assertEquals("value1", args.str("key1"))
        assertEquals(42, args.int("key2"))
    }

    @Test
    fun testParseArgsInvalid() {
        val args = ToolHelper.parseArgs("not json")
        assertEquals("", args.str("key"))
        assertEquals(0, args.int("key"))
    }

    @Test
    fun testParseArgsEmpty() {
        val args = ToolHelper.parseArgs("{}")
        assertEquals("", args.str("key"))
        assertEquals(0, args.int("key"))
        assertEquals(false, args.bool("key"))
    }

    @Test
    fun testJsonObjectExtensions() {
        val obj = JsonObject().apply {
            addProperty("str", "hello")
            addProperty("int", 42)
            addProperty("bool", true)
        }

        assertEquals("hello", obj.str("str"))
        assertEquals(42, obj.int("int"))
        assertEquals(true, obj.bool("bool"))
        assertEquals("default", obj.str("missing", "default"))
        assertEquals(99, obj.int("missing", 99))
    }

    @Test
    fun testBuildToolDefWithNoParams() {
        val toolDef = ToolHelper.buildToolDef(
            "simpleTool",
            "A simple tool",
            emptyList()
        )

        assertEquals("simpleTool", toolDef.name)
        val required = toolDef.parameters.getAsJsonArray("required")
        assertEquals(0, required.size())
    }

    @Test
    fun testBuildToolDefWithAllRequired() {
        val toolDef = ToolHelper.buildToolDef(
            "allRequired",
            "All params required",
            listOf(
                ToolHelper.PropDef("a", "string", "Param A", true),
                ToolHelper.PropDef("b", "string", "Param B", true)
            )
        )

        val required = toolDef.parameters.getAsJsonArray("required")
        assertEquals(2, required.size())
    }
}
