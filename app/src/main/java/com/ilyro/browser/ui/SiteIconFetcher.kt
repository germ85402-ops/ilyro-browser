package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest

/**
 * Loads a site icon from the site itself.
 *
 * ILYRO must never ask a third-party icon service (for example Google's `s2/favicons` endpoint)
 * for an icon, because the request would disclose the pages a user opened to that service. Icons
 * are therefore always requested first-party, over HTTPS only, and through [GeckoWebExecutor]
 * whenever the Gecko runtime already exists, so content blocking, cookie isolation and
 * private-browsing isolation apply to icon traffic as well.
 */
internal object SiteIconFetcher {
    private const val LOG_TAG = "ILYRO-SiteIcon"
    private const val MAX_ICON_BYTES = 768 * 1024
    private const val GECKO_TIMEOUT_MS = 4_000L
    private const val HTTP_TIMEOUT_MS = 3_500
    private const val ACCEPT_HEADER =
        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

    /**
     * Fetches `<site>/favicon.ico`. Private-tab requests are never retried outside Gecko so they
     * cannot escape the private browsing context.
     */
    fun fetch(siteUrl: String, isPrivate: Boolean = false): Bitmap? {
        val iconUrl = iconUrl(siteUrl) ?: return null
        fetchWithGecko(iconUrl, isPrivate)?.let { return it }
        if (isPrivate) return null
        return fetchWithHttps(iconUrl)
    }

    /** `https://host[:port]/favicon.ico` for an HTTP(S) page URL, or `null` when unsupported. */
    internal fun iconUrl(siteUrl: String): String? {
        val uri = runCatching { Uri.parse(siteUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 && it != 80 && it != 443 }?.let { ":$it" }.orEmpty()
        return "https://$host$port/favicon.ico"
    }

    private fun fetchWithGecko(iconUrl: String, isPrivate: Boolean): Bitmap? {
        val runtime = BrowserEngine.runtimeOrNull() ?: return null
        return runCatching {
            val request = WebRequest.Builder(iconUrl)
                .addHeader("Accept", ACCEPT_HEADER)
                .build()
            val flags = if (isPrivate) {
                GeckoWebExecutor.FETCH_FLAGS_PRIVATE
            } else {
                GeckoWebExecutor.FETCH_FLAGS_NONE
            }
            val response = GeckoWebExecutor(runtime)
                .fetch(request, flags)
                .poll(GECKO_TIMEOUT_MS)
                ?: return null
            try {
                if (response.statusCode !in 200..299) return null
                response.body?.let(::decodeBounded)
            } finally {
                runCatching { response.body?.close() }
            }
        }.getOrElse { error ->
            Log.d(LOG_TAG, "First-party icon fetch through Gecko failed", error)
            null
        }
    }

    private fun fetchWithHttps(iconUrl: String): Bitmap? = runCatching {
        val connection = (URL(iconUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", ACCEPT_HEADER)
        }
        try {
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_ICON_BYTES) return null
            connection.inputStream.use(::decodeBounded)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { error ->
        Log.d(LOG_TAG, "First-party icon fetch failed", error)
        null
    }

    private fun decodeBounded(input: InputStream): Bitmap? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_ICON_BYTES) return null
            output.write(buffer, 0, read)
        }
        return decodeSampled(output.toByteArray())
    }

    internal fun decodeSampled(bytes: ByteArray, maxEdge: Int = 128): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxEdge) sample *= 2

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
