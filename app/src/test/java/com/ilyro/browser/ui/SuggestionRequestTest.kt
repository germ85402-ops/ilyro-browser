package com.ilyro.browser.ui

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SuggestionRequestTest {
    @Test fun readsSuccessfulResponseAndRejectsOversizedPayload() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val data = "[\"test\",[\"test result\"]]".toByteArray()
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        server.createContext("/large") { exchange ->
            val data = ByteArray(70_000) { 65 }
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals("[\"test\",[\"test result\"]]", fetchSuggestionPayload("$base/ok"))
            assertNull(fetchSuggestionPayload("$base/large"))
        } finally { server.stop(0) }
    }

    @Test fun cancellingAnInFlightRequestDoesNotWaitForReadTimeout() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val request = async { fetchSuggestionPayload("http://127.0.0.1:${server.address.port}/slow") }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            val start = System.nanoTime()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }
}
