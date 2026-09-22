package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class HistoryItem(
    val url: String,
    val title: String,
    val visitedAt: Long
)

internal object HistoryStore {
    private const val KEY_HISTORY = "history_v1"
    private const val MAX_ITEMS = 1000

    private data class PendingSave(
        val prefs: SharedPreferences,
        val history: List<HistoryItem>
    )

    private val pendingSave = AtomicReference<PendingSave?>(null)
    private val writerActive = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ILYRO-history-writer").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    fun restore(prefs: SharedPreferences): List<HistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    if (url.isBlank()) continue
                    val title = item.optString("title").trim().ifBlank { url }
                    val visitedAt = item.optLong("visitedAt", 0L)
                        .takeIf { it > 0L } ?: System.currentTimeMillis()
                    add(HistoryItem(url = url, title = title, visitedAt = visitedAt))
                }
            }.sortedByDescending { it.visitedAt }.take(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }

    /**
     * Ordered save for explicit user actions (remove/clear/restore-sensitive flows). JSON encoding
     * runs on the dedicated writer, and the caller waits so an older queued auto-save cannot
     * overwrite a user-initiated change.
     */
    fun save(prefs: SharedPreferences, history: List<HistoryItem>) {
        val snapshot = PendingSave(prefs, history.take(MAX_ITEMS).toList())
        awaitStoreWrite(
            writer.submit {
                pendingSave.set(null)
                persist(snapshot)
            }
        )
    }

    /**
     * Coalesced save for automatic visits. Only the newest pending snapshot is serialized, which
     * avoids repeatedly encoding up to 1000 entries during rapid navigation.
     */
    fun saveAsync(prefs: SharedPreferences, history: List<HistoryItem>) {
        pendingSave.set(PendingSave(prefs, history.take(MAX_ITEMS).toList()))
        scheduleWriter()
    }

    private fun scheduleWriter() {
        if (writerActive.compareAndSet(false, true)) writer.execute(::drainPendingWrites)
    }

    private fun drainPendingWrites() {
        while (true) {
            val next = pendingSave.getAndSet(null)
            if (next != null) {
                persist(next)
                continue
            }
            writerActive.set(false)
            if (pendingSave.get() != null && writerActive.compareAndSet(false, true)) continue
            return
        }
    }

    private fun persist(snapshot: PendingSave) {
        val array = JSONArray()
        snapshot.history.forEach { entry ->
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("title", entry.title)
                    .put("visitedAt", entry.visitedAt)
            )
        }
        snapshot.prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
