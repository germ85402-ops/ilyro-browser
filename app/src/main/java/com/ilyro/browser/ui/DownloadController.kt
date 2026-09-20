package com.ilyro.browser.ui

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.ilyro.browser.ACTION_OPEN_DOWNLOADS
import com.ilyro.browser.R
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.WebExtension
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val DIRECT_NONE = 0
private const val DIRECT_RUNNING = 1
private const val DIRECT_SUCCESS = 2
private const val DIRECT_FAILED = 3
private const val DIRECT_PAUSED = 4
private const val BODY_READ_TIMEOUT_MS = 300_000L
private const val PREF_PENDING_APK_INSTALL_URI = "pending_apk_install_uri_v1"

private class InvalidDownloadPayloadException(message: String) : Exception(message)

private val DIRECT_DOWNLOAD_EXTENSIONS = setOf(
    "apk", "xapk", "apks", "aab",
    "zip", "rar", "7z", "tar", "tgz", "gz", "bz2", "xz",
    "exe", "msi", "dmg", "pkg", "deb", "rpm", "iso", "jar"
)

internal fun isLikelyDirectDownloadUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val segment = Uri.decode(uri.lastPathSegment.orEmpty()).lowercase()
    val extension = segment.substringAfterLast('.', "")
    return extension in DIRECT_DOWNLOAD_EXTENSIONS
}

private fun mimeTypeForDirectDownload(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val segment = Uri.decode(uri.lastPathSegment.orEmpty())
    val extension = segment.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "xapk", "apks" -> "application/zip"
        "aab" -> "application/octet-stream"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private val GENERIC_BINARY_MIME_TYPES = setOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/binary"
)

private fun cleanDownloadName(name: String?): String? = name
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun downloadExtension(name: String?): String = cleanDownloadName(name)
    ?.substringAfterLast('.', "")
    ?.lowercase()
    .orEmpty()

internal fun contentDispositionFileName(headerValue: String?): String? {
    val header = headerValue?.trim().orEmpty()
    if (header.isBlank()) return null

    val extended = Regex(
        pattern = "(?i)(?:^|;)\\s*filename\\*\\s*=\\s*(?:\\\"([^\\\"]+)\\\"|([^;]+))"
    ).find(header)?.let { match ->
        match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
    }
    if (!extended.isNullOrBlank()) {
        val encoded = extended.substringAfter("''", extended).trim().trim('"')
        val decoded = runCatching { java.net.URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrDefault(encoded)
        cleanDownloadName(decoded)?.let { return it }
    }

    val regular = Regex(
        pattern = "(?i)(?:^|;)\\s*filename\\s*=\\s*(?:\\\"([^\\\"]+)\\\"|([^;]+))"
    ).find(header)?.let { match ->
        match.groupValues[1].ifBlank { match.groupValues[2] }.trim().trim('"')
    }
    return cleanDownloadName(regular)
}

internal fun resolveDownloadFileName(
    guessedName: String,
    suggestedName: String?,
    mimeType: String?
): String {
    val guessed = cleanDownloadName(guessedName) ?: "download"
    val suggested = cleanDownloadName(suggestedName)
    val guessedExtension = downloadExtension(guessed)
    val suggestedExtension = downloadExtension(suggested)
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()

    if (normalizedMime == APK_MIME_TYPE) {
        if (guessedExtension == "apk") return guessed
        if (suggestedExtension == "apk") return suggested!!
        val base = if (guessedExtension.isBlank()) {
            guessed
        } else {
            guessed.substringBeforeLast('.', guessed)
        }.ifBlank { "download" }
        return "$base.apk"
    }

    // A very common APK response is a redirect to an opaque URL served as
    // application/octet-stream. Android's URLUtil then invents a .bin suffix.
    // Preserve a trustworthy .apk hint from the original request instead.
    if (suggestedExtension == "apk" && guessedExtension in setOf("", "bin", "dat")) {
        return suggested!!
    }

    return guessed
}

internal fun resolveDownloadMimeType(fileName: String, mimeType: String?): String? {
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    return if (
        fileName.endsWith(".apk", ignoreCase = true) &&
        (normalizedMime.isNullOrBlank() || normalizedMime in GENERIC_BINARY_MIME_TYPES)
    ) {
        APK_MIME_TYPE
    } else {
        mimeType
    }
}

private fun directDownloadNameHint(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null

    fun accept(candidate: String?): String? {
        val cleaned = cleanDownloadName(candidate) ?: return null
        val extension = downloadExtension(cleaned)
        return cleaned.takeIf { extension in DIRECT_DOWNLOAD_EXTENSIONS }
    }

    accept(Uri.decode(uri.lastPathSegment.orEmpty()))?.let { return it }

    // Many download CDNs hide the real filename in a query parameter while the path is opaque.
    val likelyNameKeys = listOf(
        "filename", "file_name", "file", "name", "download", "attachment",
        "response-content-disposition"
    )
    likelyNameKeys.forEach { key ->
        runCatching { uri.getQueryParameters(key) }.getOrDefault(emptyList()).forEach { value ->
            contentDispositionFileName(value)?.let { return it }
            accept(Uri.decode(value))?.let { return it }
        }
    }

    // Final fallback for encoded URLs such as ?target=https%3A%2F%2Fcdn%2Fapp.apk.
    val decodedUrl = runCatching { Uri.decode(url) }.getOrDefault(url)
    Regex("(?i)([^/?#&=]+\\.(?:apk|xapk|apks|aab))(?:$|[?&#])")
        .find(decodedUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::cleanDownloadName)
        ?.let { return it }

    return null
}

internal data class DownloadRecord(
    val id: Long,
    val sourceUrl: String,
    val fileName: String,
    val mimeType: String?,
    val createdAt: Long,
    val localUri: String? = null,
    val expectedBytes: Long = -1L,
    val directState: Int = DIRECT_NONE,
    val referrer: String? = null,
    val isPrivate: Boolean = false
)


private data class ExtensionDownloadInfo(
    val name: String,
    val mimeType: String,
    val referrerUrl: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val received: Long = 0L,
    val total: Long = -1L,
    val downloadState: Int = WebExtension.Download.STATE_IN_PROGRESS,
    val interruptReason: Int? = null,
    val isPaused: Boolean = false,
    val exists: Boolean = false
) : WebExtension.Download.Info {
    override fun bytesReceived(): Long = received
    override fun totalBytes(): Long = total
    override fun fileSize(): Long = if (downloadState == WebExtension.Download.STATE_COMPLETE) received else total
    override fun filename(): String = name
    override fun mime(): String = mimeType
    override fun referrer(): String = referrerUrl
    override fun startTime(): Long = startedAt
    override fun endTime(): Long? = endedAt
    override fun state(): Int = downloadState
    override fun paused(): Boolean = isPaused
    override fun canResume(): Boolean = false
    override fun fileExists(): Boolean = exists
    override fun error(): Int? = interruptReason
}

private data class LiveTransfer(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long
)

private data class SpeedSample(
    val downloadedBytes: Long,
    val timestampMs: Long,
    val speedBytesPerSecond: Long
)

private data class PreparedHlsResource(
    val resource: HlsResource,
    val byteRange: Pair<Long, Long>?
)

private data class BufferedHlsResource(
    val file: File
)

internal data class DownloadUiItem(
    val record: DownloadRecord,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long = 0L
) {
    val progress: Float?
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else null
}

internal class DownloadController(
    context: Context,
    private val prefs: SharedPreferences,
    private val runtime: GeckoRuntime
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val resolver = appContext.contentResolver
    private val executor = GeckoWebExecutor(runtime)
    private val recordLock = Any()
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeDownloadSessions = mutableSetOf<GeckoSession>()
    private val liveTransfers = ConcurrentHashMap<Long, LiveTransfer>()
    private val activeBodies = ConcurrentHashMap<Long, InputStream>()
    private val activeHlsBodies = ConcurrentHashMap<Long, MutableSet<InputStream>>()
    private val activeHlsPools = ConcurrentHashMap<Long, java.util.concurrent.ExecutorService>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()
    private val pausedIds = ConcurrentHashMap.newKeySet<Long>()
    private val pauseLocks = ConcurrentHashMap<Long, java.lang.Object>()
    private val managerSpeedSamples = ConcurrentHashMap<Long, SpeedSample>()
    private val managerPromotionInFlight = ConcurrentHashMap.newKeySet<Long>()
    private val recentNavigationStarts = ConcurrentHashMap<String, Long>()
    private val nextExtensionDownloadId = AtomicInteger(
        ((System.currentTimeMillis() and 0x3fffffffL).toInt()).coerceAtLeast(1)
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "ILYRO download progress"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        DownloadKeepAliveService.setControlHandler { id, pauseRequested ->
            if (pauseRequested) pause(id) else resume(id)
        }
        DownloadKeepAliveService.setCancelHandler { id ->
            val item = runCatching {
                snapshot().firstOrNull { it.record.id == id }
            }.getOrNull()
            if (item != null) {
                cancel(item)
                true
            } else {
                false
            }
        }
    }


    fun enqueueExtensionDownload(
        request: WebExtension.DownloadRequest,
        allowMetered: Boolean,
        onRecordsChanged: (() -> Unit)? = null
    ): GeckoResult<WebExtension.DownloadInitData> {
        val result = GeckoResult<WebExtension.DownloadInitData>(mainHandler)
        mainHandler.post {
            val id = nextExtensionDownloadId.getAndIncrement().let { if (it <= 0) 1 else it }
            val extensionDownload = try {
                runtime.webExtensionController.createDownload(id)
            } catch (error: Throwable) {
                result.completeExceptionally(error)
                return@post
            }
            if (extensionDownload == null) {
                result.completeExceptionally(IllegalStateException("Could not allocate extension download"))
                return@post
            }

            val startedAt = System.currentTimeMillis()
            val requestedUrl = request.request.uri
            val requestedName = request.filename
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() }
                ?: URLUtil.guessFileName(requestedUrl, null, null)
            val referrer = request.request.referrer.orEmpty()
            val initial = ExtensionDownloadInfo(
                name = requestedName,
                mimeType = "application/octet-stream",
                referrerUrl = referrer,
                startedAt = startedAt
            )
            result.complete(WebExtension.DownloadInitData(extensionDownload, initial))

            fun fail(reason: Int = WebExtension.Download.INTERRUPT_REASON_NETWORK_FAILED) {
                mainHandler.post {
                    extensionDownload.update(
                        initial.copy(
                            endedAt = System.currentTimeMillis(),
                            downloadState = WebExtension.Download.STATE_INTERRUPTED,
                            interruptReason = reason
                        )
                    )
                }
                onRecordsChanged?.invoke()
            }

            val privateRequest = request.downloadFlags and GeckoWebExecutor.FETCH_FLAGS_PRIVATE != 0
            runCatching {
                executor.fetch(request.request, request.downloadFlags).accept(
                    { response ->
                        if (response == null) {
                            fail()
                            return@accept
                        }
                        if (!request.allowHttpErrors && response.statusCode !in 200..299) {
                            runCatching { response.body?.close() }
                            val reason = when (response.statusCode) {
                                401 -> WebExtension.Download.INTERRUPT_REASON_SERVER_UNAUTHORIZED
                                403 -> WebExtension.Download.INTERRUPT_REASON_SERVER_FORBIDDEN
                                else -> WebExtension.Download.INTERRUPT_REASON_SERVER_FAILED
                            }
                            fail(reason)
                            return@accept
                        }

                        val record = enqueue(
                            response = response,
                            allowMetered = allowMetered,
                            referrer = referrer.takeIf { it.isNotBlank() },
                            isPrivate = privateRequest,
                            suggestedName = requestedName
                        )
                        if (record == null) {
                            fail(WebExtension.Download.INTERRUPT_REASON_FILE_FAILED)
                            return@accept
                        }
                        onRecordsChanged?.invoke()
                        monitorExtensionDownload(extensionDownload, record.id, startedAt, initial)
                    },
                    { _ -> fail() }
                )
            }.onFailure { fail() }
        }
        return result
    }

    private fun monitorExtensionDownload(
        extensionDownload: WebExtension.Download,
        recordId: Long,
        startedAt: Long,
        initial: ExtensionDownloadInfo
    ) {
        Thread({
            var missingPolls = 0
            while (true) {
                try { Thread.sleep(500L) } catch (_: InterruptedException) { return@Thread }
                val item = runCatching { snapshot().firstOrNull { it.record.id == recordId } }.getOrNull()
                if (item == null) {
                    missingPolls += 1
                    if (missingPolls < 4) continue
                    mainHandler.post {
                        extensionDownload.update(
                            initial.copy(
                                endedAt = System.currentTimeMillis(),
                                downloadState = WebExtension.Download.STATE_INTERRUPTED,
                                interruptReason = WebExtension.Download.INTERRUPT_REASON_USER_CANCELED
                            )
                        )
                    }
                    return@Thread
                }
                missingPolls = 0

                val state = when (item.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> WebExtension.Download.STATE_COMPLETE
                    DownloadManager.STATUS_FAILED -> WebExtension.Download.STATE_INTERRUPTED
                    else -> WebExtension.Download.STATE_IN_PROGRESS
                }
                val paused = item.status == DownloadManager.STATUS_PAUSED
                val info = ExtensionDownloadInfo(
                    name = item.record.fileName,
                    mimeType = item.record.mimeType ?: initial.mimeType,
                    referrerUrl = item.record.referrer ?: initial.referrerUrl,
                    startedAt = startedAt,
                    endedAt = if (state == WebExtension.Download.STATE_IN_PROGRESS) null else System.currentTimeMillis(),
                    received = item.downloadedBytes,
                    total = item.totalBytes,
                    downloadState = state,
                    interruptReason = if (state == WebExtension.Download.STATE_INTERRUPTED) {
                        WebExtension.Download.INTERRUPT_REASON_NETWORK_FAILED
                    } else null,
                    isPaused = paused,
                    exists = state != WebExtension.Download.STATE_INTERRUPTED
                )
                mainHandler.post { extensionDownload.update(info) }
                onRecordsChangedFromMonitor()
                if (state != WebExtension.Download.STATE_IN_PROGRESS) return@Thread
            }
        }, "ILYRO-extension-download-${extensionDownload.id}").start()
    }

    private fun onRecordsChangedFromMonitor() = Unit

    fun enqueue(
        response: WebResponse,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean,
        suggestedName: String? = null,
        onFinished: (() -> Unit)? = null
    ): DownloadRecord? {
        val url = response.uri
        val responseMimeType = header(response, "content-type")
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val contentDisposition = header(response, "content-disposition")
        val headerName = contentDispositionFileName(contentDisposition)
        val headerExtension = downloadExtension(headerName)
        val responseNameHint = headerName
            ?.takeIf { headerExtension.isNotBlank() && headerExtension !in setOf("bin", "dat") }
            ?: suggestedName?.takeIf { it.isNotBlank() }
            ?: headerName
            ?: directDownloadNameHint(url)
        val guessedFileName = URLUtil.guessFileName(
            url,
            contentDisposition,
            responseMimeType
        )
        val fileName = resolveDownloadFileName(
            guessedName = guessedFileName,
            suggestedName = responseNameHint,
            mimeType = responseMimeType
        )
        val mimeType = resolveDownloadMimeType(fileName, responseMimeType)
        val expectedBytes = header(response, "content-length")?.trim()?.toLongOrNull() ?: -1L
        val body = response.body

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (body != null) {
                response.setReadTimeoutMillis(BODY_READ_TIMEOUT_MS)
                val direct = enqueueResponseBody(
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    expectedBytes = expectedBytes,
                    body = body,
                    allowMetered = allowMetered,
                    referrer = referrer,
                    isPrivate = isPrivate,
                    onFinished = onFinished
                )
                if (direct != null) return direct
                runCatching { body.close() }
            }

            // The hidden top-level session is no longer needed once no readable
            // response body is available; the remaining fallbacks own their requests.
            runCatching { onFinished?.invoke() }

            // Some servers hand Gecko an external response without exposing the body.
            // Retry through Gecko's own network stack so the request keeps Gecko cookies
            // and private-browsing context instead of switching to Android DownloadManager.
            val geckoRetry = enqueueGeckoFetch(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                expectedBytes = expectedBytes,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate
            )
            if (geckoRetry != null) return geckoRetry
        } else {
            runCatching { body?.close() }
            runCatching { onFinished?.invoke() }
        }

        return enqueueUrl(
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            allowMetered = allowMetered,
            referrer = referrer,
            isPrivate = isPrivate
        )
    }

    fun enqueueNavigationWithSession(
        url: String,
        sourceSettings: GeckoSessionSettings,
        suggestedName: String? = null,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean,
        onRecordsChanged: (() -> Unit)? = null
    ): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        // Always let a real GeckoSession own top-level downloads, including obvious .apk/.zip
        // links. The earlier WebExecutor fast path lost navigation context on redirect/cookie
        // protected download hosts and could save an HTML/intermediate response as the file.
        val now = android.os.SystemClock.elapsedRealtime()
        val navigationKey = "${if (isPrivate) 1 else 0}|$url|${referrer.orEmpty()}"
        recentNavigationStarts.entries.removeIf { now - it.value > NAVIGATION_DEDUPE_RETENTION_MS }
        val previousStart = recentNavigationStarts.put(navigationKey, now)
        if (previousStart != null && now - previousStart < NAVIGATION_DEDUPE_WINDOW_MS) {
            // Gecko can report the same user click more than once while a redirect is being
            // resolved. Treat it as the already-running download instead of creating a duplicate.
            return true
        }

        val provisionalId = -System.nanoTime()
        val downloadNameHint = suggestedName?.takeIf { it.isNotBlank() }
            ?: directDownloadNameHint(url)
        val provisionalName = downloadNameHint
            ?: Uri.decode(uri.lastPathSegment.orEmpty()).takeIf { it.isNotBlank() }
            ?: "Media download"
        DownloadKeepAliveService.track(appContext, provisionalId, provisionalName)

        val downloadSession = GeckoSession(
            GeckoSessionSettings.Builder(sourceSettings).build()
        )
        var handedOff = false

        fun closeDownloadSession() {
            val closeAction = {
                activeDownloadSessions.remove(downloadSession)
                if (downloadSession.isOpen) {
                    runCatching { downloadSession.close() }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                closeAction()
            } else {
                mainHandler.post(closeAction)
            }
        }

        fun fallback() {
            if (handedOff) return
            handedOff = true
            closeDownloadSession()
            val record = enqueueNavigation(
                url = url,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate,
                suggestedName = downloadNameHint
            )
            if (record != null) {
                DownloadKeepAliveService.replace(appContext, provisionalId, record.id, record.fileName)
            } else {
                DownloadKeepAliveService.finish(provisionalId)
            }
            onRecordsChanged?.invoke()
        }

        downloadSession.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                if (handedOff) return
                handedOff = true
                val record = enqueue(
                    response = response,
                    allowMetered = allowMetered,
                    referrer = referrer,
                    isPrivate = isPrivate,
                    suggestedName = downloadNameHint,
                    onFinished = { closeDownloadSession() }
                )
                onRecordsChanged?.invoke()
                if (record != null) {
                    DownloadKeepAliveService.replace(appContext, provisionalId, record.id, record.fileName)
                } else {
                    closeDownloadSession()
                    val fallbackRecord = enqueueNavigation(
                        url = url,
                        allowMetered = allowMetered,
                        referrer = referrer,
                        isPrivate = isPrivate,
                        suggestedName = downloadNameHint
                    )
                    if (fallbackRecord != null) {
                        DownloadKeepAliveService.replace(
                            appContext,
                            provisionalId,
                            fallbackRecord.id,
                            fallbackRecord.fileName
                        )
                    } else {
                        DownloadKeepAliveService.finish(provisionalId)
                    }
                    onRecordsChanged?.invoke()
                }
            }
        })
        downloadSession.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                // Some download pages briefly render a redirect/challenge document and then
                // start the real attachment from JavaScript. Keep the hidden session alive for
                // a short grace period instead of closing it at the first HTML page stop.
                if (!handedOff) {
                    mainHandler.postDelayed({
                        if (!handedOff) fallback()
                    }, DOWNLOAD_PAGE_SETTLE_GRACE_MS)
                }
            }
        })

        activeDownloadSessions.add(downloadSession)
        return runCatching {
            downloadSession.open(runtime)
            downloadSession.loadUri(url)
            mainHandler.postDelayed({
                if (!handedOff) fallback()
            }, DOWNLOAD_SESSION_RESPONSE_TIMEOUT_MS)
            true
        }.getOrElse {
            fallback()
            false
        }
    }

    /**
     * Expands detected HLS master playlists into user-facing quality choices. This runs only
     * when the media sheet is opened, so ordinary browsing never pays for playlist inspection.
     */
    fun resolveMediaQualities(
        media: List<DetectedMedia>,
        isPrivate: Boolean,
        onResolved: (List<DetectedMedia>) -> Unit
    ) {
        if (media.none { it.kind == DetectedMediaKind.HLS }) {
            mainHandler.post { onResolved(media) }
            return
        }

        Thread({
            val expanded = mutableListOf<DetectedMedia>()
            media.forEach { item ->
                if (item.kind != DetectedMediaKind.HLS) {
                    expanded += item
                    return@forEach
                }

                val variants = runCatching {
                    val fetched = fetchHlsText(
                        url = item.url,
                        referrer = item.pageUrl,
                        isPrivate = isPrivate
                    )
                    parseHlsMasterVariants(fetched.second, fetched.first)
                }.getOrDefault(emptyList())

                if (variants.isEmpty()) {
                    expanded += item
                } else {
                    variants.take(MAX_HLS_QUALITY_VARIANTS).forEach { variant ->
                        expanded += item.copy(
                            url = variant.url,
                            width = variant.width.takeIf { it > 0 } ?: item.width,
                            height = variant.height.takeIf { it > 0 } ?: item.height,
                            bitrate = variant.bandwidth,
                            hlsHasSeparateAudio = variant.hasSeparateAudio,
                            source = "hls-variant"
                        )
                    }
                }
            }

            val resolved = expanded
                .sortedWith(
                    compareBy<DetectedMedia> { mediaResolvePriority(it.kind) }
                        .thenByDescending { it.height }
                        .thenByDescending { it.width }
                        .thenByDescending { it.bitrate }
                        .thenBy { it.firstSeenAt }
                )
                .distinctBy(::mediaQualityIdentity)

            mainHandler.post { onResolved(resolved) }
        }, "ILYRO-media-quality-resolver").start()
    }

    private fun mediaResolvePriority(kind: DetectedMediaKind): Int = when (kind) {
        DetectedMediaKind.VIDEO -> 0
        DetectedMediaKind.HLS -> 1
        DetectedMediaKind.DASH -> 2
        DetectedMediaKind.AUDIO -> 3
    }

    private fun mediaQualityIdentity(item: DetectedMedia): String {
        return "${item.kind}|${MediaDetectorBridge.canonicalMediaIdentity(item.url)}"
    }

    /**
     * Saves a common HLS VOD stream as one playable local file without bundling a media
     * transcoder. MPEG-TS segments are concatenated into .ts; fragmented MP4 playlists with
     * EXT-X-MAP are written as init + media fragments into .mp4. Master playlists select the
     * highest advertised resolution/bandwidth variant unless the UI already supplied a variant.
     *
     * Segment downloads use a small bounded parallel window. HLS servers commonly cap the
     * throughput of one request; four concurrent segment requests fill the connection better
     * while preserving segment order and keeping cache usage bounded.
     *
     * Live, encrypted, and separate-audio HLS variants are rejected explicitly rather than
     * producing a corrupt or silent file. Those formats need a real mux/decrypt pipeline.
     */
    fun enqueueHlsDownload(
        url: String,
        suggestedTitle: String?,
        referrer: String?,
        isPrivate: Boolean,
        onRecordsChanged: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase() !in setOf("http", "https")) return false

        val keepAliveToken = -System.nanoTime()
        DownloadKeepAliveService.track(
            appContext,
            keepAliveToken,
            suggestedTitle?.takeIf { it.isNotBlank() } ?: "Video download"
        )

        Thread({
            var createdRecord: DownloadRecord? = null
            var workerPool: java.util.concurrent.ExecutorService? = null
            val temporarySegments = ConcurrentHashMap.newKeySet<File>()

            fun reportError(message: String) {
                mainHandler.post {
                    onRecordsChanged?.invoke()
                    onError?.invoke(message)
                }
            }

            try {
                var playlistUrl = url
                var playlistText = ""
                var separateAudio = false
                var depth = 0

                // Follow nested master playlists, but keep a strict bound so malformed or cyclic
                // manifests cannot keep a download worker alive forever.
                while (true) {
                    val fetched = fetchHlsText(playlistUrl, referrer, isPrivate)
                    playlistUrl = fetched.first
                    playlistText = fetched.second
                    val variant = selectBestHlsVariant(playlistText, playlistUrl) ?: break
                    separateAudio = separateAudio || variant.hasSeparateAudio
                    depth += 1
                    if (depth >= 3) error("Nested HLS master playlist is too deep")
                    playlistUrl = variant.url
                }

                if (separateAudio) error("This HLS stream uses a separate audio track")

                val playlist = parseHlsMediaPlaylist(playlistText, playlistUrl)
                if (!playlist.isVod) error("Live HLS streams cannot be saved as a complete video yet")
                if (playlist.hasUnsupportedEncryption) error("Encrypted HLS streams are not supported yet")

                val extension = if (playlist.prefersMp4Container) "mp4" else "ts"
                val mimeType = if (playlist.prefersMp4Container) "video/mp4" else "video/mp2t"
                val fileName = buildHlsFileName(suggestedTitle, playlistUrl, extension)
                val destination = createDestination(fileName, mimeType)
                    ?: error("Could not create a Downloads destination")
                val record = newDirectRecord(
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    expectedBytes = -1L,
                    destination = destination,
                    referrer = referrer,
                    isPrivate = isPrivate
                )
                createdRecord = record
                addRecord(record)
                // Switch the foreground-service token to the real record id so progress
                // updates and completion all refer to the same single system notification.
                DownloadKeepAliveService.replace(appContext, keepAliveToken, record.id, fileName)
                mainHandler.post { onRecordsChanged?.invoke() }

                val nextRangeOffset = mutableMapOf<String, Long>()
                fun prepare(resource: HlsResource): PreparedHlsResource {
                    val range = resource.byteRange?.let { requested ->
                        val start = requested.offset ?: nextRangeOffset[resource.url] ?: 0L
                        nextRangeOffset[resource.url] = start + requested.length
                        start to (start + requested.length - 1L)
                    }
                    return PreparedHlsResource(resource, range)
                }

                val orderedResources = buildList {
                    playlist.initSegment?.let { add(it) }
                    addAll(playlist.segments)
                }.map(::prepare)
                if (orderedResources.isEmpty()) error("HLS playlist contains no downloadable media")

                val receivedBytes = AtomicLong(0L)
                val progressLock = Any()
                val knownResourceBytes = AtomicLong(0L)
                val knownResourceCount = AtomicInteger(0)
                val exactRangeTotal = if (orderedResources.all { it.byteRange != null }) {
                    orderedResources.sumOf { prepared ->
                        val range = prepared.byteRange!!
                        (range.second - range.first + 1L).coerceAtLeast(0L)
                    }
                } else {
                    -1L
                }
                val estimatedTotalBytes = AtomicLong(exactRangeTotal)
                if (exactRangeTotal > 0L) updateExpectedBytes(record.id, exactRangeTotal)

                fun registerResourceLength(length: Long) {
                    if (length <= 0L || exactRangeTotal > 0L) return
                    val knownBytes = knownResourceBytes.addAndGet(length)
                    val knownCount = knownResourceCount.incrementAndGet().coerceAtLeast(1)
                    val estimate = ((knownBytes.toDouble() / knownCount.toDouble()) * orderedResources.size)
                        .toLong()
                        .coerceAtLeast(receivedBytes.get())
                    estimatedTotalBytes.set(estimate)
                    updateExpectedBytes(record.id, estimate)
                }

                fun currentTotalBytes(downloaded: Long): Long {
                    val estimate = estimatedTotalBytes.get()
                    return if (estimate > 0L) maxOf(estimate, downloaded) else -1L
                }

                var lastNotificationAt = 0L
                var lastSpeedAt = android.os.SystemClock.elapsedRealtime()
                var lastSpeedBytes = 0L
                var speedBytesPerSecond = 0L

                fun publishProgress(delta: Int) {
                    val downloaded = receivedBytes.addAndGet(delta.toLong())
                    synchronized(progressLock) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        val elapsed = now - lastSpeedAt
                        if (elapsed >= 500L) {
                            val instant = ((downloaded - lastSpeedBytes).coerceAtLeast(0L) * 1000L /
                                elapsed.coerceAtLeast(1L))
                            speedBytesPerSecond = if (speedBytesPerSecond <= 0L) {
                                instant
                            } else {
                                (speedBytesPerSecond * 3L + instant) / 4L
                            }
                            lastSpeedAt = now
                            lastSpeedBytes = downloaded
                        }
                        val total = currentTotalBytes(downloaded)
                        liveTransfers[record.id] = LiveTransfer(downloaded, total, speedBytesPerSecond)
                        if (now - lastNotificationAt >= 500L) {
                            notifyDownloadProgress(record.id, fileName, downloaded, total)
                            lastNotificationAt = now
                        }
                    }
                }

                val activeHlsBodySet = activeHlsBodies.computeIfAbsent(record.id) {
                    ConcurrentHashMap.newKeySet<InputStream>()
                }

                fun bufferResource(prepared: PreparedHlsResource): BufferedHlsResource {
                    if (cancelledIds.contains(record.id) || Thread.currentThread().isInterrupted) {
                        throw java.io.InterruptedIOException("Download cancelled")
                    }
                    if (!waitUntilResumed(record.id)) {
                        throw java.io.InterruptedIOException("Download cancelled")
                    }

                    val response = fetchHlsResponse(
                        url = prepared.resource.url,
                        referrer = referrer ?: playlistUrl,
                        isPrivate = isPrivate,
                        byteRange = prepared.byteRange
                    )
                    if (prepared.byteRange != null && response.statusCode != 206) {
                        runCatching { response.body?.close() }
                        error("Media server did not honor an HLS byte range")
                    }
                    val body = response.body ?: error("HLS media response has no body")
                    response.setReadTimeoutMillis(BODY_READ_TIMEOUT_MS)
                    val resourceLength = prepared.byteRange?.let { range ->
                        (range.second - range.first + 1L).coerceAtLeast(0L)
                    } ?: header(response, "content-length")?.trim()?.toLongOrNull() ?: -1L
                    registerResourceLength(resourceLength)
                    activeHlsBodySet.add(body)

                    val temp = File.createTempFile("ilyro-hls-", ".part", appContext.cacheDir)
                    temporarySegments.add(temp)
                    var bytes = 0L
                    try {
                        temp.outputStream().buffered(HLS_COPY_BUFFER_BYTES).use { output ->
                            body.use { input ->
                                val buffer = ByteArray(HLS_COPY_BUFFER_BYTES)
                                while (true) {
                                    if (cancelledIds.contains(record.id) || Thread.currentThread().isInterrupted) {
                                        throw java.io.InterruptedIOException("Download cancelled")
                                    }
                                    if (!waitUntilResumed(record.id)) {
                                        throw java.io.InterruptedIOException("Download cancelled")
                                    }
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    output.write(buffer, 0, count)
                                    bytes += count
                                    publishProgress(count)
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        temporarySegments.remove(temp)
                        runCatching { temp.delete() }
                        throw error
                    } finally {
                        activeHlsBodySet.remove(body)
                    }
                    if (resourceLength <= 0L) registerResourceLength(bytes)
                    return BufferedHlsResource(temp)
                }

                val initialTotal = currentTotalBytes(0L)
                liveTransfers[record.id] = LiveTransfer(0L, initialTotal, 0L)
                notifyDownloadProgress(record.id, fileName, 0L, initialTotal)

                val workerCount = minOf(HLS_PARALLEL_FETCHES, orderedResources.size).coerceAtLeast(1)
                val pool = Executors.newFixedThreadPool(workerCount) { runnable ->
                    Thread(runnable, "ILYRO-hls-segment").apply { isDaemon = true }
                }
                workerPool = pool
                activeHlsPools[record.id] = pool

                resolver.openOutputStream(destination, "w")?.use { output ->
                    val pending = ArrayDeque<java.util.concurrent.Future<BufferedHlsResource>>()
                    var nextResource = 0

                    while (nextResource < workerCount) {
                        val prepared = orderedResources[nextResource++]
                        pending.addLast(pool.submit<BufferedHlsResource> { bufferResource(prepared) })
                    }

                    repeat(orderedResources.size) {
                        if (cancelledIds.contains(record.id)) {
                            throw java.io.InterruptedIOException("Download cancelled")
                        }
                        if (!waitUntilResumed(record.id)) {
                            throw java.io.InterruptedIOException("Download cancelled")
                        }
                        val future = pending.removeFirst()
                        val buffered = try {
                            future.get()
                        } catch (execution: java.util.concurrent.ExecutionException) {
                            throw (execution.cause ?: execution)
                        }

                        buffered.file.inputStream().buffered(HLS_COPY_BUFFER_BYTES).use { input ->
                            input.copyTo(output, HLS_COPY_BUFFER_BYTES)
                        }
                        temporarySegments.remove(buffered.file)
                        runCatching { buffered.file.delete() }

                        if (nextResource < orderedResources.size) {
                            val prepared = orderedResources[nextResource++]
                            pending.addLast(pool.submit<BufferedHlsResource> { bufferResource(prepared) })
                        }
                    }
                    output.flush()
                } ?: error("Could not open Downloads destination")

                pool.shutdown()
                activeHlsPools.remove(record.id, pool)
                workerPool = null

                if (cancelledIds.contains(record.id)) {
                    throw java.io.InterruptedIOException("Download cancelled")
                }
                val copied = receivedBytes.get()
                resolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null
                )
                markDirectSuccess(record.id, copied)
                liveTransfers[record.id] = LiveTransfer(copied, copied, 0L)
                notifyDownloadComplete(record.id, fileName, copied)
                cancelledIds.remove(record.id)
                mainHandler.post { onRecordsChanged?.invoke() }
            } catch (error: Throwable) {
                workerPool?.shutdownNow()
                createdRecord?.let { record -> activeHlsPools.remove(record.id)?.shutdownNow() }
                workerPool = null
                val record = createdRecord
                if (record != null) {
                    activeHlsBodies.remove(record.id)?.forEach { stream -> runCatching { stream.close() } }
                    liveTransfers.remove(record.id)
                    cancelDownloadNotification(record.id)
                    record.localUri?.let { runCatching { resolver.delete(Uri.parse(it), null, null) } }
                    synchronized(recordLock) {
                        saveRecordsUnsafe(restoreRecordsUnsafe().filterNot { it.id == record.id })
                    }
                    val wasCancelled = cancelledIds.remove(record.id)
                    if (wasCancelled) {
                        mainHandler.post { onRecordsChanged?.invoke() }
                        return@Thread
                    }
                }
                val root = (error as? java.util.concurrent.ExecutionException)?.cause ?: error
                reportError(root.message?.takeIf { it.isNotBlank() } ?: "HLS video download failed")
            } finally {
                workerPool?.shutdownNow()
                createdRecord?.let { record ->
                    activeHlsPools.remove(record.id)?.shutdownNow()
                    activeHlsBodies.remove(record.id)?.forEach { stream -> runCatching { stream.close() } }
                }
                temporarySegments.forEach { file -> runCatching { file.delete() } }
                temporarySegments.clear()
                DownloadKeepAliveService.finish(createdRecord?.id ?: keepAliveToken)
            }
        }, "ILYRO-hls-download-${System.nanoTime()}").start()
        return true
    }

    private fun fetchHlsText(
        url: String,
        referrer: String?,
        isPrivate: Boolean
    ): Pair<String, String> {
        val response = fetchHlsResponse(url, referrer, isPrivate, null)
        val body = response.body ?: error("HLS playlist response has no body")
        response.setReadTimeoutMillis(BODY_READ_TIMEOUT_MS)
        val bytes = body.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > HLS_MAX_PLAYLIST_BYTES) error("HLS playlist is too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return response.uri to String(bytes, Charsets.UTF_8)
    }

    private fun fetchHlsResponse(
        url: String,
        referrer: String?,
        isPrivate: Boolean,
        byteRange: Pair<Long, Long>?
    ): WebResponse {
        val request = WebRequest.Builder(url).apply {
            if (!referrer.isNullOrBlank() &&
                (referrer.startsWith("http://") || referrer.startsWith("https://"))) {
                this.referrer(referrer)
            }
            addHeader("Accept", "*/*")
            byteRange?.let { (start, end) -> addHeader("Range", "bytes=$start-$end") }
        }.build()
        val flags = if (isPrivate) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        val response = executor.fetch(request, flags).poll(HLS_FETCH_TIMEOUT_MS)
            ?: error("No response from HLS server")
        if (response.statusCode !in 200..299) {
            runCatching { response.body?.close() }
            error("HLS server returned HTTP ${response.statusCode}")
        }
        return response
    }

    private fun buildHlsFileName(title: String?, playlistUrl: String, extension: String): String {
        val fromTitle = title
            ?.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trimEnd('.')
            ?.take(96)
            ?.takeIf { it.isNotBlank() }
        val fromUrl = runCatching {
            Uri.decode(Uri.parse(playlistUrl).lastPathSegment.orEmpty())
                .substringBeforeLast('.', Uri.decode(Uri.parse(playlistUrl).lastPathSegment.orEmpty()))
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .trim()
                .take(96)
        }.getOrDefault("").takeIf { it.isNotBlank() }
        val base = fromTitle ?: fromUrl ?: "ILYRO video"
        return "$base.$extension"
    }

    fun enqueueNavigation(
        url: String,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean,
        suggestedName: String? = null
    ): DownloadRecord? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val nameHint = suggestedName?.takeIf { it.isNotBlank() }
            ?: directDownloadNameHint(url)
        val initialMimeType = mimeTypeForDirectDownload(url)
            ?: if (downloadExtension(nameHint) == "apk") APK_MIME_TYPE else null
        val guessedFileName = URLUtil.guessFileName(url, null, initialMimeType)
        val fileName = resolveDownloadFileName(guessedFileName, nameHint, initialMimeType)
        val mimeType = resolveDownloadMimeType(fileName, initialMimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val geckoDownload = enqueueGeckoFetch(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                expectedBytes = -1L,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate
            )
            if (geckoDownload != null) return geckoDownload
        }

        return enqueueUrl(
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            allowMetered = allowMetered,
            referrer = referrer,
            isPrivate = isPrivate
        )
    }

    private fun enqueueResponseBody(
        url: String,
        fileName: String,
        mimeType: String?,
        expectedBytes: Long,
        body: InputStream,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean,
        onFinished: (() -> Unit)? = null
    ): DownloadRecord? {
        val destination = createDestination(fileName, mimeType) ?: return null
        val record = newDirectRecord(
            url,
            fileName,
            mimeType,
            expectedBytes,
            destination,
            referrer,
            isPrivate
        )
        addRecord(record)
        DownloadKeepAliveService.track(appContext, record.id, fileName)
        writeBody(
            id = record.id,
            destination = destination,
            body = body,
            fileName = fileName,
            totalBytes = expectedBytes,
            onFinished = onFinished
        ) {
            retryAfterDirectFailure(
                failedId = record.id,
                failedDestination = destination,
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                expectedBytes = expectedBytes,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate,
                retryWithGecko = true
            )
        }
        return record
    }

    private fun enqueueGeckoFetch(
        url: String,
        fileName: String,
        mimeType: String?,
        expectedBytes: Long,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean
    ): DownloadRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return null

        val destination = createDestination(fileName, mimeType) ?: return null
        val record = newDirectRecord(
            url,
            fileName,
            mimeType,
            expectedBytes,
            destination,
            referrer,
            isPrivate
        )
        addRecord(record)
        DownloadKeepAliveService.track(appContext, record.id, fileName)

        val request = WebRequest.Builder(url).apply {
            if (!referrer.isNullOrBlank() &&
                (referrer.startsWith("http://") || referrer.startsWith("https://"))) {
                this.referrer(referrer)
            }
        }.build()
        val flags = if (isPrivate) {
            GeckoWebExecutor.FETCH_FLAGS_PRIVATE
        } else {
            GeckoWebExecutor.FETCH_FLAGS_NONE
        }

        fun fallbackToSystem() {
            if (cancelledIds.contains(record.id)) return
            retryAfterDirectFailure(
                failedId = record.id,
                failedDestination = destination,
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                expectedBytes = expectedBytes,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate,
                retryWithGecko = false
            )
        }

        runCatching {
            executor.fetch(request, flags).accept(
                { fetched ->
                    val fetchedBody = fetched?.body
                    if (fetched == null || fetched.statusCode !in 200..299 || fetchedBody == null) {
                        runCatching { fetchedBody?.close() }
                        fallbackToSystem()
                    } else {
                        fetched.setReadTimeoutMillis(BODY_READ_TIMEOUT_MS)
                        val fetchedLength = header(fetched, "content-length")
                            ?.trim()
                            ?.toLongOrNull()
                            ?: expectedBytes
                        updateExpectedBytes(record.id, fetchedLength)
                        writeBody(
                            id = record.id,
                            destination = destination,
                            body = fetchedBody,
                            fileName = fileName,
                            totalBytes = fetchedLength
                        ) { fallbackToSystem() }
                    }
                },
                { _ -> fallbackToSystem() }
            )
        }.onFailure {
            fallbackToSystem()
        }

        return record
    }

    private fun looksLikeApkArchive(destination: Uri): Boolean {
        return runCatching {
            val input = resolver.openInputStream(destination) ?: return@runCatching false
            input.use { raw ->
                val inputStream = BufferedInputStream(raw, 64 * 1024)
                val signature = ByteArray(4)
                var signatureRead = 0
                while (signatureRead < signature.size) {
                    val count = inputStream.read(signature, signatureRead, signature.size - signatureRead)
                    if (count < 0) break
                    if (count == 0) continue
                    signatureRead += count
                }
                val isZip = signatureRead == 4 &&
                    signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte() &&
                    ((signature[2] == 0x03.toByte() && signature[3] == 0x04.toByte()) ||
                     (signature[2] == 0x05.toByte() && signature[3] == 0x06.toByte()) ||
                     (signature[2] == 0x07.toByte() && signature[3] == 0x08.toByte()))
                if (!isZip) return@use false

                val needle = "AndroidManifest.xml".toByteArray(Charsets.US_ASCII)
                var matched = 0
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = inputStream.read(buffer)
                    if (count < 0) break
                    for (index in 0 until count) {
                        val value = buffer[index]
                        if (value == needle[matched]) {
                            matched += 1
                            if (matched == needle.size) return@use true
                        } else {
                            matched = if (value == needle[0]) 1 else 0
                        }
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun promoteGenericBinaryToApk(
        id: Long,
        destination: Uri,
        fileName: String
    ): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("", "bin", "dat")) return fileName
        if (!looksLikeApkArchive(destination)) return fileName

        val base = if (extension.isBlank()) {
            fileName
        } else {
            fileName.substringBeforeLast('.', fileName)
        }.ifBlank { "download" }
        val apkName = "$base.apk"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, apkName)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
        }
        val renamed = runCatching {
            resolver.update(destination, values, null, null) > 0
        }.getOrDefault(false)
        if (!renamed) return fileName

        updateRecordMetadata(id, apkName, APK_MIME_TYPE)
        return apkName
    }

    private fun validateCompletedPayload(fileName: String, destination: Uri) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("apk", "xapk", "apks", "aab", "zip", "jar")) return

        val signature = ByteArray(4)
        val read = resolver.openInputStream(destination)?.use { input ->
            var offset = 0
            while (offset < signature.size) {
                val count = input.read(signature, offset, signature.size - offset)
                if (count < 0) break
                if (count == 0) continue
                offset += count
            }
            offset
        } ?: 0

        val isZip = read >= 4 &&
            signature[0] == 0x50.toByte() &&
            signature[1] == 0x4B.toByte() &&
            ((signature[2] == 0x03.toByte() && signature[3] == 0x04.toByte()) ||
             (signature[2] == 0x05.toByte() && signature[3] == 0x06.toByte()) ||
             (signature[2] == 0x07.toByte() && signature[3] == 0x08.toByte()))

        if (!isZip) {
            throw InvalidDownloadPayloadException(
                "Server response is not a valid .$extension archive"
            )
        }
    }

    private fun createDestination(fileName: String, mimeType: String?): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    private fun newDirectRecord(
        url: String,
        fileName: String,
        mimeType: String?,
        expectedBytes: Long,
        destination: Uri,
        referrer: String?,
        isPrivate: Boolean
    ) = DownloadRecord(
        id = -System.nanoTime(),
        sourceUrl = url,
        fileName = fileName,
        mimeType = mimeType,
        createdAt = System.currentTimeMillis(),
        localUri = destination.toString(),
        expectedBytes = expectedBytes,
        directState = DIRECT_RUNNING,
        referrer = referrer,
        isPrivate = isPrivate
    )

    private fun writeBody(
        id: Long,
        destination: Uri,
        body: InputStream,
        fileName: String,
        totalBytes: Long,
        startingBytes: Long = 0L,
        append: Boolean = false,
        onFinished: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        Thread({
            var copied = startingBytes.coerceAtLeast(0L)
            var completedFileName = fileName
            var lastNotificationAt = 0L
            var lastSpeedAt = android.os.SystemClock.elapsedRealtime()
            var lastSpeedBytes = copied
            var speedBytesPerSecond = 0L

            activeBodies[id] = body
            liveTransfers[id] = LiveTransfer(copied, totalBytes, 0L)

            try {
                if (cancelledIds.contains(id)) {
                    throw java.io.InterruptedIOException("Download cancelled")
                }
                notifyDownloadProgress(id, fileName, copied, totalBytes)
                resolver.openOutputStream(destination, if (append) "wa" else "w")?.use { output ->
                    body.use { input ->
                        val buffer = ByteArray(DIRECT_COPY_BUFFER_BYTES)
                        while (true) {
                            if (cancelledIds.contains(id)) {
                                throw java.io.InterruptedIOException("Download cancelled")
                            }
                            if (!waitUntilResumed(id)) {
                                throw java.io.InterruptedIOException("Download cancelled")
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            copied += count

                            val now = android.os.SystemClock.elapsedRealtime()
                            val elapsed = now - lastSpeedAt
                            if (elapsed >= 500L) {
                                val instant = ((copied - lastSpeedBytes).coerceAtLeast(0L) * 1000L / elapsed.coerceAtLeast(1L))
                                speedBytesPerSecond = if (speedBytesPerSecond <= 0L) {
                                    instant
                                } else {
                                    (speedBytesPerSecond * 3L + instant) / 4L
                                }
                                lastSpeedAt = now
                                lastSpeedBytes = copied
                            }
                            liveTransfers[id] = LiveTransfer(copied, totalBytes, speedBytesPerSecond)

                            if (now - lastNotificationAt >= 500L) {
                                notifyDownloadProgress(id, fileName, copied, totalBytes)
                                lastNotificationAt = now
                            }
                        }
                        output.flush()
                    }
                } ?: error("Could not open Downloads destination")

                if (cancelledIds.contains(id)) {
                    throw java.io.InterruptedIOException("Download cancelled")
                }
                completedFileName = promoteGenericBinaryToApk(id, destination, fileName)
                validateCompletedPayload(completedFileName, destination)
                val done = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(destination, done, null, null)
                pausedIds.remove(id)
                markDirectSuccess(id, copied)
                liveTransfers[id] = LiveTransfer(copied, copied, 0L)
                notifyDownloadComplete(id, completedFileName, copied)
                cancelledIds.remove(id)
                runCatching { onFinished?.invoke() }
            } catch (error: Throwable) {
                runCatching { body.close() }
                val cancelled = cancelledIds.remove(id)
                cancelDownloadNotification(id)
                runCatching { onFinished?.invoke() }
                if (cancelled) {
                    pausedIds.remove(id)
                    runCatching { resolver.delete(destination, null, null) }
                } else if (error is InvalidDownloadPayloadException) {
                    pausedIds.remove(id)
                    markDirectFailed(id, destination)
                    notifyDownloadFailed(id, completedFileName)
                } else if (onFailure != null) {
                    pausedIds.remove(id)
                    onFailure()
                } else {
                    pausedIds.remove(id)
                    markDirectFailed(id, destination)
                    notifyDownloadFailed(id, completedFileName)
                }
            } finally {
                DownloadKeepAliveService.finish(id)
                activeBodies.remove(id)
                wakePaused(id)
                pauseLocks.remove(id)
                if (cancelledIds.contains(id)) cancelledIds.remove(id)
                if (liveTransfers[id]?.downloadedBytes == copied &&
                    liveTransfers[id]?.speedBytesPerSecond != 0L) {
                    liveTransfers[id] = liveTransfers[id]!!.copy(speedBytesPerSecond = 0L)
                }
            }
        }, "ILYRO-download-${kotlin.math.abs(id)}").start()
    }

    private fun waitUntilResumed(id: Long): Boolean {
        if (!pausedIds.contains(id)) return !cancelledIds.contains(id)
        val lock = pauseLocks.computeIfAbsent(id) { java.lang.Object() }
        synchronized(lock) {
            while (pausedIds.contains(id) && !cancelledIds.contains(id)) {
                try {
                    lock.wait(1000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return !cancelledIds.contains(id)
    }

    private fun wakePaused(id: Long) {
        pauseLocks[id]?.let { lock ->
            synchronized(lock) { lock.notifyAll() }
        }
    }

    private fun pause(id: Long): Boolean {
        val record = synchronized(recordLock) {
            restoreRecordsUnsafe().firstOrNull { it.id == id }
        } ?: return false
        if (record.localUri == null || record.directState != DIRECT_RUNNING) return false

        pausedIds.add(id)
        updateDirectState(id, DIRECT_PAUSED)
        liveTransfers.computeIfPresent(id) { _, live -> live.copy(speedBytesPerSecond = 0L) }
        DownloadKeepAliveService.setPaused(id, true)
        return true
    }

    fun pause(item: DownloadUiItem): Boolean = pause(item.record.id)

    private fun resume(id: Long): Boolean {
        val record = synchronized(recordLock) {
            restoreRecordsUnsafe().firstOrNull { it.id == id }
        } ?: return false
        if (record.localUri == null || record.directState != DIRECT_PAUSED) return false

        pausedIds.remove(id)
        updateDirectState(id, DIRECT_RUNNING)
        if (!DownloadKeepAliveService.track(appContext, id, record.fileName)) {
            pausedIds.add(id)
            updateDirectState(id, DIRECT_PAUSED)
            liveTransfers.computeIfPresent(id) { _, live -> live.copy(speedBytesPerSecond = 0L) }
            return false
        }
        DownloadKeepAliveService.setPaused(id, false)
        wakePaused(id)

        val liveWorker = activeBodies.containsKey(id) ||
            activeHlsPools.containsKey(id) ||
            activeHlsBodies.containsKey(id)
        if (liveWorker) return true

        // If Android recreated the process while a normal direct download was paused, resume the
        // existing MediaStore file with a Range request. HLS has its own live segmented worker and
        // is intentionally not converted into a raw playlist download here.
        return resumePersistedDirect(record).also { started ->
            if (!started) {
                pausedIds.add(id)
                updateDirectState(id, DIRECT_PAUSED)
                DownloadKeepAliveService.setPaused(id, true)
            }
        }
    }

    fun resume(item: DownloadUiItem): Boolean = resume(item.record.id)

    private fun resumePersistedDirect(record: DownloadRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val destinationText = record.localUri ?: return false
        val sourceUri = runCatching { Uri.parse(record.sourceUrl) }.getOrNull() ?: return false
        val scheme = sourceUri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (sourceUri.lastPathSegment.orEmpty().lowercase().contains(".m3u8")) return false

        val destination = Uri.parse(destinationText)
        val existingBytes = mediaSize(destinationText).coerceAtLeast(0L)
        val request = WebRequest.Builder(record.sourceUrl).apply {
            if (!record.referrer.isNullOrBlank() &&
                (record.referrer.startsWith("http://") || record.referrer.startsWith("https://"))) {
                referrer(record.referrer)
            }
            if (existingBytes > 0L) addHeader("Range", "bytes=$existingBytes-")
        }.build()
        val flags = if (record.isPrivate) {
            GeckoWebExecutor.FETCH_FLAGS_PRIVATE
        } else {
            GeckoWebExecutor.FETCH_FLAGS_NONE
        }

        fun pauseAgain() {
            pausedIds.add(record.id)
            updateDirectState(record.id, DIRECT_PAUSED)
            liveTransfers.computeIfPresent(record.id) { _, live -> live.copy(speedBytesPerSecond = 0L) }
            DownloadKeepAliveService.setPaused(record.id, true)
        }

        return runCatching {
            executor.fetch(request, flags).accept(
                { response ->
                    val body = response?.body
                    if (response == null || response.statusCode !in 200..299 || body == null) {
                        runCatching { body?.close() }
                        pauseAgain()
                        return@accept
                    }
                    response.setReadTimeoutMillis(BODY_READ_TIMEOUT_MS)
                    val append = existingBytes > 0L && response.statusCode == 206
                    val startingBytes = if (append) existingBytes else 0L
                    val responseBytes = header(response, "content-length")?.trim()?.toLongOrNull() ?: -1L
                    val totalBytes = when {
                        append && responseBytes > 0L -> startingBytes + responseBytes
                        responseBytes > 0L -> responseBytes
                        record.expectedBytes > 0L -> record.expectedBytes
                        else -> -1L
                    }
                    if (totalBytes > 0L) updateExpectedBytes(record.id, totalBytes)
                    writeBody(
                        id = record.id,
                        destination = destination,
                        body = body,
                        fileName = record.fileName,
                        totalBytes = totalBytes,
                        startingBytes = startingBytes,
                        append = append
                    )
                },
                { _ -> pauseAgain() }
            )
            true
        }.getOrElse {
            pauseAgain()
            false
        }
    }

    private fun sampleManagerSpeed(id: Long, downloaded: Long, status: Int): Long {
        if (status != DownloadManager.STATUS_RUNNING) {
            managerSpeedSamples.remove(id)
            return 0L
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = managerSpeedSamples[id]
        val speed = if (previous != null && now > previous.timestampMs && downloaded >= previous.downloadedBytes) {
            val elapsed = (now - previous.timestampMs).coerceAtLeast(1L)
            ((downloaded - previous.downloadedBytes) * 1000L / elapsed).coerceAtLeast(0L)
        } else {
            previous?.speedBytesPerSecond ?: 0L
        }
        managerSpeedSamples[id] = SpeedSample(downloaded, now, speed)
        return speed
    }

    private fun notificationId(id: Long): Int {
        val folded = id xor (id ushr 32)
        return (folded and 0x7fffffffL).toInt().coerceAtLeast(1)
    }

    private fun launchPendingIntent(): PendingIntent? {
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.setAction(ACTION_OPEN_DOWNLOADS)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return null
        return PendingIntent.getActivity(
            appContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyDownloadProgress(id: Long, fileName: String, downloaded: Long, total: Long) {
        // Direct Gecko/HLS transfers are already backed by the foreground service. Updating
        // that service notification avoids showing a second, duplicate progress notification.
        DownloadKeepAliveService.updateProgress(id, fileName, downloaded, total)
    }

    private fun notifyDownloadComplete(id: Long, fileName: String, bytes: Long) {
        val builder = Notification.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ilyro_download)
            .setContentTitle(fileName)
            .setContentText("Download complete • ${formatBytes(bytes)}")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
        launchPendingIntent()?.let { builder.setContentIntent(it) }
        runCatching { notificationManager.notify(notificationId(id), builder.build()) }
    }

    private fun notifyDownloadFailed(id: Long, fileName: String) {
        val builder = Notification.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ilyro_download)
            .setContentTitle(fileName)
            .setContentText("Download failed")
            .setAutoCancel(true)
            .setOngoing(false)
        launchPendingIntent()?.let { builder.setContentIntent(it) }
        runCatching { notificationManager.notify(notificationId(id), builder.build()) }
    }

    private fun cancelDownloadNotification(id: Long) {
        runCatching { notificationManager.cancel(notificationId(id)) }
    }

    private fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", safe / (1024.0 * 1024.0 * 1024.0))
            safe >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
            safe >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", safe / 1024.0)
            else -> "$safe B"
        }
    }

    fun retry(item: DownloadUiItem, allowMetered: Boolean): DownloadRecord? {
        val source = item.record
        remove(item)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val geckoRetry = enqueueGeckoFetch(
                url = source.sourceUrl,
                fileName = source.fileName,
                mimeType = source.mimeType,
                expectedBytes = source.expectedBytes,
                allowMetered = allowMetered,
                referrer = source.referrer,
                isPrivate = source.isPrivate
            )
            if (geckoRetry != null) return geckoRetry
        }
        return enqueueUrl(
            source.sourceUrl,
            source.fileName,
            source.mimeType,
            allowMetered,
            source.referrer,
            source.isPrivate
        )
    }

    private fun managerStatus(id: Long): Int? {
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                cursor.getInt(column)
            }
        }.getOrNull()
    }

    private fun promoteManagerBinaryToApk(record: DownloadRecord): Boolean {
        val extension = downloadExtension(record.fileName)
        if (extension !in setOf("", "bin", "dat")) return false
        val sourceUri = manager.getUriForDownloadedFile(record.id) ?: return false
        if (!looksLikeApkArchive(sourceUri)) return false

        val base = if (extension.isBlank()) {
            record.fileName
        } else {
            record.fileName.substringBeforeLast('.', record.fileName)
        }.ifBlank { "download" }
        val apkName = "$base.apk"
        val destination = createDestination(apkName, APK_MIME_TYPE) ?: return false
        var copied = 0L

        val copiedSuccessfully = runCatching {
            val input = resolver.openInputStream(sourceUri) ?: error("Could not open completed download")
            val output = resolver.openOutputStream(destination, "w") ?: error("Could not create APK destination")
            input.use { source ->
                output.use { target ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        target.write(buffer, 0, count)
                        copied += count
                    }
                    target.flush()
                }
            }
            true
        }.getOrElse { false }

        if (!copiedSuccessfully) {
            runCatching { resolver.delete(destination, null, null) }
            return false
        }

        val done = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
            put(MediaStore.Downloads.DISPLAY_NAME, apkName)
        }
        if (runCatching { resolver.update(destination, done, null, null) }.getOrDefault(0) <= 0) {
            runCatching { resolver.delete(destination, null, null) }
            return false
        }

        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { current ->
                if (current.id == record.id) {
                    current.copy(
                        fileName = apkName,
                        mimeType = APK_MIME_TYPE,
                        localUri = destination.toString(),
                        expectedBytes = copied,
                        directState = DIRECT_SUCCESS
                    )
                } else {
                    current
                }
            }
            saveRecordsUnsafe(records)
        }

        // DownloadManager.remove() also removes the original completed .bin file, leaving only
        // the correctly named MediaStore APK copy.
        runCatching { manager.remove(record.id) }
        managerSpeedSamples.remove(record.id)
        notifyDownloadComplete(record.id, apkName, copied)
        return true
    }

    private fun monitorManagerApkPromotion(record: DownloadRecord) {
        if (record.id < 0L || record.localUri != null) return
        if (downloadExtension(record.fileName) !in setOf("", "bin", "dat")) return
        if (!managerPromotionInFlight.add(record.id)) return

        Thread({
            try {
                repeat(7_200) {
                    when (managerStatus(record.id)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            promoteManagerBinaryToApk(record)
                            return@Thread
                        }
                        DownloadManager.STATUS_FAILED -> return@Thread
                        null -> return@Thread
                    }
                    try {
                        Thread.sleep(500L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@Thread
                    }
                }
            } finally {
                managerPromotionInFlight.remove(record.id)
            }
        }, "ILYRO-manager-apk-${record.id}").start()
    }

    private fun enqueueUrl(
        url: String,
        fileName: String,
        mimeType: String?,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean
    ): DownloadRecord? {
        return try {
            val uri = Uri.parse(url)
            if (uri.scheme != "http" && uri.scheme != "https") return null
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("Downloading with ILYRO")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(true)
            if (mimeType != null) request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", GeckoSession.getDefaultUserAgent())
            request.addRequestHeader("Accept", "*/*")
            if (!referrer.isNullOrBlank() &&
                (referrer.startsWith("http://") || referrer.startsWith("https://"))) {
                request.addRequestHeader("Referer", referrer)
            }

            val id = manager.enqueue(request)
            val record = DownloadRecord(
                id = id,
                sourceUrl = url,
                fileName = fileName,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis(),
                referrer = referrer,
                isPrivate = isPrivate
            )
            addRecord(record)
            monitorManagerApkPromotion(record)
            record
        } catch (_: Exception) {
            null
        }
    }

    fun snapshot(): List<DownloadUiItem> {
        val records = synchronized(recordLock) { restoreRecordsUnsafe() }
        if (records.isEmpty()) return emptyList()
        val statusById = mutableMapOf<Long, DownloadUiItem>()

        records.filter { it.localUri != null }.forEach { record ->
            val live = liveTransfers[record.id]
            val mediaBytes = mediaSize(record.localUri).coerceAtLeast(0L)
            val bytes = live?.downloadedBytes ?: mediaBytes
            val status = when (record.directState) {
                DIRECT_RUNNING -> DownloadManager.STATUS_RUNNING
                DIRECT_PAUSED -> DownloadManager.STATUS_PAUSED
                DIRECT_SUCCESS -> DownloadManager.STATUS_SUCCESSFUL
                DIRECT_FAILED -> DownloadManager.STATUS_FAILED
                else -> DownloadManager.STATUS_FAILED
            }
            val liveTotal = live?.totalBytes ?: -1L
            val total = when {
                liveTotal > 0L -> liveTotal
                record.expectedBytes > 0L -> record.expectedBytes
                status == DownloadManager.STATUS_SUCCESSFUL -> bytes
                else -> -1L
            }
            statusById[record.id] = DownloadUiItem(
                record = record,
                status = status,
                downloadedBytes = bytes,
                totalBytes = total,
                speedBytesPerSecond = if (status == DownloadManager.STATUS_RUNNING) {
                    live?.speedBytesPerSecond ?: 0L
                } else {
                    0L
                }
            )
        }

        val managerIds = records
            .filter { it.localUri == null && it.id >= 0L }
            .map { it.id }
            .toLongArray()
        if (managerIds.isNotEmpty()) {
            manager.query(DownloadManager.Query().setFilterById(*managerIds))?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val downloadedColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val record = records.firstOrNull { it.id == id } ?: continue
                    val status = cursor.getInt(statusColumn)
                    val downloaded = cursor.getLong(downloadedColumn).coerceAtLeast(0L)
                    val total = cursor.getLong(totalColumn)
                    statusById[id] = DownloadUiItem(
                        record = record,
                        status = status,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSecond = sampleManagerSpeed(id, downloaded, status)
                    )
                }
            }
        }

        records.forEach { record ->
            val item = statusById[record.id] ?: return@forEach
            if (item.status == DownloadManager.STATUS_SUCCESSFUL &&
                record.localUri == null &&
                downloadExtension(record.fileName) in setOf("", "bin", "dat")
            ) {
                monitorManagerApkPromotion(record)
            }
        }

        return records.map { record ->
            statusById[record.id]
                ?: DownloadUiItem(record, DownloadManager.STATUS_FAILED, 0L, record.expectedBytes)
        }
    }

    private fun launchDownloadedFile(uri: Uri, mimeType: String, fileName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
        intent.clipData = ClipData.newRawUri(fileName, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun open(item: DownloadUiItem): Boolean {
        val uri = item.record.localUri?.let(Uri::parse)
            ?: manager.getUriForDownloadedFile(item.record.id)
            ?: return false
        val detectedMimeType = (item.record.mimeType
            ?: if (item.record.localUri == null) {
                manager.getMimeTypeForDownloadedFile(item.record.id)
            } else {
                resolver.getType(uri)
            })?.substringBefore(';')?.trim()?.lowercase()

        val extension = item.record.fileName.substringAfterLast('.', "").lowercase()
        val videoExtensions = setOf(
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts",
            "3gp", "3gpp", "flv", "mpeg", "mpg", "m3u8"
        )
        val isVideo = detectedMimeType?.startsWith("video/") == true || extension in videoExtensions
        val isApk = extension == "apk" ||
            detectedMimeType == "application/vnd.android.package-archive"
        val genericMime = detectedMimeType.isNullOrBlank() || detectedMimeType in setOf(
            "application/octet-stream", "binary/octet-stream", "application/binary", "*/*"
        )
        val inferredMime = when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "3gp", "3gpp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "mpeg", "mpg" -> "video/mpeg"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        val mimeType = when {
            isApk -> APK_MIME_TYPE
            isVideo -> "video/*"
            genericMime -> inferredMime ?: "*/*"
            else -> detectedMimeType!!
        }

        if (isApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            prefs.edit()
                .putString(PREF_PENDING_APK_INSTALL_URI, uri.toString())
                .apply()
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                appContext.startActivity(permissionIntent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }

        val candidates = buildList {
            add(mimeType)
            if (isVideo) {
                inferredMime?.let(::add)
                add("video/*")
            }
            if (mimeType != "*/*") add("*/*")
        }.distinct()
        return candidates.any { candidate -> launchDownloadedFile(uri, candidate, item.record.fileName) }
    }


    fun rename(item: DownloadUiItem, requestedName: String): Boolean {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return false
        val currentName = item.record.fileName
        val cleaned = cleanDownloadName(requestedName)?.take(180) ?: return false
        if (cleaned == "." || cleaned == "..") return false
        val currentExtension = downloadExtension(currentName)
        val requestedExtension = downloadExtension(cleaned)
        val finalName = if (requestedExtension.isBlank() && currentExtension.isNotBlank()) {
            cleaned + "." + currentExtension
        } else {
            cleaned
        }
        if (finalName == currentName) return true

        val uri = item.record.localUri?.let(Uri::parse)
            ?: manager.getUriForDownloadedFile(item.record.id)
            ?: return false
        val changed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, finalName)
                    },
                    null,
                    null
                ) > 0
            }.getOrDefault(false)
        } else {
            false
        }
        if (!changed) return false

        val newMimeType = resolveDownloadMimeType(
            finalName,
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(downloadExtension(finalName))
        )
        updateRecordMetadata(item.record.id, finalName, newMimeType ?: item.record.mimeType)
        return true
    }

    fun share(item: DownloadUiItem): Boolean {
        val uri = item.record.localUri?.let(Uri::parse)
            ?: manager.getUriForDownloadedFile(item.record.id)
            ?: return false
        val mimeType = item.record.mimeType
            ?: resolver.getType(uri)
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(item.record.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            appContext.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun moveTo(item: DownloadUiItem, destination: Uri): Boolean {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return false
        val source = item.record.localUri?.let(Uri::parse)
            ?: manager.getUriForDownloadedFile(item.record.id)
            ?: return false
        if (source == destination) return false

        val copied = runCatching {
            val input = resolver.openInputStream(source) ?: return@runCatching false
            val output = resolver.openOutputStream(destination, "w") ?: return@runCatching false
            input.use { inputStream ->
                output.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        }.getOrDefault(false)
        if (!copied) return false

        remove(item)
        return true
    }

    fun cancel(item: DownloadUiItem) {
        val record = item.record
        if (record.localUri != null) {
            cancelledIds.add(record.id)
            pausedIds.remove(record.id)
            wakePaused(record.id)
            activeBodies.remove(record.id)?.let { stream -> runCatching { stream.close() } }
            activeHlsPools.remove(record.id)?.shutdownNow()
            activeHlsBodies.remove(record.id)?.forEach { stream -> runCatching { stream.close() } }
            liveTransfers.remove(record.id)
            pauseLocks.remove(record.id)
            cancelDownloadNotification(record.id)
            DownloadKeepAliveService.finish(record.id)
            runCatching { resolver.delete(Uri.parse(record.localUri), null, null) }
        } else if (record.id >= 0L) {
            manager.remove(record.id)
            managerSpeedSamples.remove(record.id)
        }
        synchronized(recordLock) {
            saveRecordsUnsafe(restoreRecordsUnsafe().filterNot { it.id == record.id })
        }
    }

    fun remove(item: DownloadUiItem) {
        if (item.status == DownloadManager.STATUS_RUNNING ||
            item.status == DownloadManager.STATUS_PENDING ||
            item.status == DownloadManager.STATUS_PAUSED
        ) {
            cancel(item)
            return
        }
        if (item.record.localUri != null) {
            liveTransfers.remove(item.record.id)
            cancelledIds.remove(item.record.id)
            cancelDownloadNotification(item.record.id)
            runCatching { resolver.delete(Uri.parse(item.record.localUri), null, null) }
        } else if (item.record.id >= 0L) {
            manager.remove(item.record.id)
            managerSpeedSamples.remove(item.record.id)
        }
        synchronized(recordLock) {
            saveRecordsUnsafe(restoreRecordsUnsafe().filterNot { it.id == item.record.id })
        }
    }

    fun clearAll() {
        snapshot().forEach { item -> remove(item) }
        pausedIds.clear()
        pauseLocks.clear()
        synchronized(recordLock) { saveRecordsUnsafe(emptyList()) }
    }

    private fun addRecord(record: DownloadRecord) {
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().filterNot { it.id == record.id }.toMutableList()
            records.add(0, record)
            saveRecordsUnsafe(records)
        }
    }

    private fun updateDirectState(id: Long, state: Int) {
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { record ->
                if (record.id == id) record.copy(directState = state) else record
            }
            saveRecordsUnsafe(records)
        }
    }

    private fun markDirectSuccess(id: Long, actualBytes: Long) {
        val finalBytes = actualBytes.coerceAtLeast(0L)
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { record ->
                if (record.id == id) {
                    record.copy(
                        directState = DIRECT_SUCCESS,
                        expectedBytes = finalBytes
                    )
                } else {
                    record
                }
            }
            saveRecordsUnsafe(records)
        }
    }

    private fun updateExpectedBytes(id: Long, expectedBytes: Long) {
        if (expectedBytes <= 0L) return
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { record ->
                if (record.id == id) record.copy(expectedBytes = expectedBytes) else record
            }
            saveRecordsUnsafe(records)
        }
    }

    private fun updateRecordMetadata(id: Long, fileName: String, mimeType: String?) {
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { record ->
                if (record.id == id) {
                    record.copy(fileName = fileName, mimeType = mimeType)
                } else {
                    record
                }
            }
            saveRecordsUnsafe(records)
        }
    }

    private fun retryAfterDirectFailure(
        failedId: Long,
        failedDestination: Uri,
        url: String,
        fileName: String,
        mimeType: String?,
        expectedBytes: Long,
        allowMetered: Boolean,
        referrer: String?,
        isPrivate: Boolean,
        retryWithGecko: Boolean
    ) {
        cleanupDirectRecord(failedId, failedDestination)

        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            addFailedRecord(
                failedId, url, fileName, mimeType, expectedBytes, referrer, isPrivate
            )
            return
        }

        if (retryWithGecko) {
            val retried = enqueueGeckoFetch(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                expectedBytes = expectedBytes,
                allowMetered = allowMetered,
                referrer = referrer,
                isPrivate = isPrivate
            )
            if (retried != null) return
        }

        val system = enqueueUrl(
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            allowMetered = allowMetered,
            referrer = referrer,
            isPrivate = isPrivate
        )
        if (system == null) {
            addFailedRecord(
                failedId, url, fileName, mimeType, expectedBytes, referrer, isPrivate
            )
        }
    }

    private fun cleanupDirectRecord(id: Long, destination: Uri) {
        DownloadKeepAliveService.finish(id)
        runCatching { resolver.delete(destination, null, null) }
        synchronized(recordLock) {
            saveRecordsUnsafe(restoreRecordsUnsafe().filterNot { it.id == id })
        }
    }

    private fun addFailedRecord(
        id: Long,
        url: String,
        fileName: String,
        mimeType: String?,
        expectedBytes: Long,
        referrer: String?,
        isPrivate: Boolean
    ) {
        addRecord(
            DownloadRecord(
                id = id,
                sourceUrl = url,
                fileName = fileName,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis(),
                expectedBytes = expectedBytes,
                directState = DIRECT_FAILED,
                referrer = referrer,
                isPrivate = isPrivate
            )
        )
    }

    private fun markDirectFailed(id: Long, destination: Uri) {
        runCatching { resolver.delete(destination, null, null) }
        synchronized(recordLock) {
            val records = restoreRecordsUnsafe().map { record ->
                if (record.id == id) {
                    record.copy(localUri = null, directState = DIRECT_FAILED)
                } else record
            }
            saveRecordsUnsafe(records)
        }
    }

    private fun mediaSize(uriText: String?): Long {
        if (uriText.isNullOrBlank()) return -1L
        return runCatching {
            resolver.query(Uri.parse(uriText), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun restoreRecordsUnsafe(): List<DownloadRecord> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", Long.MIN_VALUE)
                    val sourceUrl = item.optString("sourceUrl").trim()
                    val fileName = item.optString("fileName").trim()
                    if (id == Long.MIN_VALUE || sourceUrl.isBlank() || fileName.isBlank()) continue
                    add(
                        DownloadRecord(
                            id = id,
                            sourceUrl = sourceUrl,
                            fileName = fileName,
                            mimeType = item.optString("mimeType").takeIf { it.isNotBlank() && it != "null" },
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            localUri = item.optString("localUri").takeIf { it.isNotBlank() && it != "null" },
                            expectedBytes = item.optLong("expectedBytes", -1L),
                            directState = item.optInt("directState", DIRECT_NONE),
                            referrer = item.optString("referrer").takeIf { it.isNotBlank() && it != "null" },
                            isPrivate = item.optBoolean("isPrivate", false)
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun saveRecordsUnsafe(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("sourceUrl", record.sourceUrl)
                    .put("fileName", record.fileName)
                    .put("mimeType", record.mimeType)
                    .put("createdAt", record.createdAt)
                    .put("localUri", record.localUri)
                    .put("expectedBytes", record.expectedBytes)
                    .put("directState", record.directState)
                    .put("referrer", record.referrer)
                    .put("isPrivate", record.isPrivate)
            )
        }
        prefs.edit().putString(KEY_DOWNLOADS, array.toString()).apply()
    }

    private fun header(response: WebResponse, name: String): String? =
        response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val KEY_DOWNLOADS = "downloads_v1"
        const val MAX_RECORDS = 250
        const val DOWNLOAD_CHANNEL_ID = "ilyro_downloads"
        const val DOWNLOAD_SESSION_RESPONSE_TIMEOUT_MS = 12_000L
        const val DOWNLOAD_PAGE_SETTLE_GRACE_MS = 3_500L
        const val NAVIGATION_DEDUPE_WINDOW_MS = 2_000L
        const val NAVIGATION_DEDUPE_RETENTION_MS = 15_000L
        const val HLS_FETCH_TIMEOUT_MS = 45_000L
        const val HLS_MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
        const val HLS_PARALLEL_FETCHES = 4
        const val DIRECT_COPY_BUFFER_BYTES = 1024 * 1024
        const val HLS_COPY_BUFFER_BYTES = 512 * 1024
        const val MAX_HLS_QUALITY_VARIANTS = 12
    }
}
