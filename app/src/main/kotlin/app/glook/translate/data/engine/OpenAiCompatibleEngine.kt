package app.glook.translate.data.engine

import app.glook.translate.R
import app.glook.translate.core.str

import app.glook.translate.domain.model.EngineKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BYOK: "/chat/completions" SSE 형식을 따르는 임의의 제공자 (OpenAI, OpenRouter, 자체 호스팅 vLLM/Ollama 게이트웨이 등).
 * baseUrl 예: https://api.openai.com/v1
 */
class OpenAiCompatibleEngine(
    private val apiKey: String,
    private val model: String,
    baseUrl: String,
) : TranslationEngine {
    override val kind = EngineKind.CLOUD_BYOK
    private val endpoint = baseUrl.trimEnd('/') + "/chat/completions"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override fun stream(request: EngineRequest): Flow<String> = flow {
        require(endpoint.startsWith("https://")) { str(R.string.https_only) }
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", PromptBuilder.system(request)))
                .put(JSONObject().put("role", "user").put("content", PromptBuilder.user(request))))
            .toString()
        val call = http.newCall(
            Request.Builder().url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "text/event-stream")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build(),
        )
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw EngineException(str(R.string.cloud_error_http_fmt, resp.code))
                val source = resp.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta")?.optString("content").orEmpty()
                    if (delta.isNotEmpty()) emit(delta)
                    if (choice.optString("finish_reason") == "content_filter") throw EngineException(str(R.string.cloud_refused))
                }
            }
        } finally {
            handle.dispose()
        }
    }.flowOn(Dispatchers.IO)
}
