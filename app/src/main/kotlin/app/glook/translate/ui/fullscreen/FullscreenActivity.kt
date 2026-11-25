package app.glook.translate.ui.fullscreen

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.glook.translate.GlookApp
import app.glook.translate.core.security.applySecureFlag
import app.glook.translate.data.engine.RubySegment
import app.glook.translate.data.phonetic.PhoneticTranscriber
import app.glook.translate.domain.model.Language
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.LightMode
import app.glook.translate.ui.theme.GlookShapes
import app.glook.translate.ui.theme.GlookTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FullscreenViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    val ruby = MutableStateFlow<List<RubySegment>?>(null)
    val rubyLoading = MutableStateFlow(false)
    val secure = c.settings.settings.map { it.secureScreen }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun loadRuby(text: String, lang: String) {
        if (ruby.value != null || rubyLoading.value) return
        PhoneticTranscriber.offlineRuby(text, lang)?.let { ruby.value = it; return }
        rubyLoading.value = true
        viewModelScope.launch {
            ruby.value = c.router.readings(text, Language.of(lang))
            rubyLoading.value = false
        }
    }
}

/** 현장 소통용 풀스크린 고대비 카드. */
class FullscreenActivity : ComponentActivity() {
    private val vm: FullscreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySecureFlag(true)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val lang = intent.getStringExtra(EXTRA_LANG) ?: "en"
        setContent {
            val secure by vm.secure.collectAsStateWithLifecycle()
            LaunchedEffect(secure) { applySecureFlag(secure) }
            GlookTheme { FullscreenCard(text, lang, vm, onBrightness = ::setMaxBrightness, onClose = ::finish) }
        }
    }

    /** 화면 밝기 일시 최대화 (이 창에만 적용, 액티비티 종료 시 시스템 밝기로 복귀). */
    private fun setMaxBrightness(max: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (max) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    companion object {
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_LANG = "lang"
        fun start(context: Context, text: String, lang: String) {
            context.startActivity(
                Intent(context, FullscreenActivity::class.java)
                    .putExtra(EXTRA_TEXT, text).putExtra(EXTRA_LANG, lang)
                    .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullscreenCard(text: String, lang: String, vm: FullscreenViewModel, onBrightness: (Boolean) -> Unit, onClose: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    var showRuby by rememberSaveable { mutableStateOf(false) }
    var showRoman by rememberSaveable { mutableStateOf(false) }
    var bright by rememberSaveable { mutableStateOf(true) }
    val ruby by vm.ruby.collectAsStateWithLifecycle()
    val rubyLoading by vm.rubyLoading.collectAsStateWithLifecycle()
    val hasPhonetics = PhoneticTranscriber.supportsPhonetics(lang)
    val rubyCapable = lang == "ja" || lang == "zh"

    DisposableEffect(bright) {
        onBrightness(bright)
        onDispose { onBrightness(false) }
    }
    LaunchedEffect(showRuby) { if (showRuby) vm.loadRuby(text, lang) }

    // Display Large 기본, 긴 문장은 화면 안에 들어오도록 단계적으로 축소
    val base = MaterialTheme.typography
    val style: TextStyle = when {
        text.length <= 24 -> base.displayLarge
        text.length <= 60 -> base.displayMedium
        text.length <= 140 -> base.displaySmall
        else -> base.headlineLarge
    }.copy(color = fg, fontWeight = FontWeight.SemiBold)

    Box(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, str(R.string.close), tint = fg) }
                Box(Modifier.weight(1f))
                if (rubyCapable) FilterChip(selected = showRuby, onClick = { showRuby = !showRuby }, label = { Text(if (lang == "ja") str(R.string.furigana) else str(R.string.pinyin)) }, shape = GlookShapes.Chip)
                if (hasPhonetics) FilterChip(selected = showRoman, onClick = { showRoman = !showRoman }, label = { Text(str(R.string.romanization)) }, shape = GlookShapes.Chip)
                FilterChip(selected = bright, onClick = { bright = !bright }, label = { Text(str(R.string.brighten)) }, leadingIcon = { Icon(GlookIcons.LightMode, null) }, shape = GlookShapes.Chip)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                val segs = ruby
                if (showRuby && segs != null) {
                    FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        segs.forEach { seg ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(seg.reading ?: " ", style = base.titleMedium.copy(color = fg.copy(alpha = 0.75f)))
                                Text(seg.base, style = style)
                            }
                        }
                    }
                } else {
                    Text(text, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (showRuby && !rubyLoading && segs == null) {
                        Text(
                            str(R.string.ruby_byok_only),
                            style = base.bodyMedium.copy(color = fg.copy(alpha = 0.6f)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
                if (showRoman) {
                    Text(
                        PhoneticTranscriber.romanize(text, lang),
                        style = base.headlineSmall.copy(color = fg.copy(alpha = 0.8f)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }
    }
}
