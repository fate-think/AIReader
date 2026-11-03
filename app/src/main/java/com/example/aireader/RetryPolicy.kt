package com.example.aireader

import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

data class NetworkStabilitySettings(
    val autoRetry: Boolean = true,
    val maxRetries: Int = 2,
    val connectTimeoutSeconds: Int = 20,
    val readTimeoutSeconds: Int = 180,
    val retryHttp504: Boolean = false
) {
    fun validate() {
        require(maxRetries in 0..5)
        require(connectTimeoutSeconds in 10..60)
        require(readTimeoutSeconds in 30..600)
    }
}

fun interface RetryWaiter {
    suspend fun wait(millis: Long)
}

val coroutineRetryWaiter = RetryWaiter { delay(it) }

class RetryPolicy(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val jitterMillis: () -> Long = { (0L..250L).random() }
) {
    /** null means the server asked to wait longer than the permitted automatic retry window. */
    fun waitMillis(retryNumber: Int, retryAfter: String?): Long? {
        require(retryNumber in 1..5)
        val requested = retryAfterMillis(retryAfter)
        if (requested != null && requested > 60_000L) return null
        val base = requested ?: (2_000L shl (retryNumber - 1))
        return (base + jitterMillis().coerceIn(0L, 250L)).coerceAtMost(60_000L)
    }

    fun retryAfterMillis(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        text.toLongOrNull()?.let { seconds ->
            return if (seconds < 0) null else if (seconds > 60) 60_001L else seconds * 1000L
        }
        return try {
            val date = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME
                .withLocale(Locale.US)).toInstant().toEpochMilli()
            max(0L, date - nowMillis())
        } catch (_: Exception) { null }
    }
}
