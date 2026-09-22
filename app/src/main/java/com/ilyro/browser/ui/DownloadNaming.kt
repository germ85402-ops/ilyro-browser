package com.ilyro.browser.ui

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil

/**
 * Download state constants plus the pure naming, extension and MIME helpers used by
 * [DownloadController]. Keeping them out of the controller keeps the file reviewable and lets the
 * unit tests exercise the naming rules directly.
 */
internal const val DIRECT_NONE = 0
internal const val DIRECT_RUNNING = 1
internal const val DIRECT_SUCCESS = 2
internal const val DIRECT_FAILED = 3
internal const val DIRECT_PAUSED = 4
internal const val BODY_READ_TIMEOUT_MS = 300_000L
internal const val PREF_PENDING_APK_INSTALL_URI = "pending_apk_install_uri_v1"

internal class InvalidDownloadPayloadException(message: String) : Exception(message)

internal class MeteredNetworkBlockedException : java.io.IOException(
    "Download paused because downloads over metered networks are disabled"
)

internal fun shouldBlockMeteredDownload(allowMetered: Boolean, isMetered: Boolean): Boolean =
    !allowMetered && isMetered

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

internal fun mimeTypeForDirectDownload(url: String): String? {
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
internal val GENERIC_BINARY_MIME_TYPES = setOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/binary"
)

internal fun cleanDownloadName(name: String?): String? = name
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.trim()
    ?.takeIf { it.isNotBlank() }

internal fun downloadExtension(name: String?): String = cleanDownloadName(name)
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

internal fun directDownloadNameHint(url: String): String? {
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
