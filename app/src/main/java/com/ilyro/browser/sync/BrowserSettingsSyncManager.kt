package com.ilyro.browser.sync

import android.content.Context
import com.ilyro.browser.ui.BookmarkStore
import com.ilyro.browser.ui.BrowserSettings
import com.ilyro.browser.ui.BrowserSettingsStore
import com.ilyro.browser.ui.HistoryItem
import com.ilyro.browser.ui.HistoryStore
import com.ilyro.browser.ui.QuickLink
import com.ilyro.browser.ui.QuickLinkStore
import com.ilyro.browser.ui.RestoredTabSession
import com.ilyro.browser.ui.TabSessionMetadata
import com.ilyro.browser.ui.TabSessionStore
import java.util.UUID

internal data class SyncOutcome(
    val appliedRemoteSettings: Boolean = false,
    val appliedRemoteTabs: Boolean = false
)

/**
 * Coordinates non-sensitive browser data with a provider without exposing provider details to UI.
 * Credentials, cookies, site sessions, private tabs, downloads and Gecko session state stay local.
 */
internal class BrowserSettingsSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val browserPrefs = appContext.getSharedPreferences(BROWSER_PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun backup(
        settings: BrowserSettings,
        provider: SyncProvider
    ): SyncResult<Unit> {
        val revision = prefs.getLong(KEY_REVISION, 0L) + 1L
        val snapshot = buildSnapshot(
            settings = settings,
            revision = revision,
            history = HistoryStore.restore(browserPrefs),
            bookmarks = BookmarkStore.restore(browserPrefs),
            quickLinks = QuickLinkStore.restore(browserPrefs),
            tabSession = TabSessionStore.restore(browserPrefs, FALLBACK_HOME_URL)
        )
        return upload(snapshot, provider)
    }

    /**
     * Background-safe synchronization. Collections are merged by stable identity so a second
     * device does not silently erase local bookmarks or history. Local settings and open tabs
     * remain authoritative because changing them remotely while the browser is open is surprising.
     */
    suspend fun sync(
        settings: BrowserSettings,
        provider: SyncProvider
    ): SyncResult<SyncOutcome> {
        val localHistory = HistoryStore.restore(browserPrefs)
        val localBookmarks = BookmarkStore.restore(browserPrefs)
        val localQuickLinks = QuickLinkStore.restore(browserPrefs)
        val localTabs = TabSessionStore.restore(browserPrefs, FALLBACK_HOME_URL)
        val lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

        val remote = when (val result = provider.downloadLatest()) {
            is SyncResult.Success -> result.value
            is SyncResult.Failure -> return result
            SyncResult.NotAuthorized -> return SyncResult.NotAuthorized
        }

        val remoteData = remote
            ?.takeIf { it.schemaVersion == BrowserDataSyncCodec.SCHEMA_VERSION }
            ?.let { runCatching { BrowserDataSyncCodec.decode(it.payload) }.getOrNull() }
        val remoteUpdatedAt = remote?.updatedAtEpochMs ?: 0L
        val remoteIsNewer = remoteData != null && remoteUpdatedAt > lastSyncAt

        // Last-write-wins: a newer snapshot from another device supplies settings and normal
        // tabs. Custom wallpaper URIs stay local because their files are device-specific.
        val effectiveSettings = if (remoteIsNewer) {
            remoteData!!.settings.copy(
                customWallpaperUri = settings.customWallpaperUri,
                darkCustomWallpaperUri = settings.darkCustomWallpaperUri
            )
        } else {
            settings
        }
        val effectiveTabs = if (remoteIsNewer) {
            remoteData!!.tabs?.let(::toRestoredTabSession) ?: localTabs
        } else {
            localTabs
        }
        val appliedRemoteTabs = remoteIsNewer && remoteData!!.tabs != null

        if (remoteIsNewer) {
            BrowserSettingsStore.save(browserPrefs, effectiveSettings)
            if (appliedRemoteTabs) {
                TabSessionStore.saveNow(
                    prefs = browserPrefs,
                    urls = effectiveTabs.urls,
                    states = effectiveTabs.states,
                    metadata = effectiveTabs.metadata,
                    activeIndex = effectiveTabs.activeIndex
                )
            }
        }

        val mergedHistory = mergeHistory(localHistory, remoteData?.history.orEmpty())
        val mergedBookmarks = mergeBookmarks(localBookmarks, remoteData?.bookmarks.orEmpty())
        val mergedQuickLinks = mergeQuickLinks(localQuickLinks, remoteData?.quickLinks.orEmpty())
        val revision = maxOf(
            prefs.getLong(KEY_REVISION, 0L),
            remote?.revision ?: 0L
        ) + 1L
        val snapshot = buildSnapshot(
            settings = effectiveSettings,
            revision = revision,
            history = mergedHistory,
            bookmarks = mergedBookmarks,
            quickLinks = mergedQuickLinks,
            tabSession = effectiveTabs
        )

        return when (val uploadResult = upload(snapshot, provider)) {
            is SyncResult.Success -> SyncResult.Success(
                SyncOutcome(
                    appliedRemoteSettings = remoteIsNewer,
                    appliedRemoteTabs = appliedRemoteTabs
                )
            )

            is SyncResult.Failure -> uploadResult
            SyncResult.NotAuthorized -> SyncResult.NotAuthorized
        }
    }

    suspend fun restore(provider: SyncProvider): SyncResult<BrowserSettings?> {
        when (val result = provider.downloadLatest()) {
            is SyncResult.Failure -> {
                return SyncResult.Failure(result.message, result.recoverable)
            }

            SyncResult.NotAuthorized -> {
                return SyncResult.NotAuthorized
            }

            is SyncResult.Success -> {
                val snapshot = result.value
                    ?: return SyncResult.Success<BrowserSettings?>(null)

                return try {
                    val restored = when (snapshot.schemaVersion) {
                        BrowserSettingsSyncCodec.SCHEMA_VERSION -> RestoredBrowserData(
                            settings = BrowserSettingsSyncCodec.decode(snapshot.payload)
                        )

                        BrowserDataSyncCodec.SCHEMA_VERSION -> BrowserDataSyncCodec.decode(snapshot.payload)

                        else -> return SyncResult.Failure(
                            message = "Unsupported ILYRO sync schema " + snapshot.schemaVersion,
                            recoverable = false
                        )
                    }

                    restored.history?.let { HistoryStore.save(browserPrefs, it) }
                    restored.bookmarks?.let { BookmarkStore.save(browserPrefs, it) }
                    restored.quickLinks?.let { QuickLinkStore.save(browserPrefs, it) }
                    restored.tabs?.let { synced ->
                        TabSessionStore.saveNow(
                            prefs = browserPrefs,
                            urls = synced.tabs.map { it.url },
                            states = List(synced.tabs.size) { null },
                            metadata = synced.tabs.map { tab ->
                                TabSessionMetadata(pinned = tab.pinned, group = tab.group)
                            },
                            activeIndex = synced.activeIndex
                        )
                    }

                    val localSettings = BrowserSettingsStore.restore(browserPrefs)
                    val restoredSettings = restored.settings.copy(
                        customWallpaperUri = localSettings.customWallpaperUri,
                        darkCustomWallpaperUri = localSettings.darkCustomWallpaperUri
                    )

                    prefs.edit()
                        .putLong(KEY_REVISION, maxOf(prefs.getLong(KEY_REVISION, 0L), snapshot.revision))
                        .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                        .apply()
                    SyncResult.Success<BrowserSettings?>(restoredSettings)
                } catch (error: Exception) {
                    SyncResult.Failure(error.message, recoverable = false)
                }
            }
        }
    }

    fun lastSyncAtEpochMs(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    private fun toRestoredTabSession(session: SyncedTabSession): RestoredTabSession {
        val urls = session.tabs.map { it.url }
        return RestoredTabSession(
            urls = urls,
            states = List(urls.size) { null },
            metadata = session.tabs.map { tab ->
                TabSessionMetadata(pinned = tab.pinned, group = tab.group)
            },
            activeIndex = session.activeIndex.coerceIn(0, urls.lastIndex)
        )
    }

    private fun buildSnapshot(
        settings: BrowserSettings,
        revision: Long,
        history: List<com.ilyro.browser.ui.HistoryItem>,
        bookmarks: List<com.ilyro.browser.ui.BookmarkItem>,
        quickLinks: List<com.ilyro.browser.ui.QuickLink>,
        tabSession: RestoredTabSession
    ): SyncSnapshot {
        val now = System.currentTimeMillis()
        return SyncSnapshot(
            schemaVersion = BrowserDataSyncCodec.SCHEMA_VERSION,
            revision = revision,
            updatedAtEpochMs = now,
            deviceId = deviceId(),
            payload = BrowserDataSyncCodec.encode(
                settings = settings,
                history = history,
                bookmarks = bookmarks,
                quickLinks = quickLinks,
                tabSession = tabSession
            )
        )
    }

    private suspend fun upload(snapshot: SyncSnapshot, provider: SyncProvider): SyncResult<Unit> {
        return when (val result = provider.upload(snapshot)) {
            is SyncResult.Success -> {
                prefs.edit()
                    .putLong(KEY_REVISION, snapshot.revision)
                    .putLong(KEY_LAST_SYNC_AT, snapshot.updatedAtEpochMs)
                    .apply()
                result
            }

            is SyncResult.Failure -> result
            SyncResult.NotAuthorized -> result
        }
    }

    private fun mergeHistory(local: List<HistoryItem>, remote: List<HistoryItem>): List<HistoryItem> =
        (local + remote)
            .associateBy { it.url + "\u0000" + it.visitedAt }
            .values
            .sortedByDescending { it.visitedAt }
            .take(MAX_HISTORY_ITEMS)

    private fun mergeBookmarks(
        local: List<com.ilyro.browser.ui.BookmarkItem>,
        remote: List<com.ilyro.browser.ui.BookmarkItem>
    ): List<com.ilyro.browser.ui.BookmarkItem> {
        val merged = LinkedHashMap<String, com.ilyro.browser.ui.BookmarkItem>()
        (remote + local).forEach { item ->
            val previous = merged[item.url]
            if (previous == null || item.createdAt >= previous.createdAt) {
                merged[item.url] = item
            }
        }
        return merged.values.sortedByDescending { it.createdAt }.take(MAX_BOOKMARKS)
    }

    private fun mergeQuickLinks(
        local: List<QuickLink>,
        remote: List<QuickLink>
    ): List<QuickLink> =
        (local + remote)
            .distinctBy { it.url }
            .take(MAX_QUICK_LINKS)

    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private companion object {
        const val PREFS_NAME = "ilyro_sync_state"
        const val BROWSER_PREFS_NAME = "ilyro_browser"
        const val FALLBACK_HOME_URL = "about:blank"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_REVISION = "revision"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val MAX_HISTORY_ITEMS = 1000
        const val MAX_BOOKMARKS = 5000
        const val MAX_QUICK_LINKS = 12
    }
}
