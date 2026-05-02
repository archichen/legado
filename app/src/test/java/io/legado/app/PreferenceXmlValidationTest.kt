package io.legado.app

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 验证 Preference XML 中引用的自定义类在项目中实际存在，
 * 防止 InflateException 运行时崩溃（如 SeekBarPreference 不存在导致设置页闪退）。
 */
class PreferenceXmlValidationTest {

    private val prefsPackage = "io.legado.app.lib.prefs"
    private val prefsSourceDir = File("src/main/java/io/legado/app/lib/prefs")

    private val existingPrefClasses: Set<String> by lazy {
        if (!prefsSourceDir.exists()) emptySet()
        else prefsSourceDir.walk()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.nameWithoutExtension }
            .toSet()
    }

    @Test
    fun allPreferenceXmlClassesMustExist() {
        val xmlDir = File("src/main/res/xml")
        assertTrue("res/xml directory not found", xmlDir.exists())

        val prefFiles = xmlDir.listFiles { f -> f.name.startsWith("pref_") && f.extension == "xml" }
            ?: emptyArray()
        assertTrue("No preference XML files found", prefFiles.isNotEmpty())

        val errors = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (file in prefFiles) {
            val doc = builder.parse(file)
            checkNode(doc.documentElement, file.name, errors)
        }

        if (errors.isNotEmpty()) {
            fail(
                "Found ${errors.size} reference(s) to non-existent Preference classes:\n" +
                        errors.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun checkNode(node: org.w3c.dom.Node, fileName: String, errors: MutableList<String>) {
        if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            val tagName = node.nodeName
            if (tagName.startsWith("$prefsPackage.")) {
                val className = tagName.substringAfterLast(".")
                if (className !in existingPrefClasses) {
                    errors.add("$fileName: class '$tagName' does not exist (no $className.kt in lib/prefs/)")
                }
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            checkNode(children.item(i), fileName, errors)
        }
    }
}
