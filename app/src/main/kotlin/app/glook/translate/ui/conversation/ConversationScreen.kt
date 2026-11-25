package app.glook.translate.ui.conversation

import app.glook.translate.R
import app.glook.translate.core.str

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.GlookApp
import app.glook.translate.data.speech.SpeechInputController
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.components.LanguageButton
import app.glook.translate.ui.components.LanguagePickerSheet
import app.glook.translate.ui.components.MorphMicButton
import app.glook.translate.ui.components.MorphSwapButton
import app.glook.translate.ui.theme.GlookShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Side { ME, OTHER }

data class ConvTurn(val speaker: Side, val original: String, val translated: String)

data class ConvState(
    val me: Language = Language.of("ko"),
    val other: Language = Language.of("en"),
    val listening: Side? = null,
    val partial: String = "",
    val busy: Boolean = false,
    val turn: ConvTurn? = null,
    val error: String? = null,
) {
    fun lang(side: Side) = if (side == Side.ME) me else other
    /** 해당 쪽 사람이 읽을 텍스트(자기 언어). */
    fun textFor(side: Side): String? = turn?.let { if (it.speaker == side) it.original else it.translated }
}

/**
 * 대화 모드: 폰을 가운데 두고 마주 앉은 두 사람이 각자 마이크를 누르면 상대 언어로 통역해 상대 쪽 화면에 크게 보여 주고
 * 번역문을 읽어 줍니다. 화법 자동 매칭·스트리밍은 일반 번역과 같은 라우터를 사용합니다.
 */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    private val speech = SpeechInputController(app)
    private val _state = MutableStateFlow(ConvState())
    val state: StateFlow<ConvState> = _state
    private var job: Job? = null

    init {
        // 인식기 상태(부분 결과/오류)를 UI 상태로 반영 — 컬렉터는 하나만 둡니다.
        viewModelScope.launch {
            speech.state.collect { st ->
                _state.update { cur ->
                    when {
                        cur.listening == null -> cur
                        st.error != null -> cur.copy(listening = null, partial = "", error = st.error)
                        else -> cur.copy(partial = st.partial)
                    }
                }
            }
        }
    }

    fun toggleMic(side: Side) {
        val s = _state.value
        if (s.listening == side) { stop(); return }
        stop()
        _state.update { it.copy(listening = side, partial = "", error = null) }
        speech.start(s.lang(side).code) { text -> translate(side, text) }
    }

    private fun translate(speaker: Side, text: String) {
        val s = _state.value
        val from = s.lang(speaker)
        val to = s.lang(if (speaker == Side.ME) Side.OTHER else Side.ME)
        _state.update { it.copy(listening = null, partial = "", busy = true, turn = ConvTurn(speaker, text, "")) }
        job?.cancel()
        job = viewModelScope.launch {
            c.router.translate(TranslationRequest(text, from, to)).collect { u ->
                when (u) {
                    is TranslationUpdate.Partial -> _state.update { it.copy(turn = ConvTurn(speaker, text, u.text)) }
                    is TranslationUpdate.Final -> {
                        _state.update { it.copy(busy = false, turn = ConvTurn(speaker, text, u.text)) }
                        c.tts.speak(u.text, to.code)
                    }
                    is TranslationUpdate.Failed -> _state.update { it.copy(busy = false, error = u.message) }
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        speech.stop()
        _state.update { it.copy(listening = null, partial = "") }
    }

    fun setLanguage(side: Side, lang: Language) = _state.update { if (side == Side.ME) it.copy(me = lang) else it.copy(other = lang) }
    fun swapLanguages() = _state.update { it.copy(me = it.other, other = it.me) }

    override fun onCleared() { speech.stop(); c.tts.stop() }
}

@Composable
fun ConversationScreen(onBack: () -> Unit, vm: ConversationViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingSide by remember { mutableStateOf<Side?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val side = pendingSide
        if (granted && side != null) vm.toggleMic(side)
        else if (!granted) Toast.makeText(context, str(R.string.mic_permission), Toast.LENGTH_SHORT).show()
    }
    fun mic(side: Side) {
        pendingSide = side
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleMic(side)
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    ConversationContent(state, onMic = ::mic, onBack = { vm.stop(); onBack() }, onSwap = vm::swapLanguages, onLanguage = vm::setLanguage)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConversationContent(
    state: ConvState,
    onMic: (Side) -> Unit,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onLanguage: (Side, Language) -> Unit,
) {
    var picker by remember { mutableStateOf<Side?>(null) }
    val fold = app.glook.translate.ui.adaptive.rememberFoldPosture()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenH = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    // 테이블탑 자세: 힌지 위쪽=상대, 아래쪽=나. 컨트롤 줄을 힌지 위치에 맞춤
    val topW = if (fold.tabletop && screenH > 0) fold.hingeTop.toFloat().coerceAtLeast(1f) else 1f
    val botW = if (fold.tabletop && screenH > 0) (screenH - fold.hingeBottom).toFloat().coerceAtLeast(1f) else 1f
    val barH = if (fold.tabletop) with(density) { fold.hingeHeightPx.toDp() }.coerceAtLeast(56.dp) else androidx.compose.ui.unit.Dp.Unspecified
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val sideBySide = !fold.tabletop && conf.screenWidthDp >= 600 && conf.screenWidthDp > conf.screenHeightDp
    if (sideBySide) {
        // 가로로 넓은 화면: 좌우 분할(회전 없음). 북 자세에서는 가운데 열을 힌지 폭에 맞춤.
        val mid = if (fold.book && fold.separating) with(density) { fold.hingeWidthPx.toDp() }.coerceAtLeast(56.dp) else 56.dp
        Row(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Half(state, Side.ME, Modifier.weight(1f).fillMaxHeight(), onMic = onMic, onLanguage = { picker = Side.ME })
            Column(Modifier.width(mid).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                ChipIconButton(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.end_conversation), onClick = onBack)
                MorphSwapButton(onSwap = onSwap)
                Box(Modifier.size(48.dp))
            }
            Half(state, Side.OTHER, Modifier.weight(1f).fillMaxHeight(), onMic = onMic, onLanguage = { picker = Side.OTHER })
        }
    } else Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 상대방 쪽(180° 회전)
        Half(state, Side.OTHER, Modifier.weight(topW).rotate(180f), onMic = onMic, onLanguage = { picker = Side.OTHER })
        Row(Modifier.fillMaxWidth().then(if (barH.isSpecified) Modifier.height(barH) else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ChipIconButton(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.end_conversation), onClick = onBack)
            MorphSwapButton(onSwap = onSwap)
            Box(Modifier.size(48.dp))
        }
        Half(state, Side.ME, Modifier.weight(botW), onMic = onMic, onLanguage = { picker = Side.ME })
    }
    picker?.let { side ->
        LanguagePickerSheet(includeAuto = false, selected = state.lang(side).code, recents = emptyList(),
            onPick = { onLanguage(side, it); picker = null }, onDismiss = { picker = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Half(state: ConvState, side: Side, modifier: Modifier, onMic: (Side) -> Unit, onLanguage: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val me = side == Side.ME
    val listening = state.listening == side
    Surface(
        shape = GlookShapes.Container,
        color = if (me) cs.surfaceContainerLow else cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            LanguageButton(state.lang(side).displayName, onClick = onLanguage, accent = me)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
                val text = if (listening && state.partial.isNotBlank()) state.partial else state.textFor(side)
                when {
                    text != null && text.isNotBlank() -> Text(
                        text, style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = if (listening) cs.onSurfaceVariant else cs.onSurface,
                    )
                    state.busy && state.turn?.speaker != side -> LoadingIndicator(Modifier.size(48.dp))
                    else -> Text(
                        if (listening) str(R.string.listening) else "",
                        style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Start,
                    )
                }
            }
            state.error?.takeIf { me }?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MorphMicButton(listening = listening, onClick = { onMic(side) })
            }
        }
    }
}
