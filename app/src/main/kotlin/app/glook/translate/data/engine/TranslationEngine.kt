package app.glook.translate.data.engine

import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import app.glook.translate.domain.model.Register
import kotlinx.coroutines.flow.Flow

/** 엔진에 전달되는 확정된(자동 감지 해소된) 요청. */
data class EngineRequest(
    val text: String,
    val source: Language,
    val target: Language,
    val register: Register,
)

/** 후리가나/병음 표시용 루비 세그먼트. reading 이 null 이면 읽기 표기 없음. */
data class RubySegment(val base: String, val reading: String?)

/**
 * 번역 엔진 플러그인 인터페이스.
 * 새 백엔드(DeepL, Google Cloud, 자체 호스팅 LLM 등)는 이 인터페이스만 구현하면 라우터에 연결됩니다.
 */
interface TranslationEngine {
    val kind: EngineKind

    /** 번역문 "증분(delta)" 을 스트리밍. 비스트리밍 엔진은 전체 결과를 한 번에 emit. */
    fun stream(request: EngineRequest): Flow<String>

    /** 일본어 후리가나 등 읽기 표기. 지원하지 않으면 null. */
    suspend fun readings(text: String, language: Language): List<RubySegment>? = null
}

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
