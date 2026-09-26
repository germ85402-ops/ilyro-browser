package com.ilyro.browser.ui

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Request and snapshot models used by the browser screen. */
internal data class PendingNewTabRequest(
    val sourceTabId: String,
    val uri: String,
    val result: GeckoResult<GeckoSession>
)

internal data class LinkContextMenuRequest(
    val sourceTabId: String,
    val url: String,
    val title: String?
)

internal data class RestorableTabSnapshot(
    val ids: List<String>,
    val urls: List<String>,
    val states: List<String?>,
    val metadata: List<TabSessionMetadata>
)

/** Loaded before the interactive browser is created, so autosave cannot overwrite unread data. */
internal data class BrowserStartupData(
    val session: RestoredTabSession,
    val bookmarks: List<BookmarkItem>,
    val history: List<HistoryItem>,
    val quickLinks: List<QuickLink>
)
