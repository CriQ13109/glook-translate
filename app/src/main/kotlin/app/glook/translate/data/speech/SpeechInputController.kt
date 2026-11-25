package app.glook.translate.data.speech

import app.glook.translate.R
import app.glook.translate.core.str

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpeechState(val listening: Boolean = false, val partial: String = "", val error: String? = null)

/**
 * 음성 입력. Android 12+ 에서 온디바이스 인식기가 있으면 createOnDeviceSpeechRecognizer 사용(오디오가 기기를 떠나지 않음).
 * 없으면 시스템 기본 인식기 + EXTRA_PREFER_OFFLINE (기기/인식 서비스에 따라 서버 인식이 될 수 있음).
 * 메인 스레드에서만 호출해야 합니다.
 */
class SpeechInputController(private val context: Context) {
    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state
    private var recognizer: SpeechRecognizer? = null

    val isOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(languageCode: String?, onFinal: (String) -> Unit) = begin(languageCode, onFinal, preferOnDevice = true)

    private fun begin(languageCode: String?, onFinal: (String) -> Unit, preferOnDevice: Boolean) {
        stop()
        val onDevice = preferOnDevice && isOnDevice
        val r = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = SpeechState(listening = true) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) {
                // 12=LANGUAGE_NOT_SUPPORTED, 13=LANGUAGE_UNAVAILABLE: 온디바이스 인식기에 해당 언어 모델이 없음 → 시스템 인식기로 재시도
                if (onDevice && (error == 12 || error == 13)) {
                    recognizer?.destroy(); recognizer = null
                    begin(languageCode, onFinal, preferOnDevice = false)
                    return
                }
                _state.value = SpeechState(error = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> str(R.string.speech_not_recognized)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> str(R.string.mic_permission)
                    12, 13 -> str(R.string.speech_lang_unsupported_fmt, error)
                    else -> str(R.string.speech_error_fmt, error)
                })
                stop()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = _state.value.copy(partial = t)
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = SpeechState()
                if (t.isNotBlank()) onFinal(t)
                stop()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
            if (languageCode != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag(languageCode))
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
        _state.value = _state.value.copy(listening = false)
    }

    private fun speechTag(code: String): String = when (code) {
        "ko" -> "ko-KR"; "en" -> "en-US"; "ja" -> "ja-JP"; "zh" -> "zh-CN"; "es" -> "es-ES"; "fr" -> "fr-FR"
        "de" -> "de-DE"; "it" -> "it-IT"; "pt" -> "pt-BR"; "ru" -> "ru-RU"; "vi" -> "vi-VN"; "th" -> "th-TH"
        "id" -> "id-ID"; "ms" -> "ms-MY"; "ar" -> "ar-SA"; "hi" -> "hi-IN"; "tr" -> "tr-TR"; "nl" -> "nl-NL"
        "pl" -> "pl-PL"; "uk" -> "uk-UA"; "sv" -> "sv-SE"; "cs" -> "cs-CZ"; "el" -> "el-GR"
        else -> code
    }
}
