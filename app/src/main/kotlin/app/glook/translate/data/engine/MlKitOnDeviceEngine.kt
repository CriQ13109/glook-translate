package app.glook.translate.data.engine

import app.glook.translate.R
import app.glook.translate.core.str

import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Google ML Kit 온디바이스 번역 (오프라인·무료·텍스트가 기기를 떠나지 않음).
 * 스트리밍이 없으므로 전체 결과를 한 번에 emit 합니다. 화법 제어 기능은 없습니다.
 */
class MlKitOnDeviceEngine(private val network: NetworkMonitor) : TranslationEngine {
    override val kind = EngineKind.ON_DEVICE

    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build(),
    )
    private val modelManager = RemoteModelManager.getInstance()
    private val translators = LinkedHashMap<Pair<String, String>, Translator>()
    private val lock = Mutex()

    /** 원문 언어 자동 감지. 지원하지 않거나 불확실하면 null. */
    suspend fun detect(text: String): Language? {
        val tag = languageId.identifyLanguage(text.take(500)).await()
        if (tag == "und") return null
        val code = TranslateLanguage.fromLanguageTag(tag.substringBefore('-')) ?: return null
        return Language.of(code)
    }

    suspend fun isModelReady(code: String): Boolean {
        if (code == TranslateLanguage.ENGLISH) return true // 영어 모델은 기본 내장
        return modelManager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()
    }

    /** 오프라인 + 모델 없음 → 즉시 실패 (폴백 판단용). */
    suspend fun canTranslateNow(src: String, tgt: String): Boolean =
        network.isOnline() || (isModelReady(src) && isModelReady(tgt))

    override fun stream(request: EngineRequest): Flow<String> = flow {
        val src = TranslateLanguage.fromLanguageTag(request.source.code)
            ?: throw EngineException(str(R.string.ondevice_unsupported_fmt, request.source.displayName))
        val tgt = TranslateLanguage.fromLanguageTag(request.target.code)
            ?: throw EngineException(str(R.string.ondevice_unsupported_fmt, request.target.displayName))
        if (!canTranslateNow(src, tgt)) {
            throw EngineException(str(R.string.offline_model_missing))
        }
        val translator = translator(src, tgt)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        emit(translator.translate(request.text).await())
    }

    private suspend fun translator(src: String, tgt: String): Translator = lock.withLock {
        translators.getOrPut(src to tgt) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
        }.also {
            // 최근 4쌍만 유지 (모델당 메모리 수십 MB)
            while (translators.size > 4) translators.remove(translators.keys.first())?.close()
        }
    }

    /** Wi-Fi 에서 자주 쓰는 쌍의 모델을 미리 받아 두어 첫 오프라인 폴백이 끊기지 않게 함. */
    suspend fun prefetch(vararg codes: String) {
        val wifi = DownloadConditions.Builder().requireWifi().build()
        for (c in codes.distinct()) {
            val code = TranslateLanguage.fromLanguageTag(c) ?: continue
            if (code == TranslateLanguage.ENGLISH) continue
            runCatching { modelManager.download(TranslateRemoteModel.Builder(code).build(), wifi).await() }
        }
    }
}
