package com.ilyro.browser.ui

import org.mozilla.geckoview.WebExtension
import java.io.File

/** Persisted and in-memory models shared by [DownloadController] and the downloads UI. */
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
    val isPrivate: Boolean = false,
    /** The policy selected when this transfer was created. Older records default to allowed. */
    val allowMetered: Boolean = true,
    val isHls: Boolean = false
)


internal data class ExtensionDownloadInfo(
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

internal data class LiveTransfer(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long
)

internal data class SpeedSample(
    val downloadedBytes: Long,
    val timestampMs: Long,
    val speedBytesPerSecond: Long
)

internal data class PreparedHlsResource(
    val resource: HlsResource,
    val byteRange: Pair<Long, Long>?
)

internal data class BufferedHlsResource(
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
