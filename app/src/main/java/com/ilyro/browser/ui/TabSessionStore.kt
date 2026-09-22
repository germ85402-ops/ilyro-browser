package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class TabSessionMetadata(
    val pinned: Boolean = false,
    val group: String? = null
)

internal data class RestoredTabSession(
    val urls: List<String>,
    val states: List<String?>,
    val metadata: List<TabSessionMetadata>,
    val activeIndex: Int
)

internal object TabSessionStore {
    private const val KEY_TABS_V1 = "tabs_v1"
    private const val KEY_TABS_V2 = "tabs_v2"
    private const val KEY_ACTIVE_INDEX = "active_tab_index_v1"
    private const val MAX_SESSION_STATE_CHARS = 256_000
    private const val MAX_GROUP_NAME_CHARS = 48

    private data class DecodedTabs(
        val urls: List<String>,
        val states: List<String?>,
        val metadata: List<TabSessionMetadata>
    )

    private data class PendingSave(
        val prefs: SharedPreferences,
        val urls: List<String>,
        val states: List<String?>,
        val metadata: List<TabSessionMetadata>,
        val activeIndex: Int
    )

    private val pendingSave = AtomicReference<PendingSave?>(null)
    private val writerActive = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ILYRO-tab-state-writer").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    fun restore(prefs: SharedPreferences, fallbackUrl: String): RestoredTabSession {
        val restored = decodeTabRecords(prefs.getString(KEY_TABS_V2, null))
            .takeIf { it.urls.isNotEmpty() }
            ?: decodeTabRecords(prefs.getString(KEY_TABS_V1, null))
        val safeUrls = restored.urls.ifEmpty { listOf(fallbackUrl) }
        val states = if (restored.urls.isEmpty()) listOf(null) else restored.states.padTo(safeUrls.size)
        val metadata = if (restored.urls.isEmpty()) listOf(TabSessionMetadata()) else restored.metadata.padMetadataTo(safeUrls.size)
        return RestoredTabSession(
            urls = safeUrls,
            states = states,
            metadata = metadata,
            activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0).coerceIn(0, safeUrls.lastIndex)
        )
    }

    /**
     * Queues a coalesced snapshot of the normal (non-private) tabs.
     *
     * ILYRO always keeps at least one tab open: closing the last tab immediately replaces it with
     * a new home tab, so an empty list means "no snapshot is available yet" (for example during
     * startup) rather than "the user closed everything". Such a call is ignored on purpose so a
     * transient empty state cannot wipe a restorable session. Callers that genuinely want to drop
     * the stored session must pass the tab that replaced it.
     */
    fun save(
        prefs: SharedPreferences,
        urls: List<String>,
        states: List<String?>,
        activeIndex: Int,
        metadata: List<TabSessionMetadata> = emptyList()
    ) {
        val safeUrls = urls.toList().ifEmpty { return }
        pendingSave.set(PendingSave(
            prefs = prefs,
            urls = safeUrls,
            states = states.padTo(safeUrls.size),
            metadata = metadata.padMetadataTo(safeUrls.size),
            activeIndex = activeIndex.coerceIn(0, safeUrls.lastIndex)
        ))
        scheduleWriter()
    }

    /**
     * Replaces the persisted normal-tab snapshot and waits for the single writer queue to flush.
     * Drive restore uses this before Activity recreation so an older queued tab save cannot race
     * and overwrite the restored URLs/groups/pins.
     *
     * As in [save], an empty list is ignored because ILYRO never holds zero tabs.
     */
    fun saveNow(
        prefs: SharedPreferences,
        urls: List<String>,
        states: List<String?>,
        activeIndex: Int,
        metadata: List<TabSessionMetadata> = emptyList()
    ) {
        val safeUrls = urls.toList().ifEmpty { return }
        val snapshot = PendingSave(
            prefs = prefs,
            urls = safeUrls,
            states = states.padTo(safeUrls.size),
            metadata = metadata.padMetadataTo(safeUrls.size),
            activeIndex = activeIndex.coerceIn(0, safeUrls.lastIndex)
        )

        awaitStoreWrite(
            writer.submit {
                pendingSave.set(null)
                persist(snapshot)
            }
        )
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
        val legacy = JSONArray()
        snapshot.urls.forEach { legacy.put(JSONObject().put("url", it)) }
        snapshot.prefs.edit()
            .putString(KEY_TABS_V2, encodeTabs(snapshot.urls, snapshot.states, snapshot.metadata))
            .putString(KEY_TABS_V1, legacy.toString())
            .putInt(KEY_ACTIVE_INDEX, snapshot.activeIndex)
            .apply()
    }

    internal fun encodeTabs(
        urls: List<String>,
        states: List<String?>,
        metadata: List<TabSessionMetadata> = emptyList()
    ): String {
        val safeStates = states.padTo(urls.size)
        val safeMetadata = metadata.padMetadataTo(urls.size)
        val array = JSONArray()
        urls.forEachIndexed { index, url ->
            val item = JSONObject().put("url", url)
            safeStates[index]?.takeIf { it.length <= MAX_SESSION_STATE_CHARS }?.let { item.put("sessionState", it) }
            val tabMetadata = safeMetadata[index]
            if (tabMetadata.pinned) item.put("pinned", true)
            tabMetadata.group.normalizedGroupName()?.let { item.put("group", it) }
            array.put(item)
        }
        return array.toString()
    }

    internal fun decodeTabs(raw: String?): Pair<List<String>, List<String?>> {
        val decoded = decodeTabRecords(raw)
        return decoded.urls to decoded.states
    }

    private fun decodeTabRecords(raw: String?): DecodedTabs {
        if (raw.isNullOrBlank()) return DecodedTabs(emptyList(), emptyList(), emptyList())
        return runCatching {
            val array = JSONArray(raw)
            val urls = mutableListOf<String>()
            val states = mutableListOf<String?>()
            val metadata = mutableListOf<TabSessionMetadata>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isEmpty()) continue
                urls += url
                states += item.optString("sessionState").takeIf { it.isNotBlank() && it.length <= MAX_SESSION_STATE_CHARS }
                metadata += TabSessionMetadata(
                    pinned = item.optBoolean("pinned", false),
                    group = item.optString("group").normalizedGroupName()
                )
            }
            DecodedTabs(urls, states, metadata)
        }.getOrDefault(DecodedTabs(emptyList(), emptyList(), emptyList()))
    }

    private fun String?.normalizedGroupName(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_GROUP_NAME_CHARS)

    private fun List<String?>.padTo(size: Int): List<String?> = List(size) { getOrNull(it) }
    private fun List<TabSessionMetadata>.padMetadataTo(size: Int): List<TabSessionMetadata> =
        List(size) { getOrNull(it) ?: TabSessionMetadata() }
}
