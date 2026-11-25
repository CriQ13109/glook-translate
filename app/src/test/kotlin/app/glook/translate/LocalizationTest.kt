package app.glook.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 모든 언어 리소스가 같은 키·같은 포맷 자리표시자를 갖는지 검사. */
class LocalizationTest {
    private val resDir = File(System.getProperty("user.dir"), "src/main/res")
    private val langs = listOf("values", "values-ko", "values-ja", "values-zh-rCN", "values-es", "values-fr", "values-de", "values-pt", "values-ru", "values-vi")

    private fun load(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }

    private fun placeholders(s: String) = Regex("%\\d\\$[,0-9]*[sd]").findAll(s).map { it.value }.sorted().toList()

    @Test fun allLocalesHaveSameKeysAndPlaceholders() {
        val base = load("values")
        for (d in langs) {
            val m = load(d)
            assertEquals("$d keys", base.keys, m.keys)
            for ((k, v) in base) assertEquals("$d/$k placeholders", placeholders(v), placeholders(m.getValue(k)))
            assertTrue("$d has blank value", m.values.none { it.isBlank() })
        }
    }
}
