package com.example.aireader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException

class NetworkRetryTest {
    private lateinit var server: MockWebServer
    private val waits = mutableListOf<Long>()
    private val policy = RetryPolicy(nowMillis = { 1_700_000_000_000L }, jitterMillis = { 0L })
    private lateinit var client: OpenAiCompatibleClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OpenAiCompatibleClient(allowHttpForTests = true,
            retryWaiter = RetryWaiter { waits += it }, retryPolicy = policy)
    }

    @After fun tearDown() { server.shutdown() }

    private fun config(network: NetworkStabilitySettings = NetworkStabilitySettings(),
                       streaming: Boolean = false) = ApiConfig(server.url("/v1").toString(), "model-a",
        streaming = streaming, network = network)

    private fun ok() = MockResponse().setBody("""{"choices":[{"message":{"content":"完成"}}]}""")
    private fun sse(content: String) =
        "data: {\"choices\":[{\"delta\":{\"content\":\"$content\"},\"finish_reason\":null}]}\n\n"

    @Test fun defaultsAndConfiguredTimeouts() {
        assertEquals(NetworkStabilitySettings(), ApiConfig().network)
        val nonStream = client.configuredClient(config())
        assertEquals(20_000, nonStream.connectTimeoutMillis)
        assertEquals(180_000, nonStream.readTimeoutMillis)
        assertEquals(30_000, nonStream.writeTimeoutMillis)
        assertEquals(260_000, nonStream.callTimeoutMillis)
        val stream = client.configuredClient(config(streaming = true))
        assertEquals(0, stream.callTimeoutMillis)
        assertEquals(180_000, stream.readTimeoutMillis)
    }

    @Test fun temporary503And502RetryThenSucceed() = runBlocking {
        for (code in listOf(503, 502)) {
            server.enqueue(MockResponse().setResponseCode(code))
            server.enqueue(ok())
            assertTrue(client.complete(config(), "key", "正文").toList().contains(ReadingEvent.Finished))
        }
        assertEquals(4, server.requestCount)
        assertEquals(listOf(2000L, 2000L), waits)
    }

    @Test fun temporary429RetriesButQuota429DoesNot() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429)
            .setBody("""{"error":{"type":"rate_limit_error","code":"rate_limit_exceeded"}}"""))
        server.enqueue(ok())
        assertTrue(client.complete(config(), "key", "正文").toList().contains(ReadingEvent.Finished))
        val afterTemporary = server.requestCount
        server.enqueue(MockResponse().setResponseCode(429)
            .setBody("""{"error":{"type":"insufficient_quota","code":"billing_hard_limit_reached"}}"""))
        assertTrue(runCatching { client.complete(config(), "key", "正文").toList() }.isFailure)
        assertEquals(afterTemporary + 1, server.requestCount)
    }

    @Test fun clientErrorsAndDisabledRetryOnlySendOnce() = runBlocking {
        for (code in listOf(400, 401, 503)) {
            val before = server.requestCount
            server.enqueue(MockResponse().setResponseCode(code))
            val settings = NetworkStabilitySettings(autoRetry = code != 503)
            assertTrue(runCatching { client.complete(config(settings), "key", "正文").toList() }.isFailure)
            assertEquals(before + 1, server.requestCount)
        }
    }

    @Test fun retryAfterSecondsAndHttpDateNeverRetryEarly() {
        assertEquals(7000L, policy.waitMillis(1, "7"))
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(1_700_000_009_000L).atZone(ZoneOffset.UTC))
        assertEquals(9000L, policy.waitMillis(1, date))
        assertNull(policy.waitMillis(1, "120"))
    }

    @Test fun oversizedRetryAfterStopsWithoutAnotherRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "120"))
        val error = runCatching { client.complete(config(), "key", "正文").toList() }.exceptionOrNull()
        assertTrue(ApiErrorMapper.message(error!!).contains("60 秒"))
        assertEquals(1, server.requestCount)
    }

    @Test fun serverRetryAfterOverridesExponentialBackoff() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "7"))
        server.enqueue(ok())
        client.complete(config(), "key", "正文").toList()
        assertEquals(listOf(7000L), waits)
        assertEquals(2, server.requestCount)
    }

    @Test fun exponentialBackoffAndMaximumRetries() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        val events = mutableListOf<ReadingEvent>()
        val error = runCatching { client.complete(config(), "key", "正文").collect { events += it } }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(2000L, 4000L), waits)
        assertTrue(events.last() == ReadingEvent.RetryExhausted)
    }

    @Test fun waitCanBeCancelledBeforeRetry() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val cancellable = OpenAiCompatibleClient(allowHttpForTests = true,
            retryWaiter = RetryWaiter { waiting.complete(Unit); never.await() }, retryPolicy = policy)
        server.enqueue(MockResponse().setResponseCode(503))
        val job = async { cancellable.complete(config(), "key", "正文").toList() }
        withTimeout(5000) { waiting.await() }
        job.cancelAndJoin()
        assertEquals(1, server.requestCount)
    }

    @Test fun partialSseAndSseErrorNeverRetry() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("部分内容")))
        val events = mutableListOf<ReadingEvent>()
        val enabled504 = NetworkStabilitySettings(retryHttp504 = true)
        assertTrue(runCatching { client.complete(config(enabled504, streaming = true), "key", "正文").collect {
            events += it
        } }.isFailure)
        assertEquals(listOf(ReadingEvent.Text("部分内容")), events)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody(sse("已生成") +
            "event: error\ndata: {\"error\":{\"code\":\"504\"}}\n\n"))
        assertTrue(runCatching { client.complete(config(enabled504, streaming = true), "key", "正文").toList() }.isFailure)
        assertEquals(2, server.requestCount)
    }

    @Test fun readTimeoutAndTlsFailureNeverRetry() = runBlocking {
        for (exception in listOf(SocketTimeoutException("read"), SSLHandshakeException("tls"))) {
            val attempts = AtomicInteger()
            val failing = OkHttpClient.Builder().addInterceptor { attempts.incrementAndGet(); throw exception }.build()
            val api = OpenAiCompatibleClient(failing, allowHttpForTests = true,
                retryWaiter = RetryWaiter { waits += it }, retryPolicy = policy)
            assertTrue(runCatching { api.complete(config(), "key", "正文").toList() }.isFailure)
            assertEquals(1, attempts.get())
        }
    }

    @Test fun confirmedConnectFailureCanRetry() = runBlocking {
        val attempts = AtomicInteger()
        val failing = OkHttpClient.Builder().addInterceptor { chain ->
            if (attempts.incrementAndGet() == 1) throw ConnectException("connection refused")
            chain.proceed(chain.request())
        }.build()
        val api = OpenAiCompatibleClient(failing, allowHttpForTests = true,
            retryWaiter = RetryWaiter { waits += it }, retryPolicy = policy)
        server.enqueue(ok())
        assertTrue(api.complete(config(), "key", "正文").toList().contains(ReadingEvent.Finished))
        assertEquals(2, attempts.get())
    }

    @Test fun retriesUseIdenticalRequestBodyAndHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(ok())
        client.complete(config().copy(reasoning = ReasoningSettings(true, ReasoningEffort.HIGH)),
            "key", "固定提示词\n\n固定文章").toList()
        val first = server.takeRequest()
        val second = server.takeRequest()
        val firstBody = first.body.readUtf8()
        assertEquals(firstBody, second.body.readUtf8())
        assertEquals("high", org.json.JSONObject(firstBody).getString("reasoning_effort"))
        assertEquals(first.getHeader("Authorization"), second.getHeader("Authorization"))
        assertEquals(first.path, second.path)
    }

    @Test fun testConnectionDoesNotRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(runCatching { client.testConnection(config(), "key") }.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test fun http504NeedsBothSwitchesAndRespectsMaximum() = runBlocking {
        val before = server.requestCount
        server.enqueue(MockResponse().setResponseCode(504))
        assertTrue(runCatching {
            client.complete(config(NetworkStabilitySettings(autoRetry = false, retryHttp504 = true)),
                "key", "正文").toList()
        }.isFailure)
        assertEquals(before + 1, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(504))
        assertTrue(runCatching { client.complete(config(), "key", "正文").toList() }.isFailure)
        assertEquals(before + 2, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(504))
        server.enqueue(ok())
        val allowed = NetworkStabilitySettings(maxRetries = 1, retryHttp504 = true)
        assertTrue(client.complete(config(allowed), "key", "正文").toList().contains(ReadingEvent.Finished))
        assertEquals(before + 4, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(504))
        server.enqueue(MockResponse().setResponseCode(504))
        assertTrue(runCatching { client.complete(config(allowed), "key", "正文").toList() }.isFailure)
        assertEquals(before + 6, server.requestCount)
        val recorded = (1..6).map { server.takeRequest() }
        assertEquals(recorded[2].body.readUtf8(), recorded[3].body.readUtf8())
    }
}
