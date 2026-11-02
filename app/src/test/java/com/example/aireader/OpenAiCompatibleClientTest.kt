package com.example.aireader

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatibleClient
    private val key = "test-secret-key"

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatibleClient(allowHttpForTests = true)
    }

    @After fun tearDown() { server.shutdown() }

    private fun config(streaming: Boolean = false) = ApiConfig(
        baseUrl = server.url("/proxy/custom/v1").toString(), modelId = "test-model", streaming = streaming)

    private fun response(text: String) = """{"choices":[{"message":{"content":"$text"}}]}"""
    private fun sse(text: String) = """data: {"choices":[{"delta":{"content":"$text"},"finish_reason":null}]}""" + "\n\n"

    @Test fun baseUrlPreservesCustomPathAndAppendsEndpoint() {
        assertEquals("/proxy/custom/v1/chat/completions", client.endpoint(config().baseUrl).encodedPath)
        assertEquals("/proxy/custom/v1/chat/completions",
            client.endpoint(server.url("/proxy/custom/v1/").toString()).encodedPath)
    }

    @Test fun completeEndpointIsNotAppendedTwice() {
        assertEquals("/proxy/v1/chat/completions",
            client.endpoint(server.url("/proxy/v1/chat/completions").toString()).encodedPath)
        assertEquals("/proxy/v1/chat/completions",
            client.endpoint(server.url("/proxy/v1/chat/completions/").toString()).encodedPath)
    }

    @Test fun v1IsNotRepeated() {
        val path = client.endpoint(server.url("/v1/").toString()).encodedPath
        assertEquals("/v1/chat/completions", path)
    }

    @Test fun onlyOneUserMessageContainsCompletePromptAndProcessedArticle() {
        val processed = ArticlePreprocessor.process("甲。\n乙。", PreprocessOptions())
        val message = buildUserMessage("完整提示词\n", processed)
        val request = client.buildRequest(config(), key, message)
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        val body = JSONObject(buffer.readUtf8())
        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("完整提示词\n\n{段落1} 甲。乙。", messages.getJSONObject(0).getString("content"))
    }

    @Test fun temperatureCanBeOmittedAndMaxTokensIsCorrect() {
        val request = client.buildRequest(config().copy(sendTemperature = false, maxOutputTokens = 8192), key, "正文")
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        val json = JSONObject(buffer.readUtf8())
        assertFalse(json.has("temperature"))
        assertEquals(8192, json.getInt("max_tokens"))
        assertFalse(json.has("max_completion_tokens"))
    }

    @Test fun maxCompletionTokensCanBeSelected() {
        val request = client.buildRequest(config().copy(useMaxCompletionTokens = true), key, "正文")
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        val json = JSONObject(buffer.readUtf8())
        assertTrue(json.has("max_completion_tokens"))
        assertFalse(json.has("max_tokens"))
    }

    @Test fun nonStreamingResponseIsParsed() = runBlocking {
        server.enqueue(MockResponse().setBody(response("阅读报告")))
        val text = client.complete(config(), key, "正文").filterIsInstance<ReadingEvent.Text>()
            .map { it.value }.toList().joinToString("")
        assertEquals("阅读报告", text)
    }

    @Test fun streamingTextIsAppendedInOrderWithMultipleEvents() = runBlocking {
        val body = sse("你好") + sse("，世界") + "data: [DONE]\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val events = client.complete(config(streaming = true), key, "正文").toList()
        assertEquals("你好，世界", events.filterIsInstance<ReadingEvent.Text>().joinToString("") { it.value })
        assertEquals(ReadingEvent.Finished, events.last())
    }

    @Test fun sseWorksAcrossNetworkChunkBoundaries() = runBlocking {
        val body = sse("中文") + "data: [DONE]\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setChunkedBody(body, 1))
        val text = client.complete(config(streaming = true), key, "正文")
            .filterIsInstance<ReadingEvent.Text>().map { it.value }.toList().joinToString("")
        assertEquals("中文", text)
    }

    @Test fun sseAcceptsCrLfAndIgnoresEmptyDelta() = runBlocking {
        val body = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":null}]}\r\n\r\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"中文\"},\"finish_reason\":null}]}\r\n\r\n" +
            "data: [DONE]\r\n\r\n"
        server.enqueue(MockResponse().setBody(body))
        val text = client.complete(config(true), key, "正文").toList()
            .filterIsInstance<ReadingEvent.Text>().joinToString("") { it.value }
        assertEquals("中文", text)
    }

    @Test fun doneEndsStreamNormally() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("完成") + "data: [DONE]\n\n"))
        assertEquals(ReadingEvent.Finished, client.complete(config(true), key, "正文").toList().last())
    }

    @Test fun finishReasonCanEndStreamWithoutDone() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("完成") +
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"))
        assertEquals(ReadingEvent.Finished, client.complete(config(true), key, "正文").toList().last())
    }

    @Test fun missingDoneAndFinishReasonIsAnError() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("部分内容")))
        val error = runCatching { client.complete(config(true), key, "正文").toList() }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertTrue(ApiErrorMapper.message(error!!).contains("连接中断"))
    }

    @Test fun cancellationStopsReadingAndCancelsCall() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("第一段") + sse("第二段") + "data: [DONE]\n\n"))
        var call: okhttp3.Call? = null
        val first = client.complete(config(true), key, "正文", onCallCreated = { call = it }).take(1).toList()
        assertEquals(1, first.size)
        withTimeout(2_000) { while (call?.isCanceled() != true) delay(10) }
        assertTrue(call?.isCanceled() == true)
    }

    @Test fun oldRequestGenerationCannotUpdateNewRequest() {
        val generation = RequestGeneration()
        val old = generation.next()
        generation.invalidate()
        val current = generation.next()
        assertFalse(generation.isCurrent(old))
        assertTrue(generation.isCurrent(current))
    }

    @Test fun http401MapsToKeyHintWithoutExposingBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret response body"))
        val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("API Key"))
        assertFalse(ApiErrorMapper.message(error!!).contains("secret response body"))
    }

    @Test fun http429MentionsPossibleRateOrQuotaLimit() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("可能"))
    }

    @Test fun http500GetsFriendlyMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("500"))
    }

    @Test fun invalidJsonIsReported() = runBlocking {
        server.enqueue(MockResponse().setBody("not json"))
        val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("JSON"))
    }

    @Test fun malformedSseAndServerErrorEventsAreReported() = runBlocking {
        server.enqueue(MockResponse().setBody("data: {bad json}\n\n"))
        val malformed = runCatching { client.complete(config(true), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(malformed!!).contains("流式响应格式"))

        server.enqueue(MockResponse().setBody("event: error\ndata: {\"error\":\"rejected\"}\n\n"))
        val rejected = runCatching { client.complete(config(true), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(rejected!!).contains("服务端"))
    }

    @Test fun emptyResponseIsReported() = runBlocking {
        server.enqueue(MockResponse().setBody(response("")))
        val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("空内容"))
    }

    @Test fun knownHttpErrorsGetSpecificHints() {
        assertTrue(ApiErrorMapper.httpStatus(400).contains("参数"))
        assertTrue(ApiErrorMapper.httpStatus(403).contains("权限"))
        assertTrue(ApiErrorMapper.httpStatus(404).contains("地址"))
        assertTrue(ApiErrorMapper.httpStatus(408).contains("超时"))
        assertTrue(ApiErrorMapper.httpStatus(502).contains("暂时不可用"))
    }

    @Test fun connectionTestUsesIndependentShortMessage() = runBlocking {
        server.enqueue(MockResponse().setBody(response("OK")))
        client.testConnection(config(streaming = true), key)
        val recorded = server.takeRequest()
        val json = JSONObject(recorded.body.readUtf8())
        assertEquals("请只回复 OK。", json.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("test-model", json.getString("model"))
        assertEquals("Bearer $key", recorded.getHeader("Authorization"))
        assertFalse(json.getBoolean("stream"))
        assertEquals(16, json.getInt("max_tokens"))
    }

    @Test fun keyIsOnlyInAuthorizationHeaderAndNeverInErrorText() {
        val request = client.buildRequest(config(), key, "正文")
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        assertFalse(buffer.readUtf8().contains(key))
        assertEquals("Bearer $key", request.header("Authorization"))
        assertFalse(ApiErrorMapper.message(IllegalStateException(key)).contains(key))
    }

    @Test fun redirectsDoNotSendAuthorizationToAnotherHost() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", other.url("/stolen")))
            val error = runCatching { client.complete(config(), key, "正文").toList() }.exceptionOrNull()
            assertTrue(ApiErrorMapper.message(error!!).contains("重定向"))
            assertNull(other.takeRequest(200, TimeUnit.MILLISECONDS))
        } finally { other.shutdown() }
    }

    @Test fun httpIsRejectedUnlessExplicitlyAllowedForTests() {
        val production = OpenAiCompatibleClient()
        val error = runCatching { production.endpoint(server.url("/v1").toString()) }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("HTTPS"))
    }
}
