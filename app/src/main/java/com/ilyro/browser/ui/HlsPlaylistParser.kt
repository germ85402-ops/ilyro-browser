package com.ilyro.browser.ui

import java.net.URI

internal data class HlsByteRange(
    val length: Long,
    val offset: Long?
)

internal data class HlsResource(
    val url: String,
    val byteRange: HlsByteRange? = null,
    /** Whether the resource is covered by an unsupported EXT-X-KEY at its position. */
    val encrypted: Boolean = false
)

internal data class HlsMediaPlaylist(
    val initSegment: HlsResource?,
    val segments: List<HlsResource>,
    val isVod: Boolean,
    val hasUnsupportedEncryption: Boolean,
    val prefersMp4Container: Boolean
)

internal data class HlsMasterVariant(
    val url: String,
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val hasSeparateAudio: Boolean,
    val name: String? = null,
    val frameRate: Double = 0.0,
    val codecs: String? = null,
    val videoRange: String? = null
) {
    val pixelCount: Long
        get() = width.toLong() * height.toLong()
}

internal fun parseHlsMasterVariants(text: String, baseUrl: String): List<HlsMasterVariant> {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val variants = mutableListOf<HlsMasterVariant>()

    for (index in lines.indices) {
        val line = lines[index]
        if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) continue
        val attributes = parseHlsAttributes(line.substringAfter(':'))
        val uriLine = lines.subList(index + 1, lines.size).asSequence()
            .takeWhile { !it.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }
            .firstOrNull { !it.startsWith('#') } ?: continue
        val resolved = resolveHlsUrl(baseUrl, uriLine) ?: continue
        val bandwidth = attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull()
            ?: attributes["BANDWIDTH"]?.toLongOrNull()
            ?: 0L
        val dimensions = attributes["RESOLUTION"]
            ?.lowercase()
            ?.split('x')
            ?.takeIf { it.size == 2 }
        val width = dimensions?.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val explicitHeight = dimensions?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val name = attributes["NAME"]?.trim()?.takeIf { it.isNotBlank() }
        val height = explicitHeight.takeIf { it > 0 }
            ?: inferHlsQualityHeight(name)
            ?: inferHlsQualityHeight(uriLine)
            ?: 0

        variants += HlsMasterVariant(
            url = resolved,
            bandwidth = bandwidth,
            width = width,
            height = height,
            hasSeparateAudio = !attributes["AUDIO"].isNullOrBlank(),
            name = name,
            frameRate = attributes["FRAME-RATE"]?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            codecs = attributes["CODECS"]?.trim()?.takeIf { it.isNotBlank() },
            videoRange = attributes["VIDEO-RANGE"]?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    return variants
        .distinctBy { Triple(it.url, it.width, it.height) }
        .sortedWith(
            compareByDescending<HlsMasterVariant> { it.pixelCount }
                .thenByDescending { it.height }
                .thenByDescending { it.bandwidth }
        )
}

internal fun selectBestHlsVariant(text: String, baseUrl: String): HlsMasterVariant? {
    return parseHlsMasterVariants(text, baseUrl).firstOrNull()
}

private fun inferHlsQualityHeight(value: String?): Int? {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return null
    val explicit = Regex(
        "(?i)(?:^|[^0-9])(4320|2160|1440|1080|900|720|576|540|480|360|240|144)p(?:[^0-9]|$)"
    ).find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (explicit != null) return explicit

    return Regex(
        "(?i)(?:^|[/_.-])(4320|2160|1440|1080|900|720|576|540|480|360|240|144)(?:[/_.-]|$)"
    ).find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

internal fun parseHlsMediaPlaylist(text: String, baseUrl: String): HlsMediaPlaylist {
    require(text.contains("#EXTM3U")) { "Invalid HLS playlist" }

    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    var initSegment: HlsResource? = null
    var pendingRange: HlsByteRange? = null
    var encryptionActive = false
    var endList = false
    val segments = mutableListOf<HlsResource>()

    for (line in lines) {
        when {
            line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> endList = true
            line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                val attrs = parseHlsAttributes(line.substringAfter(':'))
                val method = attrs["METHOD"].orEmpty().uppercase()
                encryptionActive = method.isNotBlank() && method != "NONE"
            }
            line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                val attrs = parseHlsAttributes(line.substringAfter(':'))
                val uri = attrs["URI"]?.let { resolveHlsUrl(baseUrl, it) }
                if (uri != null) {
                    initSegment = HlsResource(
                        url = uri,
                        byteRange = attrs["BYTERANGE"]?.let(::parseHlsByteRange),
                        encrypted = encryptionActive
                    )
                }
            }
            line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> {
                pendingRange = parseHlsByteRange(line.substringAfter(':'))
            }
            !line.startsWith('#') -> {
                val resolved = resolveHlsUrl(baseUrl, line) ?: continue
                segments += HlsResource(resolved, pendingRange, encrypted = encryptionActive)
                pendingRange = null
            }
        }
    }

    require(segments.isNotEmpty()) { "HLS playlist contains no media segments" }
    val mp4 = initSegment != null || segments.any {
        val path = runCatching { URI(it.url).path.orEmpty().lowercase() }.getOrDefault("")
        path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".cmfv") || path.endsWith(".cmfa")
    }
    return HlsMediaPlaylist(
        initSegment = initSegment,
        segments = segments,
        isVod = endList,
        // Do not clear a playlist-wide flag when METHOD=NONE appears later. Earlier encrypted
        // resources remain unsafe to concatenate without a decrypt/mux pipeline.
        hasUnsupportedEncryption = initSegment?.encrypted == true || segments.any { it.encrypted },
        prefersMp4Container = mp4
    )
}

private fun parseHlsByteRange(value: String): HlsByteRange? {
    val clean = value.trim().trim('"')
    val pieces = clean.split('@', limit = 2)
    val length = pieces.firstOrNull()?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val offset = pieces.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
    return HlsByteRange(length, offset)
}

private fun resolveHlsUrl(baseUrl: String, child: String): String? {
    return runCatching { URI(baseUrl).resolve(child.trim().trim('"')).toString() }
        .getOrNull()
        ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}

private fun parseHlsAttributes(value: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    while (index < value.length) {
        while (index < value.length && (value[index] == ',' || value[index].isWhitespace())) index++
        val keyStart = index
        while (index < value.length && value[index] != '=') index++
        if (index >= value.length) break
        val key = value.substring(keyStart, index).trim().uppercase()
        index++

        val parsedValue = if (index < value.length && value[index] == '"') {
            index++
            val start = index
            while (index < value.length && value[index] != '"') index++
            value.substring(start, index).also { if (index < value.length) index++ }
        } else {
            val start = index
            while (index < value.length && value[index] != ',') index++
            value.substring(start, index).trim()
        }
        if (key.isNotBlank()) result[key] = parsedValue
        while (index < value.length && value[index] != ',') index++
        if (index < value.length && value[index] == ',') index++
    }
    return result
}
