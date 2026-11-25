package app.glook.translate.domain.model

import app.glook.translate.R
import app.glook.translate.core.str

/** 지원 언어. [code] 는 BCP-47 태그이며 ML Kit TranslateLanguage 코드와 호환됩니다. 표시 이름은 현재 앱 언어로 만듭니다. */
data class Language(val code: String) {
    val isAuto: Boolean get() = code == AUTO_CODE

    /** 현재 앱 언어(Locale) 기준 언어 이름. 자동 감지는 문자열 리소스. */
    val displayName: String
        get() {
            if (isAuto) return str(R.string.auto_detect)
            val loc = java.util.Locale.getDefault()
            val n = java.util.Locale.forLanguageTag(code).getDisplayLanguage(loc)
            return if (n.isBlank() || n == code) code else n.replaceFirstChar { it.titlecase(loc) }
        }

    companion object {
        const val AUTO_CODE = "auto"
        val Auto = Language(AUTO_CODE)

        /**
         * ML Kit Translation 17.0.3 이 지원하는 59개 언어 전부(TranslateLanguage 상수에서 확인). 많이 쓰는 24개를 앞에 둠.
         */
        val All: List<Language> = (
            "ko en ja zh es fr de it pt ru vi th id ms tl ar hi tr nl pl uk sv cs el " +
                "af be bg bn ca cy da eo et fa fi ga gl gu he hr ht hu is ka kn lt lv mk mr mt no ro sk sl sq sw ta te ur"
            ).split(' ').map { Language(it) }

        fun of(code: String): Language = if (code == AUTO_CODE) Auto else Language(code)
    }
}

/**
 * 원문의 화법(레지스터). 사용자가 고르는 옵션이 아니라, 엔진이 자동 판별하여
 * LLM 프롬프트 힌트로만 사용합니다. (UI 에 톤 선택 버튼 없음 — 요구사항 제외 사항 1)
 */
enum class Register(val promptHint: String) {
    BANMAL("casual intimate speech (Korean 반말) — use casual, friendly phrasing"),
    HAEYO("polite informal speech (Korean 해요체) — polite but warm, everyday phrasing"),
    HASIPSIO("formal deferential speech (Korean 하십시오체) — formal, respectful phrasing"),
    BUSINESS("business/professional register — concise, courteous, professional phrasing"),
    TECHNICAL("technical/specialist register — keep domain terms precise and consistent"),
    NEUTRAL("neutral written register — natural, faithful phrasing"),
}

data class TranslationRequest(
    val text: String,
    val source: Language,
    val target: Language,
)

/** 번역 엔진 식별자 (결과 카드에 작게 표기하고 히스토리에 기록). */
enum class EngineKind(@androidx.annotation.StringRes private val labelRes: Int) {
    ON_DEVICE(R.string.engine_ondevice),
    CLOUD_BYOK(R.string.engine_cloud);
    val label: String get() = str(labelRes)
}

/** 라우터가 UI 에 내보내는 스트리밍 업데이트. */
sealed interface TranslationUpdate {
    data class Detected(val source: Language) : TranslationUpdate
    /** 감지된 원문 언어가 대상 언어와 같아 대상 언어를 자동 전환한 경우. */
    data class Retargeted(val target: Language) : TranslationUpdate
    data class Partial(val text: String, val engine: EngineKind) : TranslationUpdate
    data class Final(
        val text: String,
        val engine: EngineKind,
        val source: Language,
        val fellBack: Boolean = false,
    ) : TranslationUpdate
    data class Failed(val message: String) : TranslationUpdate
}

data class HistoryEntry(
    val id: Long,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val engine: String,
    val createdAt: Long,
)

data class Bookmark(
    val id: Long,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val createdAt: Long,
)

/** BYOK 제공자. 새 제공자는 이 enum + [app.glook.translate.data.engine.TranslationEngine] 구현만 추가. */
enum class CloudProvider(private val labelRes: Int?, private val plainLabel: String, val defaultModel: String, val defaultBaseUrl: String) {
    ANTHROPIC(null, "Anthropic Claude", "claude-opus-5-5", "https://api.anthropic.com"),
    OPENAI_COMPATIBLE(R.string.provider_openai_compat, "", "", "https://");
    val label: String get() = labelRes?.let { str(it) } ?: plainLabel
}

data class CloudEngineConfig(
    val provider: CloudProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
)

data class AppSettings(
    val secureScreen: Boolean = true,
    val sourceLang: String = Language.AUTO_CODE,
    val targetLang: String = "ko",
    val cloudEnabled: Boolean = false,
    val provider: CloudProvider = CloudProvider.ANTHROPIC,
    val model: String = CloudProvider.ANTHROPIC.defaultModel,
    val baseUrl: String = CloudProvider.ANTHROPIC.defaultBaseUrl,
    val saveHistory: Boolean = true,
    /** 최근 사용한 언어 코드 (최신순, 최대 5). */
    val recentLangs: List<String> = emptyList(),
    val autoSpeak: Boolean = false,
    val autoCopy: Boolean = false,
    val clipboardSuggest: Boolean = true,
    val haptics: Boolean = true,
)
