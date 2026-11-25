package app.glook.translate.data.document

import app.glook.translate.R
import app.glook.translate.core.str

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.glook.translate.data.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ParsedDocument(val name: String, val format: DocFormat, val paragraphs: List<String>, val note: String? = null)

/** 문서는 메모리/캐시에서만 처리하며 처리 후 임시 파일을 삭제합니다(DB·저장소에 원문을 남기지 않음). */
class DocumentReader(private val context: Context, private val ocr: OcrEngine) {

    suspend fun read(uri: Uri, sourceCode: String): ParsedDocument = withContext(Dispatchers.IO) {
        val (name, size) = queryMeta(uri)
        if (size != null && size > MAX_FILE_BYTES) throw DocumentException(str(R.string.file_too_big_fmt, (MAX_FILE_BYTES / 1024 / 1024).toInt()))
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024); var total = 0L
            while (true) {
                val n = input.read(chunk); if (n < 0) break
                total += n; if (total > MAX_FILE_BYTES) throw DocumentException(str(R.string.file_too_big_fmt, (MAX_FILE_BYTES / 1024 / 1024).toInt()))
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        } ?: throw DocumentException(str(R.string.file_open_fail))
        val format = sniffFormat(name, bytes) ?: throw DocumentException(str(R.string.unsupported_format))

        var note: String? = null
        val paragraphs: List<String> = try {
            when (format) {
                DocFormat.TXT -> TextParser.parse(bytes)
                DocFormat.DOCX -> DocxParser.parse(bytes)
                DocFormat.HWPX -> HwpxParser.parse(bytes)
                DocFormat.PDF -> {
                    val tmp = File(File(context.cacheDir, "doc").apply { mkdirs() }, "in.pdf")
                    try {
                        tmp.writeBytes(bytes)
                        val r = PdfParser.parse(context, tmp) { bmp -> ocr.recognizeBlocks(bmp, sourceCode) }
                        if (r.scanned) note = str(R.string.scanned_pdf_ocr) + if (r.truncatedPages) str(R.string.first_30_pages) else ""
                        r.paragraphs
                    } finally { tmp.delete() }
                }
            }
        } catch (e: DocumentException) { throw e
        } catch (e: org.xmlpull.v1.XmlPullParserException) { throw DocumentException(str(R.string.doc_corrupt_mismatch))
        } catch (e: java.util.zip.ZipException) { throw DocumentException(str(R.string.doc_corrupt))
        }
        if (paragraphs.isEmpty()) throw DocumentException(str(R.string.doc_no_text))
        val (limited, truncated) = limit(paragraphs)
        if (truncated) note = listOfNotNull(note, str(R.string.doc_truncated_fmt, MAX_DOC_CHARS)).joinToString(" · ")
        ParsedDocument(name, format, limited, note)
    }

    private fun limit(p: List<String>): Pair<List<String>, Boolean> {
        var sum = 0; val out = ArrayList<String>()
        for (t in p) { if (sum + t.length > MAX_DOC_CHARS) return out to true; sum += t.length; out += t }
        return out to false
    }

    private fun queryMeta(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment ?: str(R.string.tile_document); var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        return name to size
    }
}
