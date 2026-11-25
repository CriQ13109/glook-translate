package app.glook.translate.ui.common

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.glook.translate.GlookApp
import app.glook.translate.data.speech.SpeechInputController
import app.glook.translate.data.speech.SpeechState
import app.glook.translate.data.speech.TtsState
import app.glook.translate.domain.model.AppSettings
import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranslatorUiState(
    val input: String = "",
    val source: Language = Language.Auto,
    val target: Language = Language.of("ko"),
    val detected: Language? = null,
    val output: String = "",
    val engine: EngineKind? = null,
    val streaming: Boolean = false,
    val fellBack: Boolean = false,
    val error: String? = null,
    val bookmarked: Boolean = false,
    val ocrRunning: Boolean = false,
    val ready: Boolean = false,
    val recents: List<Language> = emptyList(),
    val clipboardOffer: Boolean = false,
) {
    /** 실제 원문 언어 (자동 감지 해소). */
    val effectiveSource: Language get() = if (source.isAuto) detected ?: source else source
}

/**
 * 메인 화면과 PROCESS_TEXT 바텀시트가 공유하는 번역 세션 ViewModel (MVI 스타일: 단일 UiState + intent 메서드).
 *
 * 입력창의 진실 공급원은 UI 의 TextFieldState 입니다. 이 ViewModel 은 입력 텍스트를 "받기만" 하고,
 * 외부(공유·OCR·음성·보관함)에서 들어온 텍스트만 [setText] 이벤트로 UI 에 "밀어 넣습니다".
 * (예전에는 UiState.input 을 다시 TextField 로 되돌려 써서, 천지인 같은 10키 IME 의 조합 중에
 *  입력이 덮어써지며 글자가 사라지는 버그가 있었습니다.)
 */
@OptIn(FlowPreview::class)
class TranslatorViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    private val speech = SpeechInputController(app)

    private val _state = MutableStateFlow(TranslatorUiState())
    val state: StateFlow<TranslatorUiState> = _state
    val tts: StateFlow<TtsState> = c.tts.state
    val speechState: StateFlow<SpeechState> = speech.state
    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val secureScreen: StateFlow<Boolean> = settings.map { it.secureScreen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _setText = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** UI 의 입력창에 텍스트를 밀어 넣으라는 이벤트(외부 유입 전용). */
    val setText: SharedFlow<String> = _setText.asSharedFlow()

    private val input = MutableStateFlow("")
    private val langs = MutableStateFlow(Language.Auto to Language.of("ko"))
    @Volatile private var immediateKey: Triple<String, Language, Language>? = null
    private var handledClipTimestamp = 0L

    init {
        viewModelScope.launch {
            val s = runCatching { c.settings.settings.first() }.getOrNull()
            if (s != null) {
                langs.value = Language.of(s.sourceLang) to Language.of(s.targetLang)
                _state.update { it.copy(source = langs.value.first, target = langs.value.second) }
            }
            _state.update { it.copy(ready = true) }
        }
        viewModelScope.launch {
            settings.collect { s -> _state.update { it.copy(recents = s.recentLangs.map(Language::of)) } }
        }
        viewModelScope.launch {
            // 250ms 디바운스 후, 새 입력이 오면 진행 중인 번역(스트림 포함)을 즉시 취소(collectLatest)
            combine(input.debounce(DEBOUNCE_MS), langs) { text, l -> Triple(text, l.first, l.second) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == immediateKey) return@collectLatest // 즉시 번역으로 이미 처리됨
                    runTranslation(key.first, key.second, key.third)
                }
        }
    }

    private suspend fun runTranslation(text: String, src: Language, tgt: Language) {
        if (text.isBlank()) {
            _state.update { it.copy(output = "", engine = null, streaming = false, error = null, detected = null, bookmarked = false) }
            return
        }
        _state.update { it.copy(streaming = true, error = null) }
        c.router.translate(TranslationRequest(text, src, tgt)).collect { u ->
            when (u) {
                is TranslationUpdate.Detected -> _state.update { it.copy(detected = u.source) }
                is TranslationUpdate.Retargeted -> _state.update { it.copy(target = u.target) }
                is TranslationUpdate.Partial -> _state.update { it.copy(output = u.text, engine = u.engine, streaming = true) }
                is TranslationUpdate.Final -> {
                    val target = _state.value.target
                    _state.update { it.copy(output = u.text, engine = u.engine, streaming = false, fellBack = u.fellBack) }
                    runCatching {
                        c.library.addHistory(text.trim(), u.text, u.source.code, target.code, u.engine.name)
                        val bm = c.library.isBookmarked(text.trim(), u.text)
                        _state.update { it.copy(bookmarked = bm) }
                    }
                    afterFinal(u.text, target)
                }
                is TranslationUpdate.Failed -> _state.update { it.copy(error = u.message, streaming = false) }
            }
        }
    }

    private fun afterFinal(translated: String, target: Language) {
        val s = settings.value
        if (s.autoCopy) copyText(translated)
        if (s.autoSpeak) c.tts.speak(translated, target.code)
    }

    private fun copyText(text: String) {
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Glook", text))
        // 내가 복사한 것이므로 클립보드 제안 대상에서 제외
        cm.primaryClipDescription?.timestamp?.let { handledClipTimestamp = it }
    }

    // ---------- intents ----------
    /** 입력창(TextFieldState)에서 올라오는 텍스트. */
    fun onInput(text: String) {
        _state.update { it.copy(input = text) }
        input.value = text
    }

    /** 외부(공유, PROCESS_TEXT, OCR, 음성, 보관함)에서 들어온 텍스트는 입력창에 반영하고 디바운스 없이 즉시 번역. */
    fun submitImmediately(text: String) {
        _setText.tryEmit(text)
        val (src, tgt) = langs.value
        immediateKey = Triple(text, src, tgt)
        onInput(text)
        viewModelScope.launch { runTranslation(text, src, tgt) }
    }

    fun setSource(lang: Language) = setLangs(
        lang,
        _state.value.target.takeIf { it.code != lang.code } ?: _state.value.source.takeUnless { it.isAuto } ?: Language.of("en"),
    )

    fun setTarget(lang: Language) = setLangs(_state.value.source.takeIf { it.code != lang.code } ?: Language.Auto, lang)

    fun swap() {
        val s = _state.value
        val newSource = s.target
        val newTarget = s.effectiveSource.takeUnless { it.isAuto } ?: Language.of("en")
        val newInput = s.output.ifBlank { s.input }
        setLangs(newSource, newTarget)
        if (newInput.isNotBlank()) submitImmediately(newInput)
    }

    private fun setLangs(src: Language, tgt: Language) {
        langs.value = src to tgt
        _state.update { it.copy(source = src, target = tgt, detected = if (src.isAuto) it.detected else null) }
        viewModelScope.launch {
            runCatching {
                c.settings.update { cur ->
                    val recents = (listOf(tgt.code, src.code).filter { it != Language.AUTO_CODE } + cur.recentLangs).distinct().take(5)
                    cur.copy(sourceLang = src.code, targetLang = tgt.code, recentLangs = recents)
                }
            }
        }
    }

    fun clear() {
        _setText.tryEmit("")
        onInput("")
    }

    fun toggleBookmark() {
        val s = _state.value
        if (s.output.isBlank()) return
        viewModelScope.launch {
            val now = c.library.toggleBookmark(s.input.trim(), s.output, s.effectiveSource.code, s.target.code)
            _state.update { it.copy(bookmarked = now) }
        }
    }

    fun toggleSpeak(): Boolean = c.tts.toggle(_state.value.output, _state.value.target.code)
    fun speakSource(): Boolean = c.tts.toggle(_state.value.input, _state.value.effectiveSource.code)

    fun startVoice() {
        val code = _state.value.source.takeUnless { it.isAuto }?.code ?: "ko"
        speech.start(code) { submitImmediately(it) }
    }

    fun stopVoice() = speech.stop()

    fun recognizeImage(uri: Uri, onDone: () -> Unit = {}) {
        _state.update { it.copy(ocrRunning = true) }
        viewModelScope.launch {
            val text = runCatching { c.ocr.recognize(uri, _state.value.source.code) }.getOrDefault("")
            _state.update { it.copy(ocrRunning = false, error = if (text.isBlank()) str(R.string.image_no_text) else null) }
            if (text.isNotBlank()) submitImmediately(text)
            onDone()
        }
    }

    // ---------- 클립보드 제안 ----------
    /**
     * 앱이 포그라운드로 올 때 호출. 클립보드 "내용"은 읽지 않고 설명(MIME·타임스탬프)만 확인하므로
     * Android 12+ 의 "클립보드 접근" 토스트가 뜨지 않고, 사용자가 제안을 누르기 전에는 내용이 앱에 들어오지 않습니다.
     */
    fun checkClipboard() {
        if (!settings.value.clipboardSuggest || _state.value.input.isNotBlank()) {
            _state.update { it.copy(clipboardOffer = false) }
            return
        }
        val desc = getApplication<Application>().getSystemService(ClipboardManager::class.java).primaryClipDescription
        val hasText = desc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true || desc?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
        val ts = desc?.timestamp ?: 0L
        _state.update { it.copy(clipboardOffer = hasText && ts != handledClipTimestamp) }
    }

    fun acceptClipboard() {
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        handledClipTimestamp = cm.primaryClipDescription?.timestamp ?: 0L
        _state.update { it.copy(clipboardOffer = false) }
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(getApplication())?.toString()
        if (!text.isNullOrBlank()) submitImmediately(text)
    }

    fun dismissClipboard() {
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        handledClipTimestamp = cm.primaryClipDescription?.timestamp ?: 0L
        _state.update { it.copy(clipboardOffer = false) }
    }

    override fun onCleared() {
        speech.stop()
        c.tts.stop()
    }

    companion object {
        const val DEBOUNCE_MS = 250L
    }
}
