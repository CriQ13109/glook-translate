package app.glook.translate.ui.document

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.GlookApp
import app.glook.translate.data.document.DocFormat
import app.glook.translate.data.document.DocumentException
import app.glook.translate.data.document.DocumentReader
import app.glook.translate.data.document.ParsedDocument
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.components.ActionButton
import app.glook.translate.ui.components.LanguageBar
import app.glook.translate.ui.components.LanguagePickerSheet
import app.glook.translate.ui.components.ShapedIcon
import app.glook.translate.ui.components.bouncyClick
import app.glook.translate.ui.icons.ContentCopy
import app.glook.translate.ui.icons.Description
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.theme.GlookShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocUiState(
    val name: String? = null,
    val format: DocFormat? = null,
    val originals: List<String> = emptyList(),
    val translated: Map<Int, String> = emptyMap(),
    val failed: Set<Int> = emptySet(),
    val running: Boolean = false,
    val loading: Boolean = false,
    val target: Language = Language.of("ko"),
    val source: Language = Language.Auto,
    val detected: Language? = null,
    val note: String? = null,
    val error: String? = null,
) {
    val done get() = translated.size + failed.size
    val progress get() = if (originals.isEmpty()) 0f else done.toFloat() / originals.size
    val translatedText get() = originals.indices.joinToString("\n\n") { translated[it] ?: originals[it] }
}

class DocumentTranslateViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    private val reader = DocumentReader(app, c.ocr)
    private val _state = MutableStateFlow(DocUiState())
    val state: StateFlow<DocUiState> = _state
    private var source = Language.Auto
    private var job: Job? = null
    private var loaded: ParsedDocument? = null

    fun open(uri: Uri) {
        job?.cancel()
        _state.update { DocUiState(loading = true, target = it.target, source = it.source) }
        job = viewModelScope.launch {
            try {
                val s = c.settings.settings.first()
                source = Language.of(s.sourceLang)
                val target = Language.of(s.targetLang)
                val doc = reader.read(uri, source.code)
                loaded = doc
                _state.update { DocUiState(name = doc.name, format = doc.format, originals = doc.paragraphs, target = target, source = source, note = doc.note, running = true) }
                translateAll(target)
            } catch (e: CancellationException) { throw e
            } catch (e: DocumentException) { _state.update { it.copy(loading = false, running = false, error = e.message) }
            } catch (e: Throwable) { _state.update { it.copy(loading = false, running = false, error = str(R.string.doc_read_fail)) } }
        }
    }

    private fun retranslate(src: Language, tgt: Language) {
        if (loaded == null) return
        job?.cancel()
        source = src
        _state.update { it.copy(source = src, target = tgt, detected = null, translated = emptyMap(), failed = emptySet(), running = true) }
        viewModelScope.launch { c.settings.update { it.copy(sourceLang = src.code, targetLang = tgt.code) } }
        job = viewModelScope.launch { translateAll(tgt) }
    }

    fun setTarget(l: Language) = retranslate(source, l)
    fun setSource(l: Language) = retranslate(l, _state.value.target)
    fun swap() {
        val st = _state.value
        val from = if (st.source.isAuto) st.detected ?: return else st.source
        retranslate(st.target, from)
    }

    fun cancel() { job?.cancel(); _state.update { it.copy(running = false) } }

    private suspend fun translateAll(target: Language) {
        val list = _state.value.originals
        try {
            list.forEachIndexed { i, text ->
                var result: String? = null
                c.router.translate(TranslationRequest(text, source, target)).collect { u ->
                    if (u is TranslationUpdate.Final) result = u.text
                    if (u is TranslationUpdate.Detected && _state.value.detected == null) _state.update { it.copy(detected = u.source) }
                }
                _state.update { if (result != null) it.copy(translated = it.translated + (i to result!!)) else it.copy(failed = it.failed + i) }
            }
        } finally {
            _state.update { it.copy(running = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentTranslateScreen(initialUri: Uri?, onBack: () -> Unit, vm: DocumentTranslateViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showOriginal by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Boolean?>(null) } // true=원문, false=대상
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { o -> o.write(s.translatedText.toByteArray()) } }
    }
    LaunchedEffect(initialUri) {
        if (initialUri != null) vm.open(initialUri) else if (s.name == null && !s.loading) pick.launch(arrayOf("*/*"))
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            ChipIconButton(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.back), onClick = onBack)
            Text(s.name ?: str(R.string.doc_translate_title), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            if (s.translated.isNotEmpty()) {
                ChipIconButton(GlookIcons.ContentCopy, str(R.string.copy_translation), onClick = {
                    (context.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("translation", s.translatedText))
                })
                ChipIconButton(Icons.Filled.Share, str(R.string.share), onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.translatedText), null))
                })
            }
        }

        if (s.originals.isNotEmpty()) {
            LanguageBar(
                sourceLabel = if (s.source.isAuto) s.detected?.let { str(R.string.detected_fmt, it.displayName) } ?: str(R.string.auto_detect) else s.source.displayName,
                targetLabel = s.target.displayName,
                onSource = { picker = true }, onTarget = { picker = false }, onSwap = vm::swap,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ActionButton(str(R.string.show_original_too), onClick = { showOriginal = !showOriginal }, modifier = Modifier.weight(1f), selected = showOriginal)
                ActionButton(str(R.string.save_txt), onClick = { save.launch((s.name?.substringBeforeLast('.') ?: str(R.string.translated_word)) + "_" + str(R.string.translated_word) + ".txt") }, modifier = Modifier.weight(1f), enabled = s.translated.isNotEmpty())
            }
            if (s.running) {
                LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Text("${s.done} / ${s.originals.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(s.originals) { i, orig ->
                    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            if (showOriginal) Text(orig, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (showOriginal) Spacer(Modifier.height(8.dp))
                            val t = s.translated[i]
                            when {
                                t != null -> Text(t, style = MaterialTheme.typography.bodyLarge)
                                i in s.failed -> Text(str(R.string.doc_translate_fail), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                else -> Text(orig, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    s.loading -> androidx.compose.material3.LoadingIndicator()
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.bouncyClick { pick.launch(arrayOf("*/*")) }) {
                            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShapedIcon(MaterialShapes.Cookie9Sided, GlookIcons.Description, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 72.dp)
                                Text(str(R.string.pick_document), style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("PDF · DOCX · HWPX · TXT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
    picker?.let { isSource ->
        LanguagePickerSheet(
            includeAuto = isSource, selected = if (isSource) s.source.code else s.target.code, recents = emptyList(),
            onPick = { if (isSource) vm.setSource(it) else vm.setTarget(it); picker = null }, onDismiss = { picker = null },
        )
    }
}
