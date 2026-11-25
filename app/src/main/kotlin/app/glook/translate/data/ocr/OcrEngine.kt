package app.glook.translate.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrBlock(val text: String, val box: android.graphics.Rect)

/** ML Kit Text Recognition v2 (번들 모델, 온디바이스). 원문 언어에 맞는 스크립트 인식기 선택. */
class OcrEngine(private val context: Context) {
    private fun optionsFor(sourceCode: String) = when (sourceCode) {
            "ja" -> JapaneseTextRecognizerOptions.Builder().build()
            "zh" -> ChineseTextRecognizerOptions.Builder().build()
            "ko", "auto" -> KoreanTextRecognizerOptions.Builder().build() // 한글 + 라틴 문자 동시 인식
            else -> TextRecognizerOptions.DEFAULT_OPTIONS
        }

    /**
     * 이미지 위 번역 오버레이용: 블록별 텍스트 + 비트맵 좌표 경계 상자.
     * 원문 언어가 "자동"이면 한글/라틴·일본어·중국어 인식기를 모두 돌려 가장 많은 글자를 읽은 결과를 사용합니다.
     */
    suspend fun recognizeBlocks(bitmap: android.graphics.Bitmap, sourceCode: String): List<OcrBlock> {
        val codes = if (sourceCode == "auto") listOf("ko", "ja", "zh") else listOf(sourceCode)
        return codes.map { runBlocks(bitmap, it) }.maxByOrNull { blocks -> blocks.sumOf { it.text.length } }.orEmpty()
    }

    private suspend fun runBlocks(bitmap: android.graphics.Bitmap, code: String): List<OcrBlock> {
        val recognizer = TextRecognition.getClient(optionsFor(code))
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().textBlocks.mapNotNull { b ->
                b.boundingBox?.let { OcrBlock(b.text.replace('\n', ' ').trim(), it) }?.takeIf { it.text.isNotBlank() }
            }
        } finally {
            recognizer.close()
        }
    }

    suspend fun recognize(uri: Uri, sourceCode: String): String {
        val recognizer = TextRecognition.getClient(optionsFor(sourceCode))
        return try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image).await().textBlocks.joinToString("\n") { it.text }
        } finally {
            recognizer.close()
        }
    }
}
