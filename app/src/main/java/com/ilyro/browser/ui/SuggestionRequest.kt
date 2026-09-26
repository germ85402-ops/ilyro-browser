package com.ilyro.browser.ui

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private val suggestionRequests = ThreadPoolExecutor(
    2, 2, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(16),
    { task -> Thread(task, "ILYRO-suggestions").apply { isDaemon = true } }
).apply { allowCoreThreadTimeOut(true) }
private val suggestionDisconnects = Executors.newSingleThreadExecutor { task ->
    Thread(task, "ILYRO-suggestion-cleanup").apply { isDaemon = true }
}

/** Cancellation closes the connection even while a worker is waiting for response headers. */
internal suspend fun fetchSuggestionPayload(endpoint: String): String? = suspendCancellableCoroutine { continuation ->
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 1600
        readTimeout = 1800
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "ILYRO Android")
    }
    val future = try {
        suggestionRequests.submit {
            val result = try {
                if (!continuation.isActive || connection.responseCode !in 200..299) null
                else connection.inputStream.bufferedReader().use { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(2048)
                    while (continuation.isActive) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (text.length + count > 65_536) return@use null
                        text.append(buffer, 0, count)
                    }
                    text.toString().takeIf { continuation.isActive }
                }
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
            continuation.resume(result)
        }
    } catch (_: java.util.concurrent.RejectedExecutionException) {
        connection.disconnect()
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    continuation.invokeOnCancellation {
        future.cancel(true)
        (future as? Runnable)?.let(suggestionRequests::remove)
        // Disconnect can wait for a socket lock; never do that on the UI thread.
        suggestionDisconnects.execute { connection.disconnect() }
    }
}
