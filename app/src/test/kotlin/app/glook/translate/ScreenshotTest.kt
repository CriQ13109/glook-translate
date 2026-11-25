package app.glook.translate

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import app.glook.translate.data.speech.TtsState
import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import app.glook.translate.ui.common.TranslatorUiState
import app.glook.translate.ui.conversation.ConvState
import app.glook.translate.ui.conversation.ConvTurn
import app.glook.translate.ui.conversation.ConversationContent
import app.glook.translate.ui.conversation.Side
import app.glook.translate.ui.home.HomeCallbacks
import app.glook.translate.ui.home.HomeScreen
import app.glook.translate.ui.theme.GlookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @org.junit.Before fun initStrings() { app.glook.translate.core.AppStrings.init(androidx.test.core.app.ApplicationProvider.getApplicationContext()) }

    @get:Rule val rule = createComposeRule()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { GlookTheme(darkTheme = name.endsWith("dark"), dynamicColor = !name.contains("fixed")) { content() } }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable private fun Home(state: TranslatorUiState, fab: Boolean = false, speaking: Boolean = false) {
        val field = rememberTextFieldState(state.input)
        HomeScreen(state, TtsState(speaking, if (speaking) 0.4f else null), false, "", true, field, androidx.compose.runtime.remember { FocusRequester() }, HomeCallbacks(), Modifier.fillMaxSize())
    }

    private val empty = TranslatorUiState(ready = true, recents = listOf(Language.of("ja"), Language.of("en")))
    private val done = empty.copy(input = "내일 회의 몇 시에 시작해요?", output = "明日の会議は何時に始まりますか?", target = Language.of("ja"),
        detected = Language.of("ko"), engine = EngineKind.ON_DEVICE)

    @Test fun homeEmpty() = shot("home_empty") { Home(empty.copy(clipboardOffer = true)) }
    @Test fun homeResult() = shot("home_result") { Home(done, speaking = true) }
    @Test fun homeFixed() = shot("home_fixed") { Home(empty.copy(clipboardOffer = true)) }
    @Test fun homeFixedDark() = shot("home_fixed_dark") { Home(empty.copy(clipboardOffer = true)) }
    @Test fun homeResultDark() = shot("home_result_dark") { Home(done) }
    @Test @Config(qualifiers = "w840dp-h900dp-xhdpi") fun homeWide() = shot("home_wide") { Home(done) }
    @Test @Config(qualifiers = "w1000dp-h720dp-xhdpi") fun homeWideEmpty() = shot("home_wide_empty") { Home(empty) }
    @Test @Config(qualifiers = "w1000dp-h720dp-xhdpi") fun homeWideResult() = shot("home_wide_result") { Home(done) }
    @Test @Config(qualifiers = "w1000dp-h720dp-xhdpi") fun conversationWide() = shot("conversation_wide") {
        ConversationContent(ConvState(turn = ConvTurn(Side.OTHER, "Where is the station?", "역이 어디예요?"), listening = Side.ME, partial = "저기 앞에"), {}, {}, {}, { _, _ -> })
    }
    @Test @Config(qualifiers = "en-w1000dp-h720dp-xhdpi") fun homeWideEn() = shot("home_wide_en") { Home(done) }
    @Test @Config(qualifiers = "ja-w1000dp-h720dp-xhdpi") fun homeWideJa() = shot("home_wide_ja") { Home(empty) }
    @Test @Config(qualifiers = "ru-w1000dp-h720dp-xhdpi") fun homeWideRu() = shot("home_wide_ru") { Home(empty) }
    @Test fun conversation() = shot("conversation") {
        ConversationContent(ConvState(turn = ConvTurn(Side.OTHER, "Where is the station?", "역이 어디예요?"), listening = Side.ME, partial = "저기 앞에"), {}, {}, {}, { _, _ -> })
    }
}
