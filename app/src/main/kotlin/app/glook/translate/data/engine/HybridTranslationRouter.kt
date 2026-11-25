package app.glook.translate.data.engine

import app.glook.translate.R
import app.glook.translate.core.str

import app.glook.translate.domain.model.CloudProvider
import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import app.glook.translate.domain.repository.ApiKeyRepository
import app.glook.translate.domain.repository.SettingsRepository
import app.glook.translate.domain.repository.TranslationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 하이브리드 번역 라우터.
 *
 * ```
 *  입력(250ms 디바운스는 ViewModel) ─▶ 언어 감지(ML Kit LangID) ─▶ 화법 판별(RegisterDetector)
 *        │
 *        ├─ 클라우드 미설정 / 오프라인 ───────────────▶ ML Kit 온디바이스 → Final
 *        │
 *        └─ BYOK 클라우드 스트리밍 시작 (t=0)
 *             ├─ 첫 토큰 도착 → Partial 로 실시간 렌더 → Final
 *             ├─ t=600ms 까지 첫 토큰 없음 → ML Kit 결과를 "임시" Partial 로 먼저 표시
 *             │      (이후 클라우드 토큰이 오면 클라우드 결과로 자연스럽게 교체)
 *             ├─ t=4s 까지 첫 토큰 없음 / 오류 / 거절 → 클라우드 취소, ML Kit 결과를 Final(fellBack)
 *             └─ 스트리밍 도중 오류 → ML Kit 결과를 Final(fellBack)
 * ```
 */
class HybridTranslationRouter(
    private val onDevice: MlKitOnDeviceEngine,
    private val network: NetworkMonitor,
    private val settings: SettingsRepository,
    private val keys: ApiKeyRepository,
    private val cloudFactory: CloudEngineFactory = CloudEngineFactory(),
) : TranslationRepository {

    override fun translate(request: TranslationRequest): Flow<TranslationUpdate> = channelFlow {
        val text = request.text.trim()
        if (text.isEmpty()) return@channelFlow

        // 1) 원문 언어 확정
        val source = if (request.source.isAuto) {
            (runCatching { onDevice.detect(text) }.getOrNull() ?: Language.of("en")).also { send(TranslationUpdate.Detected(it)) }
        } else request.source

        // 2) 감지된 언어 == 대상 언어면 대상 자동 전환 (한국어↔영어)
        var target = request.target
        if (source.code == target.code) {
            target = Language.of(if (source.code == "ko") "en" else "ko")
            send(TranslationUpdate.Retargeted(target))
        }

        val engineRequest = EngineRequest(text, source, target, RegisterDetector.detect(text, source.code))

        suspend fun onDeviceText(): String? = runCatching {
            onDevice.stream(engineRequest).fold("") { acc, s -> acc + s }
        }.getOrElse { if (it is CancellationException) throw it else null }

        suspend fun finishOnDevice(fellBack: Boolean, cached: String? = null) {
            val result = cached ?: onDeviceText()
            if (result != null) {
                send(TranslationUpdate.Final(result, EngineKind.ON_DEVICE, source, fellBack))
            } else {
                send(TranslationUpdate.Failed(str(R.string.offline_model_missing)))
            }
        }

        // 3) 클라우드 엔진 사용 가능 여부
        val cloud = cloudEngineOrNull()
        if (cloud == null || !network.isOnline()) {
            finishOnDevice(fellBack = cloud != null)
            return@channelFlow
        }

        // 4) 클라우드 스트리밍 + 온디바이스 폴백 경주
        val firstToken = CompletableDeferred<Boolean>() // true=첫 토큰 도착, false=첫 토큰 전 실패
        val midStreamFailure = AtomicBoolean(false)
        var cloudText = ""

        val cloudJob = launch {
            try {
                val sb = StringBuilder()
                cloud.stream(engineRequest).collect { delta ->
                    sb.append(delta)
                    firstToken.complete(true)
                    send(TranslationUpdate.Partial(sb.toString(), EngineKind.CLOUD_BYOK))
                }
                cloudText = sb.toString().trim()
                if (!firstToken.complete(false) && cloudText.isEmpty()) midStreamFailure.set(true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (!firstToken.complete(false)) midStreamFailure.set(true)
            }
        }

        var provisional: String? = null
        val provisionalJob = launch {
            delay(PROVISIONAL_AFTER_MS)
            if (firstToken.isCompleted) return@launch
            val local = onDeviceText() ?: return@launch
            provisional = local
            if (!firstToken.isCompleted) send(TranslationUpdate.Partial(local, EngineKind.ON_DEVICE))
        }

        val gotToken = withTimeoutOrNull(CLOUD_FIRST_TOKEN_TIMEOUT_MS) { firstToken.await() } ?: false
        if (!gotToken) {
            cloudJob.cancel()
            provisionalJob.join()
            finishOnDevice(fellBack = true, cached = provisional)
            return@channelFlow
        }
        provisionalJob.cancel()
        cloudJob.join()
        if (midStreamFailure.get()) {
            finishOnDevice(fellBack = true)
        } else {
            send(TranslationUpdate.Final(cloudText, EngineKind.CLOUD_BYOK, source))
        }
    }

    /** 읽기 표기(후리가나): 클라우드 엔진이 있으면 사용. 없으면 null → UI 는 오프라인 표기만. */
    suspend fun readings(text: String, language: Language): List<RubySegment>? =
        if (network.isOnline()) runCatching { cloudEngineOrNull()?.readings(text, language) }.getOrNull() else null

    private suspend fun cloudEngineOrNull(): TranslationEngine? {
        val s = settings.settings.first()
        if (!s.cloudEnabled) return null
        val key = keys.getKey(s.provider) ?: return null
        return cloudFactory.get(s.provider, key, s.model, s.baseUrl)
    }

    companion object {
        const val PROVISIONAL_AFTER_MS = 600L
        const val CLOUD_FIRST_TOKEN_TIMEOUT_MS = 4_000L
    }
}

/** 동일 설정이면 HTTP 클라이언트를 재사용 (연결 재사용 → 첫 토큰 지연 단축). */
class CloudEngineFactory {
    private var cachedKey: List<String>? = null
    private var cached: TranslationEngine? = null

    @Synchronized
    fun get(provider: CloudProvider, apiKey: String, model: String, baseUrl: String): TranslationEngine {
        val id = listOf(provider.name, apiKey.hashCode().toString(), model, baseUrl)
        cached?.takeIf { cachedKey == id }?.let { return it }
        val engine = when (provider) {
            CloudProvider.ANTHROPIC -> AnthropicEngine(apiKey, model.ifBlank { provider.defaultModel }, baseUrl.ifBlank { provider.defaultBaseUrl })
            CloudProvider.OPENAI_COMPATIBLE -> OpenAiCompatibleEngine(apiKey, model, baseUrl)
        }
        cachedKey = id
        cached = engine
        return engine
    }
}
