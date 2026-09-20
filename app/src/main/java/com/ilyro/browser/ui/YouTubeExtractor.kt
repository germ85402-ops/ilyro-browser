package com.ilyro.browser.ui

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File

/**
 * Small app-facing adapter around the maintained Android yt-dlp wrapper.
 *
 * GeckoView can play YouTube while still hiding the signed googlevideo subrequests from a
 * WebExtension. In that case the page URL is still enough for yt-dlp to resolve a public video,
 * so the UI can keep one reliable fallback instead of exposing an empty media sheet.
 */
internal object YouTubeExtractor {
    private const val FORMAT_SELECTOR =
        "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"

    private val initLock = Any()

    @Volatile
    private var initialized = false

    @Volatile
    private var initializationError: Throwable? = null

    internal data class ResolvedInfo(
        val title: String?,
        val width: Int,
        val height: Int,
        val bitrate: Long,
        val fileSize: Long,
        val formatLabel: String?
    )

    fun resolve(context: Context, pageUrl: String): ResolvedInfo {
        ensureInitialized(context)
        val request = YoutubeDLRequest(pageUrl).apply {
            addOption("--no-playlist")
            addOption("-f", FORMAT_SELECTOR)
        }
        val info = YoutubeDL.getInfo(request)
        return info.toResolvedInfo()
    }

    fun download(
        context: Context,
        pageUrl: String,
        outputFile: File,
        processId: String,
        onProgress: (Float) -> Unit
    ) {
        ensureInitialized(context)
        val request = YoutubeDLRequest(pageUrl).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--merge-output-format", "mp4")
            addOption("-f", FORMAT_SELECTOR)
            addOption("-o", outputFile.absolutePath)
        }
        YoutubeDL.execute(request, processId) { progress, _, _ ->
            onProgress(progress.coerceIn(0f, 100f))
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.destroyProcessById(processId) }
    }

    fun errorMessage(error: Throwable): String {
        val detail = error.message
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter { it.isNotBlank() }
            ?.lastOrNull()
            ?.take(180)
        return detail ?: "YouTube extractor could not resolve this video"
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            initializationError?.let { throw it }
            try {
                YoutubeDL.init(context.applicationContext)
                FFmpeg.init(context.applicationContext)
                initialized = true
            } catch (error: Throwable) {
                initializationError = error
                throw error
            }
        }
    }

    private fun VideoInfo.toResolvedInfo(): ResolvedInfo {
        val requested = requestedFormats.orEmpty()
        val width = maxOf(width, requested.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(height, requested.maxOfOrNull { it.height } ?: 0)
        val bitrate = maxOf(
            requested.sumOf { it.tbr.toLong().coerceAtLeast(0L) },
            formats.orEmpty().maxOfOrNull { it.tbr.toLong().coerceAtLeast(0L) } ?: 0L
        )
        val fileSize = maxOf(
            fileSize,
            fileSizeApproximate,
            requested.sumOf { maxOf(it.fileSize, it.fileSizeApproximate) }
        )
        return ResolvedInfo(
            title = title?.takeIf { it.isNotBlank() } ?: fulltitle?.takeIf { it.isNotBlank() },
            width = width,
            height = height,
            bitrate = bitrate,
            fileSize = fileSize,
            formatLabel = format?.takeIf { it.isNotBlank() } ?: formatId?.takeIf { it.isNotBlank() }
        )
    }
}
