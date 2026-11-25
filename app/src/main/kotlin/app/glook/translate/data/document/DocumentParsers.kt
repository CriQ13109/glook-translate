package app.glook.translate.data.document

import app.glook.translate.R
import app.glook.translate.core.str

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

enum class DocFormat(val label: String) { TXT("TXT"), DOCX("DOCX"), HWPX("HWPX"), PDF("PDF") }

class DocumentException(message: String) : Exception(message)

/** 서식 없는 텍스트 문서에서 번역할 문단 하나의 최대 길이. */
const val MAX_PARAGRAPH_CHARS = 1500
/** 한 문서에서 처리할 최대 글자 수(초과분은 잘라내고 안내). */
const val MAX_DOC_CHARS = 120_000
const val MAX_FILE_BYTES = 25L * 1024 * 1024
private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

/** 확장자와 파일 앞부분(매직 바이트)으로 형식 판별. ZIP 이면 내부 항목으로 DOCX/HWPX 구분. */
fun sniffFormat(name: String, bytes: ByteArray): DocFormat? {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) return DocFormat.PDF
    if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
        val names = zipEntryNames(bytes)
        return when {
            "word/document.xml" in names -> DocFormat.DOCX
            names.any { it.startsWith("Contents/section") } -> DocFormat.HWPX
            else -> null
        }
    }
    return when (ext) { "txt", "md", "csv", "srt", "log", "text" -> DocFormat.TXT; else -> if (looksLikeText(bytes)) DocFormat.TXT else null }
}

private fun looksLikeText(b: ByteArray): Boolean = b.take(2048).none { it.toInt() == 0 }

private fun zipEntryNames(bytes: ByteArray): Set<String> {
    val out = HashSet<String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        var n = 0
        while (n++ < 5000) { val e = z.nextEntry ?: break; out += e.name }
    }
    return out
}

/** 압축 해제 크기를 제한하며 zip 항목을 읽습니다(zip bomb 방지). */
private fun readZipEntries(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    var total = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        var count = 0
        while (count++ < 5000) {
            val e = z.nextEntry ?: break
            if (e.isDirectory || !wanted(e.name)) continue
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = z.read(chunk); if (n < 0) break
                buf.write(chunk, 0, n); total += n
                if (buf.size() > MAX_ENTRY_BYTES || total > MAX_ENTRY_BYTES * 2) throw DocumentException(str(R.string.doc_too_big))
            }
            out[e.name] = buf.toByteArray()
        }
    }
    return out
}

object TextParser {
    /** UTF-8(BOM 허용) 으로 엄격히 디코딩해 보고 실패하면 CP949(한글 윈도우 메모장 ANSI)로 읽습니다. */
    fun decode(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b = b.copyOfRange(3, b.size)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            val cs = runCatching { Charset.forName("x-windows-949") }.getOrNull() ?: runCatching { Charset.forName("EUC-KR") }.getOrDefault(Charsets.ISO_8859_1)
            String(b, cs)
        }
    }

    fun parse(bytes: ByteArray): List<String> = splitBlocks(decode(bytes))
}

/** 빈 줄로 문단을 나누고, 너무 긴 문단은 줄·문장 경계에서 쪼갭니다. */
fun splitBlocks(text: String): List<String> {
    val out = ArrayList<String>()
    for (block in text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))) {
        val t = block.trim()
        if (t.isEmpty()) continue
        out += chunk(t)
    }
    return out
}

internal fun chunk(t: String, max: Int = MAX_PARAGRAPH_CHARS): List<String> {
    if (t.length <= max) return listOf(t)
    val out = ArrayList<String>()
    var rest = t
    while (rest.length > max) {
        val window = rest.substring(0, max)
        var cut = window.lastIndexOf('\n')
        if (cut < max / 2) cut = Regex("[.!?。！？]\\s").findAll(window).lastOrNull()?.range?.last ?: -1
        if (cut < max / 2) cut = window.lastIndexOf(' ')
        if (cut < max / 2) cut = max - 1
        out += rest.substring(0, cut + 1).trim()
        rest = rest.substring(cut + 1)
    }
    if (rest.isNotBlank()) out += rest.trim()
    return out.filter { it.isNotEmpty() }
}

/** DOCX: word/document.xml 의 w:p(문단) 안 w:t(글자)·w:tab·w:br 을 순서대로 읽습니다. 머리글·각주는 제외. */
object DocxParser {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    fun parse(bytes: ByteArray): List<String> {
        val entry = readZipEntries(bytes) { it == "word/document.xml" }["word/document.xml"]
            ?: throw DocumentException(str(R.string.docx_no_body))
        return parseXml(ByteArrayInputStream(entry), textTag = "t", paraTag = "p", ns = W, breakTags = setOf("br", "cr"), tabTag = "tab")
    }
}

/** HWPX: Contents/section*.xml 의 hp:p / hp:t (접두사 무시, 표 안의 중첩 문단 포함). */
object HwpxParser {
    fun parse(bytes: ByteArray): List<String> {
        val sections = readZipEntries(bytes) { Regex("Contents/section\\d+\\.xml").matches(it) }
        if (sections.isEmpty()) throw DocumentException(str(R.string.hwpx_no_body))
        val ordered = sections.entries.sortedBy { Regex("\\d+").find(it.key.substringAfterLast('/'))!!.value.toInt() }
        return ordered.flatMap { parseXml(ByteArrayInputStream(it.value), textTag = "t", paraTag = "p", ns = null, breakTags = setOf("lineBreak"), tabTag = "tab") }
    }
}

private fun parseXml(input: InputStream, textTag: String, paraTag: String, ns: String?, breakTags: Set<String>, tabTag: String): List<String> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, ns != null)
    parser.setInput(input, null)
    val out = ArrayList<String>()
    val stack = ArrayList<StringBuilder>() // 중첩 문단(표 셀, 텍스트 상자) 대응
    var inText = 0
    fun matches(name: String?, tag: String, namespace: String?) =
        (name == tag || name?.substringAfter(':') == tag) && (ns == null || namespace == ns)
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> {
                val n = parser.name; val u = parser.namespace
                when {
                    matches(n, paraTag, u) -> stack += StringBuilder()
                    matches(n, textTag, u) && stack.isNotEmpty() -> inText++
                    matches(n, tabTag, u) && stack.isNotEmpty() -> stack.last().append('\t')
                    breakTags.any { matches(n, it, u) } && stack.isNotEmpty() -> stack.last().append('\n')
                }
            }
            XmlPullParser.TEXT -> if (inText > 0 && stack.isNotEmpty()) stack.last().append(parser.text)
            XmlPullParser.END_TAG -> {
                val n = parser.name; val u = parser.namespace
                when {
                    matches(n, textTag, u) && inText > 0 -> inText--
                    matches(n, paraTag, u) && stack.isNotEmpty() -> {
                        val t = stack.removeAt(stack.lastIndex).toString().trim()
                        if (t.isNotEmpty()) out += chunk(t)
                    }
                }
            }
        }
        ev = parser.next()
    }
    return out
}
