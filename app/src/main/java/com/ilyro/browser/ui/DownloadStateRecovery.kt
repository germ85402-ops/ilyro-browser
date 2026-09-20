package com.ilyro.browser.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repairs direct-download state after Android has killed the browser process.
 *
 * Direct MediaStore transfers are owned by in-process workers. A normal direct HTTP transfer can
 * continue from its existing MediaStore destination, so a stale RUNNING record is recovered as
 * PAUSED and the user can resume it. HLS is different: its segmented worker owns temporary pieces
 * and cannot safely continue after process death, so an interrupted HLS record and its incomplete
 * destination are discarded instead of exposing a permanently unresumable Paused item.
 *
 * The guard is process-local on purpose: Activity recreation must not pause or discard a worker
 * that is still alive in the same process.
 */
internal object DownloadStateRecovery {
    private const val KEY_DOWNLOADS = "downloads_v1"
    private const val DIRECT_RUNNING = 1
    internal const val DIRECT_PAUSED = 4

    internal data class Result(
        val json: String,
        val discardedLocalUris: List<String>
    )

    private val appliedForProcess = AtomicBoolean(false)

    fun recoverInterruptedDirectDownloadsOnce(context: Context, prefs: SharedPreferences): Boolean {
        if (!appliedForProcess.compareAndSet(false, true)) return false
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return false
        val recovered = recoverJson(raw) ?: return false

        recovered.discardedLocalUris.forEach { rawUri ->
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return@forEach
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        prefs.edit().putString(KEY_DOWNLOADS, recovered.json).apply()
        return true
    }

    /** Returns a repaired payload, or null when the payload is invalid or needs no changes. */
    internal fun recoverJson(raw: String?): Result? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            val discardedUris = mutableListOf<String>()
            var changed = false

            // Iterate backwards because interrupted HLS records are removed in-place.
            for (index in array.length() - 1 downTo 0) {
                val item = array.optJSONObject(index) ?: continue
                val localUri = item.optString("localUri")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: continue
                if (item.optInt("directState", 0) != DIRECT_RUNNING) continue

                val sourceUrl = item.optString("sourceUrl")
                if (isHlsPlaylistUrl(sourceUrl)) {
                    discardedUris += localUri
                    array.remove(index)
                } else {
                    item.put("directState", DIRECT_PAUSED)
                }
                changed = true
            }

            if (changed) Result(array.toString(), discardedUris) else null
        }.getOrNull()
    }

    private fun isHlsPlaylistUrl(url: String): Boolean {
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?').lowercase()
        return withoutQuery.endsWith(".m3u8") || withoutQuery.contains(".m3u8/")
    }
}
