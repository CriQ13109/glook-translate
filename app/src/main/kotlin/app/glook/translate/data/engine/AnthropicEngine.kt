package app.glook.translate.data.engine

import app.glook.translate.R
import app.glook.translate.core.str

import app.glook.translate.domain.model.EngineKind
import app.glook.translate.domain.model.Language
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * BYOK: Anthropic Messages API (공식 anthropic-java SDK, SSE 스트리밍).
 * 사용자의 API 키로 기기에서 Anthropic 으로 직접 요청하며, 중간 서버는 없습니다.
 */
class AnthropicEngine(
    apiKey: String,
    private val model: String,
    baseUrl: String,
) : TranslationEngine {
    override val kind = EngineKind.CLOUD_BYOK

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .maxRetries(0) // 재시도 대신 라우터가 즉시 온디바이스로 폴백
        .build()

    private fun baseParams(system: String, user: String, maxTokens: Long) = MessageCreateParams.builder()
        .model(model)
        .maxTokens(maxTokens)
        .system(system)
        .addUserMessage(user)
        // 번역은 지연 시간이 가장 중요 → 낮은 effort
        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        // 서버 측 거절(refusal) 폴백: 거절 카테고리에 맞는 모델로 서버가 자동 재시도
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    override fun stream(request: EngineRequest): Flow<String> = flow {
        val params = baseParams(PromptBuilder.system(request), PromptBuilder.user(request), 8192L).build()
        val response = client.messages().createStreaming(params)
        // 코루틴 취소 시 HTTP 스트림을 닫아 블로킹 읽기를 즉시 해제
        val handle = currentCoroutineContext().job.invokeOnCompletion { runCatching { response.close() } }
        try {
            val events = response.stream().iterator()
            while (events.hasNext()) {
                val event = events.next()
                val delta = event.contentBlockDelta().flatMap { it.delta().text() }.map { it.text() }.orElse(null)
                if (!delta.isNullOrEmpty()) emit(delta)
                val stop = event.messageDelta().flatMap { it.delta().stopReason() }.orElse(null)
                if (stop == StopReason.REFUSAL) throw EngineException(str(R.string.cloud_refused))
            }
        } finally {
            handle.dispose()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun readings(text: String, language: Language): List<RubySegment>? {
        if (language.code != "ja") return null
        return withContext(Dispatchers.IO) {
            val msg = client.messages().create(baseParams(PromptBuilder.readingsSystem(), "<source>\n$text\n</source>", 4096L).build())
            if (msg.stopReason().orElse(null) == StopReason.REFUSAL) return@withContext null
            val raw = msg.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("")
            parseSegments(raw, text)
        }
    }

    companion object {
        fun parseSegments(raw: String, original: String): List<RubySegment>? = runCatching {
            val json = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
            val arr = json.getJSONArray("segments")
            val segs = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                RubySegment(o.getString("t"), o.optString("r").ifBlank { null })
            }
            // 원문과 정확히 일치하지 않으면 신뢰하지 않음
            segs.takeIf { s -> s.joinToString("") { it.base } == original }
        }.getOrNull()
    }
}
