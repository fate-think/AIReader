package com.example.aireader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
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
import java.net.NoRouteToHostException
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
    val useMaxCompletionTokens: Boolean = false,
    val allowHttp: Boolean = false,
    val network: NetworkStabilitySettings = NetworkStabilitySettings(),
    val reasoning: ReasoningSettings = ReasoningSettings()
)

sealed interface ReadingEvent {
    data class Text(val value: String) : ReadingEvent
    data class RetryWaiting(val attempt: Int, val maxRetries: Int, val seconds: Int) : ReadingEvent
    data class Retrying(val attempt: Int, val maxRetries: Int) : ReadingEvent
    data object RetryExhausted : ReadingEvent
    data object Finished : ReadingEvent
}

open class ApiException(message: String) : IOException(message)

private class HttpFailure(val code: Int, val retryAfter: String?, val temporary: Boolean,
                          hint: String? = null) :
    ApiException(hint ?: ApiErrorMapper.httpStatus(code))

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
        504 -> "网关超时（HTTP 504）。上游请求可能仍在执行；手动重新生成或重试可能产生重复费用。"
        in 300..399 -> "服务返回重定向；为保护 API Key，已阻止自动跳转。"
        else -> "API 请求失败（HTTP $code），请检查配置或服务状态。"
    }
}

class OpenAiCompatibleClient(
    client: OkHttpClient = OkHttpClient.Builder().build(),
    private val allowHttpForTests: Boolean = false,
    private val retryWaiter: RetryWaiter = coroutineRetryWaiter,
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    fun endpoint(baseUrl: String, allowHttp: Boolean = false): HttpUrl {
        val url = baseUrl.trim().toHttpUrlOrNull()
            ?: throw ApiException("API Base URL 无效。")
        if (url.scheme != "https" && !(url.scheme == "http" && (allowHttp || allowHttpForTests))) {
            throw ApiException("API Base URL 必须使用 HTTPS；如需 HTTP，请在 API 设置中开启允许 HTTP Base URL。")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw ApiException("API Base URL 不能包含账号信息。")
        }
        val path = url.encodedPath.trimEnd('/')
        val endpointPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
        return url.newBuilder().encodedPath(endpointPath).build()
    }

    fun validate(config: ApiConfig, apiKey: String): HttpUrl {
        ApiKeyNormalizer.normalize(apiKey)
        try { config.network.validate() }
        catch (_: IllegalArgumentException) { throw ApiException("网络稳定性设置超出允许范围。") }
        if (config.modelId.isBlank()) throw ApiException("请先填写 Model ID。")
        if (config.maxOutputTokens <= 0) throw ApiException("Max Output Tokens 必须大于 0。")
        if (config.sendTemperature && (!config.temperature.isFinite() || config.temperature !in 0.0..2.0)) {
            throw ApiException("Temperature 应在 0 到 2 之间。")
        }
        return endpoint(config.baseUrl, config.allowHttp)
    }

    fun buildRequest(config: ApiConfig, apiKey: String, userMessage: String,
                     stream: Boolean = config.streaming): Request {
        val url = validate(config, apiKey)
        val normalizedKey = ApiKeyNormalizer.normalize(apiKey)
        val body = JSONObject()
            .put("model", config.modelId)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", userMessage)))
            .put("stream", stream)
            .put(if (config.useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", config.maxOutputTokens)
        if (config.sendTemperature) body.put("temperature", config.temperature)
        if (config.reasoning.sendReasoningEffort) {
            body.put("reasoning_effort", config.reasoning.reasoningEffort.apiValue)
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $normalizedKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    fun complete(config: ApiConfig, apiKey: String, userMessage: String,
                 onCallCreated: (Call) -> Unit = {}): Flow<ReadingEvent> = flow {
        val request = buildRequest(config, apiKey, userMessage)
        val settings = config.network
        val maxRetries = if (settings.autoRetry) settings.maxRetries else 0
        var retries = 0
        var cumulativeWait = 0L
        while (true) {
            var hasText = false
            val call = configuredClient(config).newCall(request)
            onCallCreated(call)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val temporary = when (response.code) {
                            500, 502, 503 -> true
                            504 -> settings.retryHttp504
                            429 -> !quotaFailure(response.peekBody(8192).string())
                            else -> false
                        }
                        val hint = if (response.code == 400 && config.reasoning.sendReasoningEffort)
                            reasoningRejection(response.peekBody(8192).string()) else null
                        throw HttpFailure(response.code, response.header("Retry-After"), temporary, hint)
                    }
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
                                else -> Unit
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
                return@flow
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                val safeToRetry = !hasText && (error is HttpFailure && error.temporary ||
                    error is ConnectException || error is UnknownHostException || error is NoRouteToHostException)
                if (!safeToRetry || maxRetries == 0) throw error
                if (retries >= maxRetries) {
                    emit(ReadingEvent.RetryExhausted)
                    throw error
                }
                val next = retries + 1
                val wait = retryPolicy.waitMillis(next, (error as? HttpFailure)?.retryAfter)
                    ?: throw ApiException("服务器要求等待超过 60 秒，请稍后手动重试。")
                if (cumulativeWait + wait > 120_000L) {
                    emit(ReadingEvent.RetryExhausted)
                    throw ApiException("自动重试等待时间已达上限，请稍后手动重试。")
                }
                retries = next
                cumulativeWait += wait
                emit(ReadingEvent.RetryWaiting(retries, maxRetries, ((wait + 999) / 1000).toInt()))
                retryWaiter.wait(wait)
                emit(ReadingEvent.Retrying(retries, maxRetries))
            } finally {
                call.cancel()
            }
        }
    }.flowOn(Dispatchers.IO).buffer(0)

    internal fun configuredClient(config: ApiConfig): OkHttpClient {
        val network = config.network
        network.validate()
        return http.newBuilder()
            .connectTimeout(network.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(network.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(if (config.streaming) 0L else
                (network.connectTimeoutSeconds + network.readTimeoutSeconds + 60).toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun quotaFailure(body: String): Boolean {
        val details = try {
            val error = JSONObject(body).optJSONObject("error")
            if (error == null) body.lowercase().take(8192)
            else listOf(error.optString("type"), error.optString("code"), error.optString("message"))
                .joinToString(" ").lowercase()
        } catch (_: JSONException) { body.lowercase().take(8192) }
        return listOf("insufficient_quota", "quota_exceeded", "billing", "balance", "credit", "payment")
            .any { it in details }
    }

    private fun reasoningRejection(body: String): String? {
        val lower = body.lowercase()
        return when {
            "reasoning_effort" in lower &&
                ("unknown" in lower || "unrecognized" in lower || "unsupported parameter" in lower) ->
                "当前 API 可能不支持思考深度参数，请在设置中关闭‘发送思考深度参数’。"
            "reasoning effort" in lower && "unsupported" in lower ->
                "当前模型可能不支持所选思考深度，请尝试其他档位。"
            else -> null
        }
    }

    suspend fun testConnection(config: ApiConfig, apiKey: String, onCallCreated: (Call) -> Unit = {}) {
        var receivedText = false
        complete(config.copy(network = config.network.copy(autoRetry = false)), apiKey,
            "请只回复 OK。", onCallCreated).collect { event ->
            if (event is ReadingEvent.Text && event.value.isNotEmpty()) receivedText = true
        }
        if (!receivedText) throw ApiException("测试连接未收到有效内容。")
    }
}
