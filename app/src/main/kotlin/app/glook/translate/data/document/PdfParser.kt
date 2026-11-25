package app.glook.translate.data.document

import app.glook.translate.R
import app.glook.translate.core.str

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.glook.translate.data.ocr.OcrBlock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** PDF 문단 추출 결과. [scanned] 가 true 면 텍스트 레이어가 없어 OCR 로 읽은 것(정확도 낮음). */
class PdfResult(val paragraphs: List<String>, val pages: Int, val scanned: Boolean, val truncatedPages: Boolean)

object PdfParser {
    private const val MAX_OCR_PAGES = 30

    /** 텍스트 레이어 PDF: PDFBox(Android 포트)로 추출. 거의 없으면 PdfRenderer + ML Kit OCR 로 대체. */
    suspend fun parse(context: Context, file: File, ocr: suspend (Bitmap) -> List<OcrBlock>): PdfResult {
        PDFBoxResourceLoader.init(context)
        val extracted = try {
            PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) throw DocumentException(str(R.string.pdf_encrypted))
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val pages = doc.numberOfPages
                val text = (1..pages).joinToString("\n\n") { p -> stripper.startPage = p; stripper.endPage = p; stripper.getText(doc) }
                pages to text
            }
        } catch (e: DocumentException) { throw e
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) { throw DocumentException(str(R.string.pdf_encrypted))
        } catch (e: Exception) { null }

        if (extracted != null && extracted.second.count { !it.isWhitespace() } >= 20) {
            return PdfResult(unwrapLines(extracted.second), extracted.first, scanned = false, truncatedPages = false)
        }
        return ocrPdf(file, ocr)
    }

    private suspend fun ocrPdf(file: File, ocr: suspend (Bitmap) -> List<OcrBlock>): PdfResult {
        val out = ArrayList<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                val n = minOf(r.pageCount, MAX_OCR_PAGES)
                for (i in 0 until n) {
                    r.openPage(i).use { page ->
                        val w = 1600; val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ocr(bmp).sortedWith(compareBy({ it.box.top / 40 }, { it.box.left })).forEach { out += chunk(it.text) }
                        bmp.recycle()
                    }
                }
                if (out.isEmpty()) throw DocumentException(str(R.string.pdf_no_text))
                return PdfResult(out, r.pageCount, scanned = true, truncatedPages = r.pageCount > n)
            }
        }
    }

    /** PDF 는 줄바꿈이 문단 경계가 아니므로, 문장부호로 끝나지 않는 줄은 다음 줄과 이어 붙입니다. */
    fun unwrapLines(text: String): List<String> {
        val out = ArrayList<String>()
        for (block in text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))) {
            val cur = StringBuilder()
            for (raw in block.lines()) {
                val line = raw.trim(); if (line.isEmpty()) continue
                if (cur.isNotEmpty()) {
                    val prev = cur.last(); val cjk = isCjk(prev) || isCjk(line.first())
                    if (prev == '-' && !cjk) cur.setLength(cur.length - 1) else if (!cjk) cur.append(' ')
                }
                cur.append(line)
                if (cur.length > 700 && Regex("[.!?。！？다요]$").containsMatchIn(line)) { out += chunk(cur.toString()); cur.setLength(0) }
            }
            if (cur.isNotBlank()) out += chunk(cur.toString().trim())
        }
        return out
    }

    /** 띄어쓰기를 쓰지 않는 문자(한자·가나). 한글은 단어 사이에 공백이 있으므로 제외. */
    private fun isCjk(c: Char) = Character.UnicodeScript.of(c.code).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA
    }
}
