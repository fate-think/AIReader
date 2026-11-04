package com.example.aireader

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReasoningEffortTest {
    private val client = OpenAiCompatibleClient(allowHttpForTests = true)

    private fun json(config: ApiConfig, message: String = "完整提示词\n\n预处理文章"): JSONObject {
        val buffer = Buffer()
        client.buildRequest(config, "test-key", message).body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun config(settings: ReasoningSettings = ReasoningSettings(), streaming: Boolean = true,
                       modelId: String = "glm-5.3") = ApiConfig(
        baseUrl = "https://example.com/v1", modelId = modelId, temperature = 0.7,
        maxOutputTokens = 8192, streaming = streaming, reasoning = settings)

    @Test fun defaultAndDisabledSwitchOmitTheFieldCompletely() {
        assertEquals(ReasoningSettings(false, ReasoningEffort.MAX), ApiConfig().reasoning)
        for (effort in ReasoningEffort.entries) {
            val body = json(config(ReasoningSettings(false, effort)))
            assertFalse(body.has("reasoning_effort"))
            assertFalse(body.toString().contains("\"reasoning_effort\":null"))
            assertFalse(body.toString().contains("\"reasoning_effort\":\"\""))
        }
    }

    @Test fun sevenValuesAreSentExactlyAsSelected() {
        val expected = listOf("max", "xhigh", "high", "medium", "low", "minimal", "none")
        assertEquals(expected, ReasoningEffort.entries.map { it.apiValue })
        ReasoningEffort.entries.forEachIndexed { index, effort ->
            val body = json(config(ReasoningSettings(true, effort)))
            assertEquals(expected[index], body.getString("reasoning_effort"))
            assertFalse(body.toString().contains("\"reasoning_effort\":\"auto\""))
        }
    }

    @Test fun streamModesAndUnknownModelUseTheSameTopLevelField() {
        for (streaming in listOf(true, false)) {
            val body = json(config(ReasoningSettings(true, ReasoningEffort.MINIMAL),
                streaming = streaming, modelId = "unknown-provider-model"))
            assertEquals("minimal", body.getString("reasoning_effort"))
            assertEquals(streaming, body.getBoolean("stream"))
            assertEquals("unknown-provider-model", body.getString("model"))
            assertEquals(8192, body.getInt("max_tokens"))
            assertEquals(0.7, body.getDouble("temperature"), 0.0)
            val messages = body.getJSONArray("messages")
            assertEquals(1, messages.length())
            assertEquals("user", messages.getJSONObject(0).getString("role"))
            assertEquals("完整提示词\n\n预处理文章", messages.getJSONObject(0).getString("content"))
        }
    }

    @Test fun alternateTokenFieldAndMessageBuilderStayIntact() {
        val processed = ArticlePreprocessor.process("甲。\n乙。", PreprocessOptions())
        assertEquals("{段落1} 甲。乙。", processed)
        val message = buildUserMessage("提示词\n", processed)
        assertEquals("提示词\n{段落1} 甲。乙。", message)
        val body = json(config(ReasoningSettings(true, ReasoningEffort.NONE))
            .copy(useMaxCompletionTokens = true, sendTemperature = false), message)
        assertEquals("none", body.getString("reasoning_effort"))
        assertEquals(8192, body.getInt("max_completion_tokens"))
        assertFalse(body.has("max_tokens"))
        assertFalse(body.has("temperature"))
        assertEquals(message, body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun connectionTestSendsTheSelectedEffortWithShortMessage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))
            client.testConnection(config(ReasoningSettings(true, ReasoningEffort.XHIGH), streaming = false)
                .copy(baseUrl = server.url("/v1").toString()), "test-key")
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("xhigh", body.getString("reasoning_effort"))
            assertEquals("请只回复 OK。", body.getJSONArray("messages").getJSONObject(0).getString("content"))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun explicitReasoningRejectionIsHelpfulAndDoesNotRetryOrDowngrade() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val selected = ReasoningSettings(true, ReasoningEffort.MAX)
            val requestConfig = config(selected, streaming = false).copy(baseUrl = server.url("/v1").toString())
            server.enqueue(MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"Unknown parameter: reasoning_effort"}}"""))
            val unsupportedParameter = runCatching {
                client.complete(requestConfig, "test-key", "正文").collect { }
            }.exceptionOrNull()
            assertTrue(ApiErrorMapper.message(unsupportedParameter!!).contains("关闭‘发送思考深度参数’"))
            assertEquals(1, server.requestCount)
            assertEquals(selected, requestConfig.reasoning)

            server.enqueue(MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"Unsupported reasoning effort: max"}}"""))
            val unsupportedValue = runCatching {
                client.complete(requestConfig, "test-key", "正文").collect { }
            }.exceptionOrNull()
            assertTrue(ApiErrorMapper.message(unsupportedValue!!).contains("其他档位"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
