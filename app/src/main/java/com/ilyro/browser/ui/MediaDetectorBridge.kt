package com.ilyro.browser.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

internal enum class DetectedMediaKind {
    VIDEO,
    AUDIO,
    HLS,
    DASH
}

internal data class DetectedMedia(
    val url: String,
    val kind: DetectedMediaKind,
    val mimeType: String?,
    val pageUrl: String?,
    val title: String?,
    val source: String,
    val width: Int,
    val height: Int,
    val bitrate: Long = 0L,
    val frameRate: Double = 0.0,
    val codecs: String? = null,
    val variantLabel: String? = null,
    val hlsHasSeparateAudio: Boolean = false,
    val requiresSeparateAudio: Boolean = false,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = firstSeenAt
) {
    val isYouTubeStream: Boolean
        get() = source.startsWith("youtube-")

    val isYouTubeExtractor: Boolean
        get() = source == "youtube-extractor"

    val canDownload: Boolean
        get() = (kind == DetectedMediaKind.VIDEO &&
            (!requiresSeparateAudio || isYouTubeStream)) ||
            kind == DetectedMediaKind.AUDIO ||
            (kind == DetectedMediaKind.HLS && !hlsHasSeparateAudio)

    val qualityLabel: String
        get() = when {
            height > 0 -> "${height}p"
            !variantLabel.isNullOrBlank() -> variantLabel
            kind == DetectedMediaKind.AUDIO -> "Audio"
            kind == DetectedMediaKind.HLS || kind == DetectedMediaKind.DASH -> "Auto"
            else -> "Video"
        }
}

/**
 * Receives media candidates discovered by ILYRO's bundled content extension.
 *
 * Detection state is intentionally memory-only. It is scoped to a GeckoSession and is cleared
 * on navigation/close, which also keeps private tabs from persisting media history.
 */
internal object MediaDetectorBridge {
    private const val NATIVE_APP = "ilyro_media"
    private const val MAX_MEDIA_PER_SESSION = 80
    private val EPHEMERAL_QUERY_KEYS = setOf(
        "token", "sig", "signature", "expires", "expire", "exp", "policy",
        "key-pair-id", "hdnts", "hdntl", "auth", "authorization",
        "timestamp", "ts", "t", "st", "e", "ttl", "session", "session_id", "sid",
        "nonce", "rnd", "random", "cache", "cachebust", "cb", "_",
        "range", "rn", "rbuf"
    )

    private var extension: WebExtension? = null
    private val boundSessions = mutableSetOf<GeckoSession>()
    private val mediaBySession = mutableMapOf<GeckoSession, LinkedHashMap<String, DetectedMedia>>()

    var revision by mutableIntStateOf(0)
        private set

    fun attach(value: WebExtension) {
        extension = value
        boundSessions.toList().forEach { install(it, value) }
    }

    fun bind(session: GeckoSession) {
        boundSessions.add(session)
        extension?.let { install(session, it) }
    }

    fun unbind(session: GeckoSession) {
        boundSessions.remove(session)
        if (mediaBySession.remove(session) != null) revision += 1
    }

    fun clear(session: GeckoSession) {
        if (mediaBySession.remove(session) != null) revision += 1
    }

    fun itemsFor(session: GeckoSession): List<DetectedMedia> {
        return mediaBySession[session]
            ?.values
            ?.sortedWith(
                compareByDescending<DetectedMedia> { it.lastSeenAt }
                    .thenBy { mediaPriority(it.kind) }
                    .thenByDescending { it.width * it.height }
            )
            .orEmpty()
    }

    internal fun selectPrimaryCandidate(media: List<DetectedMedia>): DetectedMedia? {
        val visual = media.filter { it.kind != DetectedMediaKind.AUDIO }
        val candidates = if (visual.isNotEmpty()) visual else media
        if (candidates.isEmpty()) return null

        val newestSeenAt = candidates.maxOf { it.lastSeenAt }
        return candidates
            .asSequence()
            .filter { it.lastSeenAt == newestSeenAt }
            .maxWithOrNull(
                compareBy<DetectedMedia> { if (it.canDownload) 1 else 0 }
                    .thenBy { if (it.height > 0) 1 else 0 }
                    .thenBy { it.height }
                    .thenBy { it.width }
                    .thenBy { it.bitrate }
            )
    }

    /**
     * YouTube changes the visible URL during SPA navigation and can use www, m, youtu.be or an
     * embed host for the same player. Keep audio pairing attached to the video id instead of the
     * full, often-changing page URL.
     */
    internal fun youtubePageIdentity(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(rawUrl)
            val host = uri.host.orEmpty().lowercase().trimEnd('.')
            val isYouTubeHost = host == "youtu.be" ||
                host.endsWith(".youtube.com") ||
                host == "youtube.com" ||
                host.endsWith(".youtube-nocookie.com") ||
                host == "youtube-nocookie.com"
            if (!isYouTubeHost) return@runCatching null

            val segments = uri.pathSegments
            val videoId = when {
                host == "youtu.be" -> segments.firstOrNull()
                uri.path.equals("/watch", ignoreCase = true) -> uri.getQueryParameter("v")
                segments.size >= 2 && segments.first().lowercase() in setOf("shorts", "embed", "live") ->
                    segments[1]
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() }

            videoId?.let { "video:$it" } ?: "page:${host}|${uri.path.orEmpty()}"
        }.getOrNull()
    }

    internal fun sameYoutubePage(firstUrl: String?, secondUrl: String?): Boolean {
        val first = youtubePageIdentity(firstUrl)
        val second = youtubePageIdentity(secondUrl)
        return if (first != null && second != null) first == second else firstUrl == secondUrl
    }

    /**
     * A page-level YouTube candidate keeps the media action useful when GeckoView does not expose
     * the signed googlevideo requests to the bundled detector. The actual stream is resolved by
     * [YouTubeExtractor] only when the media sheet is opened or the user starts a download.
     */
    internal fun youtubePageCandidate(pageUrl: String?, title: String?): DetectedMedia? {
        val identity = youtubePageIdentity(pageUrl) ?: return null
        if (!identity.startsWith("video:")) return null
        val normalizedUrl = pageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return DetectedMedia(
            url = normalizedUrl,
            kind = DetectedMediaKind.VIDEO,
            mimeType = "video/mp4",
            pageUrl = normalizedUrl,
            title = title?.trim()?.takeIf { it.isNotBlank() },
            source = "youtube-extractor",
            width = 0,
            height = 0,
            variantLabel = "Best available"
        )
    }

    // Media detection is enabled on YouTube too.
    fun isExcludedUrl(url: String): Boolean = false

    private fun install(session: GeckoSession, extension: WebExtension) {
        session.webExtensionController.setMessageDelegate(
            extension,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val json = message as? JSONObject ?: return
                            if (json.optString("type") != "media") return

                            val pageUrl = json.optString("pageUrl").trim().takeIf { it.isNotEmpty() }
                            if (pageUrl != null && isExcludedUrl(pageUrl)) return

                            val mediaUrl = json.optString("url").trim()
                            if (!mediaUrl.startsWith("https://", ignoreCase = true) &&
                                !mediaUrl.startsWith("http://", ignoreCase = true)) return

                            val kind = when (json.optString("kind").lowercase()) {
                                "video" -> DetectedMediaKind.VIDEO
                                "audio" -> DetectedMediaKind.AUDIO
                                "hls" -> DetectedMediaKind.HLS
                                "dash" -> DetectedMediaKind.DASH
                                else -> return
                            }
                            val mime = json.optString("mime").trim().takeIf { it.isNotEmpty() }
                            val title = json.optString("title").trim().takeIf { it.isNotEmpty() }
                            val source = json.optString("source").trim().takeIf { it.isNotEmpty() } ?: "page"
                            val width = json.optInt("width", 0).coerceAtLeast(0)
                            val height = json.optInt("height", 0).coerceAtLeast(0)
                            val bitrate = json.optLong("bitrate", 0L).coerceAtLeast(0L)
                            val frameRate = json.optDouble("frameRate", 0.0).coerceAtLeast(0.0)
                            val codecs = json.optString("codecs").trim().takeIf { it.isNotEmpty() }
                            val requiresSeparateAudio = json.optBoolean("separateAudio", false)
                            val now = System.currentTimeMillis()

                            val items = mediaBySession.getOrPut(session) { linkedMapOf() }
                            val key = canonicalMediaIdentity(mediaUrl)
                            val previous = items[key]
                            val next = if (previous == null) {
                                DetectedMedia(
                                    url = mediaUrl,
                                    kind = kind,
                                    mimeType = mime,
                                    pageUrl = pageUrl,
                                    title = title,
                                    source = source,
                                    width = width,
                                    height = height,
                                    bitrate = bitrate,
                                    frameRate = frameRate,
                                    codecs = codecs,
                                    requiresSeparateAudio = requiresSeparateAudio,
                                    firstSeenAt = now,
                                    lastSeenAt = now
                                )
                            } else {
                                previous.copy(
                                    // Keep the freshest signed URL while grouping URLs that differ only
                                    // by short-lived auth/cache parameters into one visible media item.
                                    url = mediaUrl,
                                    kind = preferKind(previous.kind, kind),
                                    mimeType = mime ?: previous.mimeType,
                                    pageUrl = pageUrl ?: previous.pageUrl,
                                    title = title ?: previous.title,
                                    source = preferSource(previous.source, source),
                                    width = maxOf(previous.width, width),
                                    height = maxOf(previous.height, height),
                                    bitrate = maxOf(previous.bitrate, bitrate),
                                    frameRate = maxOf(previous.frameRate, frameRate),
                                    codecs = codecs ?: previous.codecs,
                                    requiresSeparateAudio =
                                        previous.requiresSeparateAudio || requiresSeparateAudio,
                                    lastSeenAt = now
                                )
                            }

                            val meaningfulChange = previous == null ||
                                previous.copy(lastSeenAt = next.lastSeenAt) != next
                            items[key] = next
                            if (!meaningfulChange) return
                            while (items.size > MAX_MEDIA_PER_SESSION) {
                                items.remove(items.keys.first())
                            }
                            revision += 1
                        }
                    })
                }
            },
            NATIVE_APP
        )
    }

    internal fun canonicalMediaIdentity(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme.orEmpty().lowercase()
            val host = uri.host.orEmpty().lowercase().trimEnd('.')
            val port = uri.port
            val authority = if (port >= 0) "$host:$port" else host
            val path = uri.encodedPath.orEmpty().ifBlank { "/" }
            val queryParts = uri.queryParameterNames
                .filterNot { name ->
                    val normalized = name.lowercase()
                    normalized in EPHEMERAL_QUERY_KEYS || normalized.startsWith("x-amz-")
                }
                .sortedBy { it.lowercase() }
                .flatMap { name ->
                    val values = uri.getQueryParameters(name)
                    if (values.isEmpty()) listOf(name)
                    else values.sorted().map { value -> "$name=$value" }
                }
            buildString {
                append(scheme).append("://").append(authority).append(path)
                if (queryParts.isNotEmpty()) append('?').append(queryParts.joinToString("&"))
            }
        }.getOrDefault(url.substringBefore('#'))
    }

    private fun preferSource(old: String, new: String): String {
        fun rank(value: String): Int = when {
            value.startsWith("youtube-") -> 4
            value.startsWith("resource-") || value == "network" -> 3
            value.startsWith("source-") -> 2
            value == "video" || value == "audio" -> 1
            else -> 0
        }
        return if (rank(new) > rank(old)) new else old
    }

    private fun mediaPriority(kind: DetectedMediaKind): Int = when (kind) {
        DetectedMediaKind.VIDEO -> 0
        DetectedMediaKind.HLS -> 1
        DetectedMediaKind.DASH -> 2
        DetectedMediaKind.AUDIO -> 3
    }

    private fun preferKind(old: DetectedMediaKind, new: DetectedMediaKind): DetectedMediaKind {
        if (old == new) return old
        if (new == DetectedMediaKind.HLS || new == DetectedMediaKind.DASH) return new
        return old
    }
}
