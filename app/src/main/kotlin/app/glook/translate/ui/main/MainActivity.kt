package app.glook.translate.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glook.translate.core.security.applySecureFlag
import app.glook.translate.ui.common.TranslatorViewModel
import app.glook.translate.ui.conversation.ConversationScreen
import app.glook.translate.ui.image.ImageTranslateScreen
import app.glook.translate.ui.theme.GlookTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** 위젯·타일·공유 시트에서 들어오는 진입 동작. */
enum class LaunchAction { NONE, FOCUS_INPUT, VOICE, OCR, CONVERSATION }
enum class ImageEntry { CHOOSE, CAMERA, GALLERY }

private enum class Dest { HOME, CONVERSATION, IMAGE, DOCUMENT }

class MainActivity : ComponentActivity() {
    private val vm: TranslatorViewModel by viewModels()
    private val pendingAction = MutableStateFlow(LaunchAction.NONE)
    /** 다른 앱에서 공유된 문서 (공유 시트 → Glook). */
    private val sharedDoc = MutableStateFlow<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        applySecureFlag(true) // 설정 로드 전에는 안전한 기본값
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val secure by vm.secureScreen.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            LaunchedEffect(secure) { applySecureFlag(secure) }
            var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
            var imageEntry by rememberSaveable { mutableStateOf(ImageEntry.CHOOSE) }
            var docUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
            val shared by sharedDoc.collectAsStateWithLifecycle()
            LaunchedEffect(shared) { shared?.let { docUri = it; dest = Dest.DOCUMENT; sharedDoc.value = null } }
            BackHandler(dest != Dest.HOME) { dest = Dest.HOME; docUri = null }
            GlookTheme(haptics = settings.haptics) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    val spec = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = dest,
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = fadeIn(spec.defaultEffectsSpec()) + scaleIn(spec.defaultSpatialSpec(), initialScale = 0.92f),
                                initialContentExit = fadeOut(spec.defaultEffectsSpec()) + scaleOut(spec.defaultSpatialSpec(), targetScale = 1.04f),
                            )
                        },
                        label = "dest",
                    ) { d ->
                        when (d) {
                            Dest.HOME -> HomeRoute(
                                vm = vm, pendingAction = pendingAction,
                                onOpenConversation = { dest = Dest.CONVERSATION },
                                onOpenImage = { imageEntry = it; dest = Dest.IMAGE },
                                onOpenDocument = { docUri = null; dest = Dest.DOCUMENT },
                            )
                            Dest.CONVERSATION -> ConversationScreen(onBack = { dest = Dest.HOME })
                            Dest.IMAGE -> ImageTranslateScreen(imageEntry, vm, onBack = { dest = Dest.HOME })
                            Dest.DOCUMENT -> app.glook.translate.ui.document.DocumentTranslateScreen(docUri, onBack = { dest = Dest.HOME; docUri = null })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
                if (stream != null && intent.type?.startsWith("text/plain") != true || stream != null && intent.getStringExtra(Intent.EXTRA_TEXT) == null) sharedDoc.value = stream
                else intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.submitImmediately(it) }
            }
            ACTION_FOCUS_INPUT -> pendingAction.value = LaunchAction.FOCUS_INPUT
            ACTION_VOICE -> pendingAction.value = LaunchAction.VOICE
            ACTION_OCR -> pendingAction.value = LaunchAction.OCR
            ACTION_CONVERSATION -> pendingAction.value = LaunchAction.CONVERSATION
            ACTION_TRANSLATE_TEXT -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.submitImmediately(it) }
        }
    }

    companion object {
        const val ACTION_FOCUS_INPUT = "app.glook.translate.action.FOCUS_INPUT"
        const val ACTION_VOICE = "app.glook.translate.action.VOICE"
        const val ACTION_OCR = "app.glook.translate.action.OCR"
        const val ACTION_CONVERSATION = "app.glook.translate.action.CONVERSATION"
        const val ACTION_TRANSLATE_TEXT = "app.glook.translate.action.TRANSLATE_TEXT"
    }
}
