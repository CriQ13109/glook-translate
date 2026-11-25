package app.glook.translate

import android.app.Application
import app.glook.translate.core.security.DatabaseKeyManager
import app.glook.translate.data.db.Vault
import app.glook.translate.data.engine.HybridTranslationRouter
import app.glook.translate.data.engine.MlKitOnDeviceEngine
import app.glook.translate.data.engine.NetworkMonitor
import app.glook.translate.data.ocr.OcrEngine
import app.glook.translate.data.repository.ApiKeyRepositoryImpl
import app.glook.translate.data.repository.LibraryRepositoryImpl
import app.glook.translate.data.repository.SettingsRepositoryImpl
import app.glook.translate.data.repository.VaultResetterImpl
import app.glook.translate.data.speech.TtsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GlookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        app.glook.translate.core.AppStrings.init(this)
        app.glook.translate.core.crash.CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // 크래시 보고 프로세스에서는 DB·엔진 등을 만들지 않음
        if (app.glook.translate.core.crash.CrashReporter.isCrashProcess()) return
        container = AppContainer(this)
    }
}

/** 수동 DI 컨테이너 (Hilt 없이 — 빌드 단순화, 리플렉션/코드젠 최소화). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault = Vault(app, DatabaseKeyManager(app))
    val settings = SettingsRepositoryImpl(vault)
    val apiKeys = ApiKeyRepositoryImpl(vault)
    val library = LibraryRepositoryImpl(vault, settings)
    val network = NetworkMonitor(app)
    val onDevice = MlKitOnDeviceEngine(network)
    val router = HybridTranslationRouter(onDevice, network, settings, apiKeys)
    val tts by lazy { TtsController(app) }
    val ocr by lazy { OcrEngine(app) }
    val resetter = VaultResetterImpl(vault) { }

    init {
        // 자주 쓰는 쌍의 온디바이스 모델을 Wi-Fi 에서 미리 받아 오프라인 폴백 대비
        appScope.launch {
            val s = runCatching { settings.settings.first() }.getOrNull()
            onDevice.prefetch("ko", s?.targetLang ?: "ko", s?.sourceLang?.takeIf { it != "auto" } ?: "ja")
        }
    }
}
