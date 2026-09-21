package com.ilyro.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/**
 * Persistent favicon cache shared by the home quick links and search-engine selector.
 *
 * The previous implementation only kept bitmaps for the lifetime of the process, which meant a
 * cold start always showed letter fallbacks and then visibly replaced them after another network
 * request. Disk reads and bitmap decoding are now kept off the main thread, while the in-memory
 * cache still avoids repeated work during a session and stale entries refresh in the background.
 */
internal object BrowserIconCache {
    private const val CACHE_DIR = "browser-icons-v1"
    private const val MAX_DISK_ENTRIES = 64
    private const val MAX_MEMORY_ENTRIES = 24
    private const val MAX_FAILURE_ENTRIES = 64
    private val refreshAgeMs = TimeUnit.DAYS.toMillis(7)
    private val retryDelayMs = TimeUnit.MINUTES.toMillis(10)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val bitmaps = object : LinkedHashMap<String, Bitmap>(
        MAX_MEMORY_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_MEMORY_ENTRIES
    }
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()
    private val failedAt = mutableMapOf<String, Long>()

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context, quickLinkUrls: List<String>) {
        if (appContext == null) {
            synchronized(lock) {
                if (appContext == null) appContext = context.applicationContext
            }
        }

        val targets = (quickLinkUrls + SearchEngine.entries.map(::searchEngineSiteUrl))
            .map(::siteIdentityUrl)
            .filter { it.isNotBlank() }
            .distinct()

        // Disk reads and bitmap decoding happen on the IO scope. The first Compose frame uses a
        // placeholder when the warm-up has not completed yet, avoiding main-thread I/O during
        // Activity creation and composition.
        targets.forEach(::prefetch)
        scope.launch { trimDiskCache() }
    }

    fun peek(url: String): Bitmap? {
        val key = siteIdentityUrl(url)
        if (key.isBlank()) return null

        synchronized(lock) {
            bitmaps[key]?.let { return it }
        }
        return null
    }

    fun peek(engine: SearchEngine): Bitmap? = peek(searchEngineSiteUrl(engine))

    suspend fun loadOnce(url: String): Bitmap? {
        val key = siteIdentityUrl(url)
        if (key.isBlank()) return null
        val job = synchronized(lock) {
            bitmaps[key]?.let { return it }
            inFlight[key] ?: run {
                val now = System.currentTimeMillis()
                val lastFailure = failedAt[key] ?: 0L
                if (now - lastFailure < retryDelayMs) return null

                scope.async(start = CoroutineStart.LAZY) {
                    val cached = appContext?.let { context ->
                        withContext(Dispatchers.IO) { readFromDisk(context, key) }
                    }
                    if (cached != null) {
                        synchronized(lock) {
                            bitmaps[key] = cached
                            failedAt.remove(key)
                            inFlight.remove(key)
                        }
                        return@async cached
                    }

                    val loaded = fetchAndPersist(key)
                    synchronized(lock) {
                        if (loaded != null) {
                            bitmaps[key] = loaded
                            failedAt.remove(key)
                        } else {
                            recordFailureLocked(key)
                        }
                        inFlight.remove(key)
                    }
                    loaded
                }.also { newJob ->
                    inFlight[key] = newJob
                    newJob.start()
                }
            }
        }
        return job.await()
    }

    suspend fun loadOnce(engine: SearchEngine): Bitmap? = loadOnce(searchEngineSiteUrl(engine))

    fun prefetch(url: String) {
        val key = siteIdentityUrl(url)
        if (key.isBlank()) return
        scope.launch {
            val context = appContext ?: return@launch
            val cached = synchronized(lock) { bitmaps[key] }
            if (cached == null) {
                loadOnce(key)
            }
            val file = cacheFile(context, key)
            if (file.exists() && System.currentTimeMillis() - file.lastModified() >= refreshAgeMs) {
                scheduleRefresh(key)
            }
        }
    }

    private fun scheduleRefresh(url: String) {
        synchronized(lock) {
            if (inFlight[url] != null) return
            val job = scope.async(start = CoroutineStart.LAZY) {
                val loaded = fetchAndPersist(url)
                synchronized(lock) {
                    if (loaded != null) {
                        bitmaps[url] = loaded
                        failedAt.remove(url)
                    } else {
                        recordFailureLocked(url)
                    }
                    inFlight.remove(url)
                }
                loaded
            }
            inFlight[url] = job
            job.start()
        }
    }

    fun trimMemory() {
        synchronized(lock) {
            // Do not recycle: Compose may still be drawing one of these bitmaps in the current
            // frame. Dropping the cache reference lets Android reclaim it safely once unused.
            bitmaps.clear()
            val now = System.currentTimeMillis()
            failedAt.keys
                .filter { key -> now - (failedAt[key] ?: now) >= retryDelayMs }
                .forEach(failedAt::remove)
        }
    }

    private fun recordFailureLocked(url: String) {
        failedAt[url] = System.currentTimeMillis()
        if (failedAt.size <= MAX_FAILURE_ENTRIES) return
        failedAt.entries
            .sortedBy { it.value }
            .take(failedAt.size - MAX_FAILURE_ENTRIES)
            .forEach { failedAt.remove(it.key) }
    }

    private suspend fun fetchAndPersist(siteUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext null
        val bitmap = fetchSiteFavicon(siteUrl) ?: return@withContext null
        runCatching { writeToDisk(context, siteUrl, bitmap) }
        bitmap
    }

    private fun readFromDisk(context: Context, url: String): Bitmap? {
        val file = cacheFile(context, url)
        if (!file.isFile || file.length() <= 0L) return null
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (decoded != null) return decoded
        runCatching { file.delete() }
        return null
    }

    private fun writeToDisk(context: Context, url: String, bitmap: Bitmap) {
        val directory = cacheDirectory(context)
        if (!directory.exists()) directory.mkdirs()
        val destination = cacheFile(context, url)
        val temporary = File(directory, destination.name + ".tmp")

        FileOutputStream(temporary).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.fd.sync()
        }

        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        destination.setLastModified(System.currentTimeMillis())
    }

    private fun trimDiskCache() {
        val context = appContext ?: return
        val directory = cacheDirectory(context)
        val files = directory.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }
            ?.sortedByDescending(File::lastModified)
            ?: return
        files.drop(MAX_DISK_ENTRIES).forEach { runCatching { it.delete() } }
        directory.listFiles { file -> file.name.endsWith(".tmp") }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun cacheDirectory(context: Context): File = File(context.filesDir, CACHE_DIR)

    private fun cacheFile(context: Context, url: String): File =
        File(cacheDirectory(context), sha256(url) + ".png")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun siteIdentityUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.isBlank()) return ""
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return value
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) return value
        val port = uri.port
        val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return buildString {
            append(scheme)
            append("://")
            append(host)
            if (port != -1 && !defaultPort) {
                append(':')
                append(port)
            }
            append('/')
        }
    }

    private fun searchEngineSiteUrl(engine: SearchEngine): String = when (engine) {
        SearchEngine.GOOGLE -> "https://www.google.com/"
        SearchEngine.YANDEX -> "https://yandex.com/"
        SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/"
        SearchEngine.BRAVE -> "https://search.brave.com/"
        SearchEngine.BING -> "https://www.bing.com/"
    }

    private fun fetchSiteFavicon(siteUrl: String): Bitmap? = runCatching {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val connection = (URL("https://www.google.com/s2/favicons?sz=128&domain_url=$encoded")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_500
            readTimeout = 3_500
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "ILYRO/0.16.26 Android")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
