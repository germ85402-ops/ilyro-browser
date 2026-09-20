package com.ilyro.browser.sync

import com.ilyro.browser.ui.BookmarkItem
import com.ilyro.browser.ui.BrowserSettings
import com.ilyro.browser.ui.HistoryItem
import com.ilyro.browser.ui.QuickLink
import com.ilyro.browser.ui.RestoredTabSession
import org.json.JSONArray
import org.json.JSONObject

internal data class SyncedTab(
    val url: String,
    val pinned: Boolean,
    val group: String?
)

internal data class SyncedTabSession(
    val tabs: List<SyncedTab>,
    val activeIndex: Int
)

/**
 * Full non-sensitive ILYRO snapshot restored from Drive.
 * Nullable collections keep schema-v1 (settings-only) backups backward compatible.
 */
internal data class RestoredBrowserData(
    val settings: BrowserSettings,
    val history: List<HistoryItem>? = null,
    val bookmarks: List<BookmarkItem>? = null,
    val quickLinks: List<QuickLink>? = null,
    val tabs: SyncedTabSession? = null
)

/**
 * Version 2 sync payload: settings plus user-created browser data.
 * Passwords, cookies, site sessions, private tabs, downloads and Gecko session state are excluded.
 */
internal object BrowserDataSyncCodec {
    const val SCHEMA_VERSION = 2

    private const val MAX_HISTORY_ITEMS = 1000
    private const val MAX_BOOKMARKS = 5000
    private const val MAX_QUICK_LINKS = 12
    private const val MAX_TABS = 100
    private const val MAX_GROUP_NAME_CHARS = 48

    fun encode(
        settings: BrowserSettings,
        history: List<HistoryItem>,
        bookmarks: List<BookmarkItem>,
        quickLinks: List<QuickLink>,
        tabSession: RestoredTabSession
    ): String {
        val root = JSONObject()
            .put("settings", JSONObject(BrowserSettingsSyncCodec.encode(settings)))

        val historyArray = JSONArray()
        history.take(MAX_HISTORY_ITEMS).forEach { item ->
            historyArray.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("visitedAt", item.visitedAt)
            )
        }
        root.put("history", historyArray)

        val bookmarkArray = JSONArray()
        bookmarks.take(MAX_BOOKMARKS).forEach { item ->
            bookmarkArray.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("createdAt", item.createdAt)
                    .put("folder", item.folder)
            )
        }
        root.put("bookmarks", bookmarkArray)

        val quickLinkArray = JSONArray()
        quickLinks.take(MAX_QUICK_LINKS).forEach { item ->
            quickLinkArray.put(
                JSONObject()
                    .put("id", item.id)
                    .put("label", item.label)
                    .put("url", item.url)
            )
        }
        root.put("quickLinks", quickLinkArray)

        val tabArray = JSONArray()
        tabSession.urls.take(MAX_TABS).forEachIndexed { index, url ->
            if (url.isBlank()) return@forEachIndexed
            val metadata = tabSession.metadata.getOrNull(index)
            val item = JSONObject().put("url", url)
            if (metadata?.pinned == true) item.put("pinned", true)
            metadata?.group
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_GROUP_NAME_CHARS)
                ?.let { item.put("group", it) }
            tabArray.put(item)
        }
        root.put(
            "tabs",
            JSONObject()
                .put("items", tabArray)
                .put("activeIndex", tabSession.activeIndex.coerceAtLeast(0))
        )

        return root.toString()
    }

    fun decode(payload: String): RestoredBrowserData {
        val root = JSONObject(payload)
        val settingsJson = root.getJSONObject("settings")
        val settings = BrowserSettingsSyncCodec.decode(settingsJson.toString())

        val history = buildList {
            val array = root.optJSONArray("history") ?: JSONArray()
            for (index in 0 until minOf(array.length(), MAX_HISTORY_ITEMS)) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                val title = item.optString("title").trim().ifBlank { url }
                val visitedAt = item.optLong("visitedAt", 0L).takeIf { it > 0L } ?: continue
                add(HistoryItem(url = url, title = title, visitedAt = visitedAt))
            }
        }.sortedByDescending { it.visitedAt }

        val bookmarks = buildList {
            val array = root.optJSONArray("bookmarks") ?: JSONArray()
            for (index in 0 until minOf(array.length(), MAX_BOOKMARKS)) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                val title = item.optString("title").trim().ifBlank { url }
                val createdAt = item.optLong("createdAt", 0L).takeIf { it > 0L } ?: continue
                val folder = item.optString("folder")
                    .trim()
                    .takeIf { it.isNotBlank() && it != "null" }
                add(
                    BookmarkItem(
                        url = url,
                        title = title,
                        createdAt = createdAt,
                        folder = folder
                    )
                )
            }
        }.distinctBy { it.url }

        val quickLinks = buildList {
            val array = root.optJSONArray("quickLinks") ?: JSONArray()
            for (index in 0 until minOf(array.length(), MAX_QUICK_LINKS)) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val label = item.optString("label").trim()
                val url = item.optString("url").trim()
                if (id.isBlank() || label.isBlank() || url.isBlank()) continue
                add(QuickLink(id = id, label = label, url = url))
            }
        }

        val tabObject = root.optJSONObject("tabs")
        val tabItems = tabObject?.optJSONArray("items")
        val tabs = if (tabItems == null) {
            null
        } else {
            val decoded = buildList {
                for (index in 0 until minOf(tabItems.length(), MAX_TABS)) {
                    val item = tabItems.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    if (url.isBlank()) continue
                    add(
                        SyncedTab(
                            url = url,
                            pinned = item.optBoolean("pinned", false),
                            group = item.optString("group")
                                .trim()
                                .takeIf { it.isNotBlank() }
                                ?.take(MAX_GROUP_NAME_CHARS)
                        )
                    )
                }
            }
            if (decoded.isEmpty()) {
                null
            } else {
                SyncedTabSession(
                    tabs = decoded,
                    activeIndex = tabObject.optInt("activeIndex", 0)
                        .coerceIn(0, decoded.lastIndex)
                )
            }
        }

        return RestoredBrowserData(
            settings = settings,
            history = history,
            bookmarks = bookmarks,
            quickLinks = quickLinks,
            tabs = tabs
        )
    }
}
