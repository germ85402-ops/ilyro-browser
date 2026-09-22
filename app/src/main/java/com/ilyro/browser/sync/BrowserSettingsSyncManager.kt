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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class SyncOutcome(
    val appliedRemoteSettings: Boolean = false,
    val appliedRemoteTabs: Boolean = false,
    val preservedLocalChanges: Boolean = false
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
        val tabSession = TabSessionStore.restore(browserPrefs, FALLBACK_HOME_URL)
        val snapshot = buildSnapshot(
            settings = settings,
            revision = revision,
            history = HistoryStore.restore(browserPrefs),
            bookmarks = BookmarkStore.restore(browserPrefs),
            quickLinks = QuickLinkStore.restore(browserPrefs),
            tabSession = tabSession
        )
        return upload(snapshot, provider, controlFingerprint(settings, tabSession))
    }

    /**
     * Background-safe synchronization. Collections are merged by stable identity so a second
     * device does not silently erase local bookmarks or history. Settings and open tabs use
     * last-write-wins only when this device has not changed them since its last successful sync.
     * If both devices changed those controls, local values are kept and uploaded instead of being
     * silently overwritten.
     */
    suspend fun sync(
        settings: BrowserSettings,
        provider: SyncProvider
    ): SyncResult<SyncOutcome> {
        val localHistory = HistoryStore.restore(browserPrefs)
        val localBookmarks = BookmarkStore.restore(browserPrefs)
        val localQuickLinks = QuickLinkStore.restore(browserPrefs)
        val localTabs = TabSessionStore.restore(browserPrefs, FALLBACK_HOME_URL)
        val localControlFingerprint = controlFingerprint(settings, localTabs)
        val localHasUnsyncedControlChanges = SyncConflictPolicy.hasLocalChanges(
            lastSyncedFingerprint = prefs.getString(KEY_LAST_SYNCED_CONTROL_HASH, null),
            currentFingerprint = localControlFingerprint
        )

        val remote = when (val result = provider.downloadLatest()) {
            is SyncResult.Success -> result.value
            is SyncResult.Failure -> return result
            SyncResult.NotAuthorized -> return SyncResult.NotAuthorized
        }

        val remoteData = remote?.let { snapshot ->
            try {
                SyncSnapshotPayloadDecoder.decode(snapshot)
            } catch (_: Exception) {
                return SyncResult.Failure(
                    message = "Remote ILYRO sync data is unsupported or incomplete. Local and cloud data were left unchanged.",
                    recoverable = false
                )
            }
        }
        val remoteIsNewer = remoteData != null && remote?.let { snapshot ->
            SyncOrderingPolicy.isRemoteNewer(
                snapshot = snapshot,
                lastRevision = prefs.getLong(KEY_LAST_REMOTE_REVISION, Long.MIN_VALUE),
                lastFingerprint = prefs.getString(KEY_LAST_REMOTE_FINGERPRINT, null)
            )
        } == true
        val applyRemoteControl = remoteIsNewer && !localHasUnsyncedControlChanges

        // A newer snapshot supplies settings and normal tabs only when this device has no
        // unuploaded local changes to those values. Custom wallpaper URIs stay local because
        // their files are device-specific.
        val effectiveSettings = if (applyRemoteControl) {
            remoteData!!.settings.copy(
                customWallpaperUri = settings.customWallpaperUri,
                darkCustomWallpaperUri = settings.darkCustomWallpaperUri
            )
        } else {
            settings
        }
        val effectiveTabs = if (applyRemoteControl) {
            remoteData!!.tabs?.let(::toRestoredTabSession) ?: localTabs
        } else {
            localTabs
        }
        val appliedRemoteTabs = applyRemoteControl && remoteData!!.tabs != null

        if (applyRemoteControl) {
            BrowserSettingsStore.save(browserPrefs, effectiveSettings)
            if (appliedRemoteTabs) {
                withContext(Dispatchers.IO) {
                    TabSessionStore.saveNow(
                        prefs = browserPrefs,
                        urls = effectiveTabs.urls,
                        states = effectiveTabs.states,
                        metadata = effectiveTabs.metadata,
                        activeIndex = effectiveTabs.activeIndex
                    )
                }
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

        return when (
            val uploadResult = upload(
                snapshot = snapshot,
                provider = provider,
                controlFingerprint = controlFingerprint(effectiveSettings, effectiveTabs)
            )
        ) {
            is SyncResult.Success -> SyncResult.Success(
                SyncOutcome(
                    appliedRemoteSettings = applyRemoteControl,
                    appliedRemoteTabs = appliedRemoteTabs,
                    preservedLocalChanges = remoteIsNewer && localHasUnsyncedControlChanges
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
                    val restored = SyncSnapshotPayloadDecoder.decode(snapshot)

                    // These ordered stores may wait for their writer futures. Restore runs from
                    // the UI flow, so wait off-main before reading the restored tabs or recreating
                    // the Activity; otherwise an older queued save can overwrite the remote state.
                    withContext(Dispatchers.IO) {
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
                    }

                    val localSettings = BrowserSettingsStore.restore(browserPrefs)
                    val restoredSettings = restored.settings.copy(
                        customWallpaperUri = localSettings.customWallpaperUri,
                        darkCustomWallpaperUri = localSettings.darkCustomWallpaperUri
                    )
                    val restoredTabs = TabSessionStore.restore(browserPrefs, FALLBACK_HOME_URL)

                    prefs.edit()
                        .putLong(KEY_REVISION, maxOf(prefs.getLong(KEY_REVISION, 0L), snapshot.revision))
                        .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                        .putLong(KEY_LAST_REMOTE_REVISION, snapshot.revision)
                        .putString(KEY_LAST_REMOTE_FINGERPRINT, SyncOrderingPolicy.fingerprint(snapshot))
                        .putString(
                            KEY_LAST_SYNCED_CONTROL_HASH,
                            controlFingerprint(restoredSettings, restoredTabs)
                        )
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
                // Wallpaper files live in app-private storage and cannot be resolved on another
                // device. Keep the preference itself local while syncing the rest of the settings.
                settings = settings.copy(
                    customWallpaperUri = null,
                    darkCustomWallpaperUri = null
                ),
                history = history,
                bookmarks = bookmarks,
                quickLinks = quickLinks,
                tabSession = tabSession
            )
        )
    }

    private suspend fun upload(
        snapshot: SyncSnapshot,
        provider: SyncProvider,
        controlFingerprint: String? = null
    ): SyncResult<Unit> {
        return when (val result = provider.upload(snapshot)) {
            is SyncResult.Success -> {
                val editor = prefs.edit()
                    .putLong(KEY_REVISION, snapshot.revision)
                    .putLong(KEY_LAST_SYNC_AT, snapshot.updatedAtEpochMs)
                    .putLong(KEY_LAST_REMOTE_REVISION, snapshot.revision)
                    .putString(KEY_LAST_REMOTE_FINGERPRINT, SyncOrderingPolicy.fingerprint(snapshot))
                if (controlFingerprint != null) {
                    editor.putString(KEY_LAST_SYNCED_CONTROL_HASH, controlFingerprint)
                }
                editor.apply()
                result
            }

            is SyncResult.Failure -> result
            SyncResult.NotAuthorized -> result
        }
    }

    private fun controlFingerprint(
        settings: BrowserSettings,
        tabSession: RestoredTabSession
    ): String = SyncPayloadFingerprint.of(
        BrowserDataSyncCodec.encode(
            settings = settings.copy(
                customWallpaperUri = null,
                darkCustomWallpaperUri = null
            ),
            history = emptyList(),
            bookmarks = emptyList(),
            quickLinks = emptyList(),
            tabSession = tabSession
        )
    )

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
        const val KEY_LAST_REMOTE_REVISION = "last_remote_revision"
        const val KEY_LAST_REMOTE_FINGERPRINT = "last_remote_fingerprint"
        const val KEY_LAST_SYNCED_CONTROL_HASH = "last_synced_control_hash_v1"
        const val MAX_HISTORY_ITEMS = 1000
        const val MAX_BOOKMARKS = 5000
        const val MAX_QUICK_LINKS = 12
    }
}
