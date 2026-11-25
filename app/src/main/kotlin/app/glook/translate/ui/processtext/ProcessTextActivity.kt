package app.glook.translate.ui.processtext

import app.glook.translate.R
import app.glook.translate.core.str

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glook.translate.core.security.applySecureFlag
import app.glook.translate.ui.common.TranslatorViewModel
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.components.CircleActionButton
import app.glook.translate.ui.components.TtsWaveControl
import app.glook.translate.ui.fullscreen.FullscreenActivity
import app.glook.translate.ui.icons.ContentCopy
import app.glook.translate.ui.icons.FindReplace
import app.glook.translate.ui.icons.Fullscreen
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.main.copyToClipboard
import app.glook.translate.ui.theme.GlookShapes
import app.glook.translate.ui.theme.GlookTheme
import kotlinx.coroutines.launch

/**
 * 타 앱에서 텍스트 선택 → ⋮ → "Glook 번역".
 * 투명 액티비티 위에 ModalBottomSheet 만 띄워 호출한 앱 화면을 그대로 유지합니다.
 */
class ProcessTextActivity : ComponentActivity() {
    private val vm: TranslatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySecureFlag(true)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        // 결과를 돌려받을 호출자(startActivityForResult)가 있고, 편집 가능한 입력창일 때만 "대치" 노출
        val canReplace = !readOnly && callingActivity != null
        if (text.isBlank()) { finish(); return }
        if (savedInstanceState == null) vm.submitImmediately(text)

        setContent {
            val secure by vm.secureScreen.collectAsStateWithLifecycle()
            LaunchedEffect(secure) { applySecureFlag(secure) }
            GlookTheme {
                ProcessTextSheet(
                    vm = vm,
                    canReplace = canReplace,
                    onReplace = { translated ->
                        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, translated))
                        finish()
                    },
                    onExpand = { translated, lang ->
                        FullscreenActivity.start(this, translated, lang)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessTextSheet(
    vm: TranslatorViewModel,
    canReplace: Boolean,
    onReplace: (String) -> Unit,
    onExpand: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tts by vm.tts.collectAsStateWithLifecycle()
    var sourceExpanded by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasOutput = state.output.isNotBlank()

    fun hideThen(action: () -> Unit) = scope.launch { sheetState.hide() }.invokeOnCompletion { action() }

    // ModalBottomSheet 의 등장/드래그 애니메이션은 MaterialExpressiveTheme 의 MotionScheme(스프링)을 따릅니다.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 원문 (접기/펼치기)
            Row(
                Modifier.fillMaxWidth().clip(GlookShapes.Chip).clickable { sourceExpanded = !sourceExpanded }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                    Text(
                        (state.detected ?: state.source).displayName + " → " + state.target.displayName,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        state.input,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (sourceExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(if (sourceExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (sourceExpanded) str(R.string.collapse_original) else str(R.string.expand_original))
            }

            // 번역 결과 카드
            Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Column(Modifier.heightIn(min = 72.dp, max = 320.dp).verticalScroll(rememberScrollState())) {
                        when {
                            state.error != null && !hasOutput -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            !hasOutput -> LoadingIndicator(Modifier.size(48.dp))
                            else -> Text(state.output, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TtsWaveControl(tts, onToggle = { vm.toggleSpeak() }, enabled = hasOutput, modifier = Modifier.weight(1f))
                        ChipIconButton(GlookIcons.ContentCopy, str(R.string.copy), enabled = hasOutput, onClick = {
                            copyToClipboard(context, state.output)
                            Toast.makeText(context, str(R.string.copied), Toast.LENGTH_SHORT).show()
                        })
                        CircleActionButton(GlookIcons.Fullscreen, str(R.string.fullscreen_zoom), enabled = hasOutput, onClick = {
                            hideThen { onExpand(state.output, state.target.code) }
                        })
                    }
                }
            }

            AnimatedVisibility(canReplace && hasOutput && !state.streaming) {
                FilledTonalButton(onClick = { onReplace(state.output) }, shape = GlookShapes.Chip, modifier = Modifier.fillMaxWidth()) {
                    Icon(GlookIcons.FindReplace, null)
                    Spacer(Modifier.size(8.dp))
                    Text(str(R.string.replace_with_translation))
                }
            }
        }
    }
}
