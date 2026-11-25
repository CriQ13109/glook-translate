package app.glook.translate.ui.main

import app.glook.translate.R
import app.glook.translate.core.str

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glook.translate.ui.common.TranslatorViewModel
import app.glook.translate.ui.fullscreen.FullscreenActivity
import app.glook.translate.ui.home.HomeCallbacks
import app.glook.translate.ui.home.HomeScreen
import app.glook.translate.ui.sheet.LibrarySheet
import app.glook.translate.ui.sheet.OfflinePacksSheet
import app.glook.translate.ui.sheet.SettingsSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

/** 홈 화면 라우트: ViewModel·권한·시트를 연결하고 상태 없는 [HomeScreen] 에 전달. */
@Composable
fun HomeRoute(
    vm: TranslatorViewModel,
    pendingAction: MutableStateFlow<LaunchAction>,
    onOpenConversation: () -> Unit,
    onOpenImage: (ImageEntry) -> Unit,
    onOpenDocument: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tts by vm.tts.collectAsStateWithLifecycle()
    val speech by vm.speechState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val action by pendingAction.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showLibrary by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPacks by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val field = rememberTextFieldState()

    // 입력창 ⇄ ViewModel: 입력창(TextFieldState)이 진실 공급원. 입력은 VM 으로 "올리기만" 하고,
    // VM 이 되돌려 쓰는 것은 외부 유입 텍스트(setText 이벤트)뿐이라 IME 조합을 건드리지 않습니다.
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { vm.onInput(it) } }
    LaunchedEffect(vm) {
        vm.setText.collect { text -> if (field.text.toString() != text) field.setTextAndPlaceCursorAtEnd(text) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkClipboard() }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoice() else Toast.makeText(context, str(R.string.mic_permission), Toast.LENGTH_SHORT).show()
    }
    fun startVoice() {
        if (speech.listening) { vm.stopVoice(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoice()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(action) {
        when (action) {
            LaunchAction.FOCUS_INPUT -> runCatching { focus.requestFocus() }
            LaunchAction.VOICE -> startVoice()
            LaunchAction.OCR -> onOpenImage(ImageEntry.CAMERA)
            LaunchAction.CONVERSATION -> onOpenConversation()
            LaunchAction.NONE -> Unit
        }
        pendingAction.value = LaunchAction.NONE
    }

    HomeScreen(
        state = state, tts = tts, listening = speech.listening, partial = speech.partial,
        cloudOn = settings.cloudEnabled, field = field, focus = focus,
        cb = HomeCallbacks(
            onClear = vm::clear,
            onPaste = { readClipboard(context)?.let(vm::submitImmediately) },
            onSwap = vm::swap,
            onPickSource = vm::setSource,
            onPickTarget = vm::setTarget,
            onVoice = ::startVoice,
            onImage = { onOpenImage(ImageEntry.CAMERA) },
            onConversation = onOpenConversation,
            onDocument = onOpenDocument,
            onSpeak = { if (!vm.toggleSpeak()) Toast.makeText(context, str(R.string.no_voice_data), Toast.LENGTH_SHORT).show() },
            onCopy = { copyToClipboard(context, state.output); Toast.makeText(context, str(R.string.copied), Toast.LENGTH_SHORT).show() },
            onBookmark = vm::toggleBookmark,
            onShare = { shareText(context, state.output) },
            onFullscreen = { FullscreenActivity.start(context, state.output, state.target.code) },
            onLibrary = { showLibrary = true },
            onSettings = { showSettings = true },
            onOfflinePacks = { showPacks = true },
            onClipboardAccept = vm::acceptClipboard,
            onClipboardDismiss = vm::dismissClipboard,
        ),
    )

    if (showLibrary) LibrarySheet(onDismiss = { showLibrary = false }, onOpen = { text -> showLibrary = false; vm.submitImmediately(text) })
    if (showSettings) SettingsSheet(onDismiss = { showSettings = false }, onOpenPacks = { showSettings = false; showPacks = true })
    if (showPacks) OfflinePacksSheet(onDismiss = { showPacks = false })
}

// ---------- helpers ----------
fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Glook", text))
}

fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
