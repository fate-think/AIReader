package com.example.aireader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class ApiConfig(
    val baseUrl: String = "",
    val modelId: String = "",
    val temperature: Double = 0.3,
    val maxOutputTokens: Int = 4096,
    val streaming: Boolean = true,
    val sendTemperature: Boolean = true,
    val useMaxCompletionTokens: Boolean = false
)

sealed interface ReadingEvent {
    data class Text(val value: String) : ReadingEvent
    data object Finished : ReadingEvent
}

class ApiException(message: String) : IOException(message)

object ApiErrorMapper {
    fun message(error: Throwable): String = when (error) {
        is ApiException -> error.message ?: "API 请求失败。"
        is UnknownHostException -> "无法解析 API 主机地址，请检查网络和 API 地址。"
        is SSLException -> "TLS 连接或证书验证失败，请检查 API 地址和证书。"
        is SocketTimeoutException -> "请求超时，请稍后重试或检查服务状态。"
        is ConnectException -> "无法连接 API 服务，请检查网络和地址。"
        is JSONException -> "API 返回的 JSON 格式无效。"
        is IOException -> "网络连接中断，请检查网络后重试。"
        else -> "请求失败，请检查 API 配置后重试。"
    }

    fun httpStatus(code: Int): String = when (code) {
        400 -> "请求参数不被服务接受，请检查模型及参数设置。"
        401 -> "认证失败，请检查 API Key。"
        403 -> "服务拒绝访问，请检查密钥权限。"
        404 -> "接口不存在，请检查 API 地址、路径及模型。"
        408 -> "服务端请求超时，请稍后重试。"
        429 -> "请求过于频繁，可能触及速率限制或额度限制。"
        500, 502, 503 -> "API 服务暂时不可用（HTTP $code），请稍后重试。"
        in 300..399 -> "服务返回重定向；为保护 API Key，已阻止自动跳转。"
        else -> "API 请求失败（HTTP $code），请检查配置或服务状态。"
    }
}

class OpenAiCompatibleClient(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build(),
    private val allowHttpForTests: Boolean = false
) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    fun endpoint(baseUrl: String): HttpUrl {
        val url = baseUrl.trim().toHttpUrlOrNull()
            ?: throw ApiException("API Base URL 无效。")
        if (url.scheme != "https" && !(allowHttpForTests && url.scheme == "http")) {
            throw ApiException("API Base URL 必须使用 HTTPS。")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw ApiException("API Base URL 不能包含账号信息。")
        }
        val path = url.encodedPath.trimEnd('/')
        val endpointPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
        return url.newBuilder().encodedPath(endpointPath).build()
    }

    fun validate(config: ApiConfig, apiKey: String): HttpUrl {
        if (apiKey.isBlank()) throw ApiException("请先配置 API Key。")
        if (config.modelId.isBlank()) throw ApiException("请先填写 Model ID。")
        if (config.maxOutputTokens <= 0) throw ApiException("Max Output Tokens 必须大于 0。")
        if (config.sendTemperature && (!config.temperature.isFinite() || config.temperature !in 0.0..2.0)) {
            throw ApiException("Temperature 应在 0 到 2 之间。")
        }
        return endpoint(config.baseUrl)
    }

    fun buildRequest(config: ApiConfig, apiKey: String, userMessage: String,
                     stream: Boolean = config.streaming): Request {
        val url = validate(config, apiKey)
        val body = JSONObject()
            .put("model", config.modelId)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", userMessage)))
            .put("stream", stream)
            .put(if (config.useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", config.maxOutputTokens)
        if (config.sendTemperature) body.put("temperature", config.temperature)
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    fun complete(config: ApiConfig, apiKey: String, userMessage: String,
                 onCallCreated: (Call) -> Unit = {}): Flow<ReadingEvent> = flow {
        val call = http.newCall(buildRequest(config, apiKey, userMessage))
        onCallCreated(call)
        var hasText = false
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw ApiException(ApiErrorMapper.httpStatus(response.code))
                val body = response.body ?: throw ApiException("API 返回空响应。")
                if (config.streaming) {
                    for (event in SseParser.events(body.charStream().buffered())) {
                        when (event) {
                            is ReadingEvent.Text -> {
                                hasText = hasText || event.value.isNotEmpty()
                                emit(event)
                            }
                            ReadingEvent.Finished -> {
                                if (!hasText) throw ApiException("API 返回空内容。")
                                emit(ReadingEvent.Finished)
                            }
                        }
                    }
                } else {
                    val text = try {
                        val raw = body.string()
                        if (raw.isBlank()) throw ApiException("API 返回空内容。")
                        val root = JSONObject(raw)
                        if (root.has("error")) throw ApiException("服务端报告请求失败，请检查配置。")
                        val message = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        if (message.isNull("content")) "" else message.getString("content")
                    } catch (_: JSONException) {
                        throw ApiException("API 返回的 JSON 格式无效。")
                    }
                    if (text.isEmpty()) throw ApiException("API 返回空内容。")
                    emit(ReadingEvent.Text(text))
                    emit(ReadingEvent.Finished)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(config: ApiConfig, apiKey: String, onCallCreated: (Call) -> Unit = {}) {
        var receivedText = false
        complete(config.copy(streaming = false, maxOutputTokens = minOf(config.maxOutputTokens, 16)),
            apiKey, "请只回复 OK。", onCallCreated).collect { event ->
            if (event is ReadingEvent.Text && event.value.isNotEmpty()) receivedText = true
        }
        if (!receivedText) throw ApiException("测试连接未收到有效内容。")
    }
}
