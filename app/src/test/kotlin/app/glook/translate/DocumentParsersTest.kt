package app.glook.translate

import androidx.test.core.app.ApplicationProvider
import app.glook.translate.data.document.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentParsersTest {
    private fun zip(vararg e: Pair<String, String>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> e.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return b.toByteArray()
    }

    @Test fun txtUtf8AndCp949() {
        assertEquals(listOf("안녕하세요", "두 번째 문단"), TextParser.parse("안녕하세요\n\n\n두 번째 문단\n".toByteArray()))
        val cp949 = "한글 메모장 문서".toByteArray(java.nio.charset.Charset.forName("x-windows-949"))
        assertEquals("한글 메모장 문서", TextParser.decode(cp949))
    }

    @Test fun txtLongParagraphIsSplit() {
        val long = (1..400).joinToString(" ") { "문장 $it 입니다." }
        val parts = splitBlocks(long)
        assertTrue(parts.size > 1 && parts.all { it.length <= MAX_PARAGRAPH_CHARS })
    }

    @Test fun docxParagraphsTabsAndTables() {
        val xml = """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:t xml:space="preserve">world</w:t></w:r></w:p>
            <w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>셀1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>셀2</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p/></w:body></w:document>"""
        val bytes = zip("word/document.xml" to xml, "[Content_Types].xml" to "<x/>")
        assertEquals(DocFormat.DOCX, sniffFormat("a.bin", bytes))
        assertEquals(listOf("Hello world", "a\tb", "셀1", "셀2"), DocxParser.parse(bytes))
    }

    @Test fun hwpxSectionsOrderedAndNestedParagraphs() {
        fun sec(t: String) = """<?xml version="1.0"?><hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section" xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
            <hp:p><hp:run><hp:t>$t</hp:t></hp:run></hp:p></hs:sec>"""
        val tbl = """<hs:sec xmlns:hs="s" xmlns:hp="p"><hp:p><hp:run><hp:t>표 앞</hp:t><hp:tbl><hp:tr><hp:tc><hp:subList><hp:p><hp:run><hp:t>셀</hp:t></hp:run></hp:p></hp:subList></hp:tc></hp:tr></hp:tbl></hp:run></hp:p></hs:sec>"""
        val bytes = zip("Contents/section10.xml" to sec("열번째"), "Contents/section2.xml" to tbl, "Contents/section0.xml" to sec("첫째"), "mimetype" to "application/hwp+zip")
        assertEquals(DocFormat.HWPX, sniffFormat("x.hwpx", bytes))
        assertEquals(listOf("첫째", "셀", "표 앞", "열번째"), HwpxParser.parse(bytes))
    }

    @Test fun pdfUnwrapJoinsLines() {
        assertEquals(listOf("This is a wrapped sentence that continues. Next one."), PdfParser.unwrapLines("This is a wrapped sentence\nthat continues. Next\none."))
        assertEquals(listOf("한국어 문장은 줄바꿈을 이어 붙입니다"), PdfParser.unwrapLines("한국어 문장은\n줄바꿈을 이어\n붙입니다"))
        assertEquals(listOf("日本語の文章は改行をつなげる"), PdfParser.unwrapLines("日本語の文章は\n改行を\nつなげる"))
    }

    @Test fun pdfTextLayerExtraction() = runBlocking {
        val body = "BT /F1 18 Tf 72 700 Td (Hello Glook document translation test.) Tj ET"
        val objs = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${body.length} >>\nstream\n$body\nendstream", "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val sb = StringBuilder("%PDF-1.4\n"); val offs = ArrayList<Int>()
        objs.forEachIndexed { i, o -> offs += sb.length; sb.append("${i + 1} 0 obj\n$o\nendobj\n") }
        val xref = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n"); offs.forEach { sb.append("%010d 00000 n \n".format(it)) }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        val f = File.createTempFile("glook", ".pdf").apply { writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1)); deleteOnExit() }
        assertEquals(DocFormat.PDF, sniffFormat("a", f.readBytes()))
        val r = PdfParser.parse(ApplicationProvider.getApplicationContext(), f) { emptyList() }
        assertFalse(r.scanned)
        assertTrue(r.paragraphs.joinToString(" "), r.paragraphs.joinToString(" ").contains("Hello Glook document translation test."))
    }
}
