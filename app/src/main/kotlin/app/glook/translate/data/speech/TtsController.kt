package app.glook.translate.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

data class TtsState(
    val speaking: Boolean = false,
    /** 0..1, onRangeStart 를 지원하지 않는 TTS 엔진이면 null(불확정 진행). */
    val progress: Float? = null,
)

/** 원어민 발음 재생. 진행률은 UtteranceProgressListener.onRangeStart(API 26+) 로 계산. */
class TtsController(context: Context) {
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state

    private var ready = false
    private var currentLength = 1
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _state.value = TtsState(speaking = true, progress = null) }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                _state.value = TtsState(speaking = true, progress = (end.toFloat() / currentLength).coerceIn(0f, 1f))
            }
            override fun onDone(utteranceId: String?) { _state.value = TtsState() }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { _state.value = TtsState() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _state.value = TtsState() }
            override fun onError(utteranceId: String?, errorCode: Int) { _state.value = TtsState() }
        })
    }

    /** @return 해당 언어 음성이 없으면 false */
    fun speak(text: String, languageCode: String): Boolean {
        if (!ready || text.isBlank()) return false
        val result = tts.setLanguage(Locale.forLanguageTag(languageCode))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        currentLength = text.length.coerceAtLeast(1)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        return true
    }

    fun toggle(text: String, languageCode: String): Boolean =
        if (_state.value.speaking) { stop(); true } else speak(text, languageCode)

    fun stop() {
        tts.stop()
        _state.value = TtsState()
    }

    fun shutdown() = tts.shutdown()
}
