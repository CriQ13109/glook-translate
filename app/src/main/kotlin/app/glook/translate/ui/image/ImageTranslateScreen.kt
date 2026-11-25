package app.glook.translate.ui.image

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.GlookApp
import app.glook.translate.data.ocr.OcrBlock
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import app.glook.translate.ui.common.TranslatorViewModel
import app.glook.translate.ui.components.ActionButton
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.icons.ContentCopy
import app.glook.translate.ui.icons.Compare
import app.glook.translate.ui.icons.ContentPasteGo
import app.glook.translate.ui.home.FeatureTile
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.Image
import app.glook.translate.ui.icons.PhotoCamera
import app.glook.translate.ui.main.ImageEntry
import app.glook.translate.ui.main.copyToClipboard
import app.glook.translate.ui.theme.GlookShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ImageUiState(
    val bitmap: Bitmap? = null,
    val blocks: List<OcrBlock> = emptyList(),
    val translated: Map<Int, String> = emptyMap(),
    val running: Boolean = false,
    val message: String? = null,
    val target: Language = Language.of("ko"),
) {
    val done get() = !running && bitmap != null && blocks.isNotEmpty() && translated.size >= blocks.size
}

/**
 * 이미지 번역: ML Kit OCR 로 글자 블록을 찾고 블록마다 번역한 뒤 원본 사진 위에 번역문을 덮어 그립니다.
 * 이미지는 메모리에서만 처리하고 촬영 임시 파일은 즉시 삭제합니다(디스크·DB 에 사진을 남기지 않음).
 */
class ImageTranslateViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    private val _state = MutableStateFlow(ImageUiState())
    val state: StateFlow<ImageUiState> = _state

    fun reset() { job?.cancel(); _state.update { ImageUiState(target = it.target) } }

    private var job: kotlinx.coroutines.Job? = null

    fun load(uri: Uri, deleteAfter: File? = null) {
        _state.update { ImageUiState(running = true, target = it.target) }
        job = viewModelScope.launch {
            try {
                val s = c.settings.settings.first()
                val target = Language.of(s.targetLang)
                val source = Language.of(s.sourceLang)
                val bmp = decode(uri)
                deleteAfter?.delete()
                _state.update { it.copy(bitmap = bmp, target = target) }
                val blocks = try { c.ocr.recognizeBlocks(bmp, source.code) } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                    _state.update { it.copy(running = false, message = str(R.string.ocr_unavailable)) }
                    return@launch
                }
                if (blocks.isEmpty()) {
                    _state.update { it.copy(running = false, message = str(R.string.image_no_text)) }
                    return@launch
                }
                _state.update { it.copy(blocks = blocks) }
                blocks.forEachIndexed { i, b ->
                    var result: String? = null
                    c.router.translate(TranslationRequest(b.text, source, target)).collect { u ->
                        if (u is TranslationUpdate.Final) result = u.text
                    }
                    result?.let { r -> _state.update { it.copy(translated = it.translated + (i to r)) } }
                }
                _state.update { it.copy(running = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                deleteAfter?.delete()
                _state.update { it.copy(running = false, message = str(R.string.image_load_fail)) }
            }
        }
    }

    private fun decode(uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val max = 2048f
            val scale = minOf(1f, max / maxOf(info.size.width, info.size.height))
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageTranslateScreen(entry: ImageEntry, translator: TranslatorViewModel, onBack: () -> Unit, vm: ImageTranslateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var launched by rememberSaveable { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    fun openPicker() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    LaunchedEffect(Unit) { if (!launched) { launched = true; if (entry == ImageEntry.GALLERY) openPicker() } }

    // 사진이 없고 진행 중도 아니고 오류도 없으면 앱 내 카메라(+최근 사진 줄)
    if (state.bitmap == null && !state.running && state.message == null) {
        CameraCapture(
            onCaptured = { uri, file -> vm.load(uri, deleteAfter = file) },
            onOpenPicker = ::openPicker,
            onBack = onBack,
        )
        return
    }

    ImageTranslateContent(
        state = state, showOriginal = showOriginal,
        onBack = onBack,
        onToggleOriginal = { showOriginal = !showOriginal },
        onCopy = {
            copyToClipboard(context, state.blocks.indices.mapNotNull { state.translated[it] }.joinToString("\n"))
            Toast.makeText(context, str(R.string.translation_copied), Toast.LENGTH_SHORT).show()
        },
        onToText = {
            translator.submitImmediately(state.blocks.joinToString("\n") { it.text })
            onBack()
        },
        onCamera = vm::reset,
        onGallery = ::openPicker,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageTranslateContent(
    state: ImageUiState, showOriginal: Boolean,
    onBack: () -> Unit, onToggleOriginal: () -> Unit, onCopy: () -> Unit, onToText: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            ChipIconButton(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.back), onClick = onBack)
            Text(str(R.string.image_translate_title_fmt, state.target.displayName), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 8.dp))
        }
        Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bmp = state.bitmap
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    if (!showOriginal) Overlay(bmp, state)
                }
                if (state.running) {
                    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)) {
                        androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp))
                            Text(
                                if (state.blocks.isEmpty()) str(R.string.finding_text) else str(R.string.translating_progress_fmt, state.translated.size, state.blocks.size),
                                modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
                if (bmp == null && !state.running) {
                    androidx.compose.foundation.layout.Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(str(R.string.retake), onCamera, selected = true)
                        ActionButton(str(R.string.gallery), onGallery)
                    }
                }
            }
        }
        if (state.bitmap != null) HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onCamera) { Icon(GlookIcons.PhotoCamera, str(R.string.retake)) }
            },
        ) {
            ChipIconButton(GlookIcons.Image, str(R.string.other_image), onClick = onGallery)
            ChipIconButton(GlookIcons.Compare, if (showOriginal) str(R.string.view_translation) else str(R.string.view_original), onClick = onToggleOriginal, enabled = state.bitmap != null, selected = showOriginal)
            ChipIconButton(GlookIcons.ContentCopy, str(R.string.copy_translation), onClick = onCopy, enabled = state.translated.isNotEmpty())
            ChipIconButton(GlookIcons.ContentPasteGo, str(R.string.original_to_input), onClick = onToText, enabled = state.blocks.isNotEmpty())
        }
    }
}

/** 원본 이미지 위(ContentScale.Fit 영역)에 블록별 번역문을 덮어 그림. */
@Composable
private fun Overlay(bmp: Bitmap, state: ImageUiState) {
    val measurer = rememberTextMeasurer()
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val fg = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxSize()) {
        val scale = minOf(size.width / bmp.width, size.height / bmp.height)
        val dx = (size.width - bmp.width * scale) / 2f
        val dy = (size.height - bmp.height * scale) / 2f
        state.blocks.forEachIndexed { i, b ->
            val text = state.translated[i] ?: return@forEachIndexed
            val left = dx + b.box.left * scale
            val top = dy + b.box.top * scale
            val w = (b.box.width() * scale).coerceAtLeast(24f)
            val h = (b.box.height() * scale).coerceAtLeast(16f)
            drawRoundRect(bg, Offset(left, top), Size(w, h), CornerRadius(8f, 8f))
            val laid = fitText(measurer, text, w - 8f, h - 4f, fg)
            drawText(laid, topLeft = Offset(left + 4f, top + 2f))
        }
    }
}

private fun fitText(measurer: TextMeasurer, text: String, w: Float, h: Float, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.TextLayoutResult {
    var size = h.coerceIn(10f, 64f)
    var result = measurer.measure(text, TextStyle(fontSize = TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp), color = color),
        constraints = Constraints(maxWidth = w.toInt().coerceAtLeast(1)), overflow = TextOverflow.Ellipsis)
    var guard = 0
    while (result.size.height > h && size > 9f && guard++ < 14) {
        size *= 0.88f
        result = measurer.measure(text, TextStyle(fontSize = TextUnit(size, androidx.compose.ui.unit.TextUnitType.Sp), color = color),
            constraints = Constraints(maxWidth = w.toInt().coerceAtLeast(1)), overflow = TextOverflow.Ellipsis)
    }
    return result
}
