package app.glook.translate.data.phonetic

import android.icu.text.Transliterator
import app.glook.translate.data.engine.RubySegment

/**
 * 오프라인 발음 표기 (android.icu, API 29+).
 * - 중국어: 한자별 병음 루비 (Han-Latin). 다음자(多音字)는 문맥 없이 대표음으로 표기될 수 있음.
 * - 일본어: 가나 → 로마자 (Hiragana/Katakana-Latin). 한자 후리가나는 사전/형태소 분석이 필요하여 오프라인에서는 미지원,
 *   BYOK 클라우드 엔진이 있으면 그 결과를 사용합니다.
 * - 한국어: Hangul-Latin (ICU 규칙이며 국립국어원 로마자 표기법과 일부 다를 수 있음).
 */
object PhoneticTranscriber {
    private val hanLatin by lazy { Transliterator.getInstance("Han-Latin") }
    private val kanaLatin by lazy { Transliterator.getInstance("Hiragana-Latin; Katakana-Latin") }
    private val hangulLatin by lazy { Transliterator.getInstance("Hangul-Latin") }
    private val anyLatin by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII") }

    fun supportsPhonetics(code: String) = code in setOf("ja", "zh", "ko", "ru", "uk", "el", "th", "ar", "hi")

    fun romanize(text: String, code: String): String = when (code) {
        "zh" -> hanLatin.transliterate(text)
        "ja" -> kanaLatin.transliterate(text) // 한자는 그대로 남음
        "ko" -> hangulLatin.transliterate(text)
        else -> anyLatin.transliterate(text)
    }

    /** 오프라인 루비: 중국어는 한자 단위 병음. 그 외 언어는 null. */
    fun offlineRuby(text: String, code: String): List<RubySegment>? = when (code) {
        "zh" -> text.map { ch ->
            val s = ch.toString()
            if (Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN) RubySegment(s, hanLatin.transliterate(s).trim())
            else RubySegment(s, null)
        }
        else -> null
    }
}
