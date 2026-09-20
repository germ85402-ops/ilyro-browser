package com.ilyro.browser.sync

import com.ilyro.browser.ui.BookmarkItem
import com.ilyro.browser.ui.BrowserSettings
import com.ilyro.browser.ui.HistoryItem
import com.ilyro.browser.ui.QuickLink
import com.ilyro.browser.ui.RestoredTabSession
import com.ilyro.browser.ui.TabSessionMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDataSyncCodecTest {
    @Test
    fun roundTrip_preservesNonSensitiveBrowserData() {
        val settings = BrowserSettings(desktopMode = true, httpsOnly = true)
        val history = listOf(
            HistoryItem("https://example.com/a", "A", 300L),
            HistoryItem("https://example.com/b", "B", 200L)
        )
        val bookmarks = listOf(
            BookmarkItem("https://example.com/bookmark", "Bookmark", 100L)
        )
        val quickLinks = listOf(
            QuickLink("custom", "Custom", "https://example.com/")
        )
        val tabs = RestoredTabSession(
            urls = listOf("https://example.com/one", "about:blank"),
            states = listOf("large-gecko-state-must-not-sync", "another-state"),
            metadata = listOf(
                TabSessionMetadata(pinned = true, group = "Work"),
                TabSessionMetadata()
            ),
            activeIndex = 1
        )

        val restored = BrowserDataSyncCodec.decode(
            BrowserDataSyncCodec.encode(settings, history, bookmarks, quickLinks, tabs)
        )

        assertEquals(settings, restored.settings)
        assertEquals(history, restored.history)
        assertEquals(bookmarks, restored.bookmarks)
        assertEquals(quickLinks, restored.quickLinks)
        assertEquals(2, restored.tabs?.tabs?.size)
        assertEquals(1, restored.tabs?.activeIndex)
        assertEquals("https://example.com/one", restored.tabs?.tabs?.first()?.url)
        assertTrue(restored.tabs?.tabs?.first()?.pinned == true)
        assertEquals("Work", restored.tabs?.tabs?.first()?.group)
        assertFalse(BrowserDataSyncCodec.encode(settings, history, bookmarks, quickLinks, tabs)
            .contains("large-gecko-state-must-not-sync"))
    }

    @Test
    fun decode_invalidOptionalRecords_skipsThemSafely() {
        val payload = """{
            "settings": ${BrowserSettingsSyncCodec.encode(BrowserSettings())},
            "history": [{"url":"","title":"bad","visitedAt":1}],
            "bookmarks": [{"url":"","title":"bad","createdAt":1}],
            "quickLinks": [{"id":"","label":"bad","url":"https://example.com"}],
            "tabs": {"items": [], "activeIndex": 99}
        }""".trimIndent()

        val restored = BrowserDataSyncCodec.decode(payload)

        assertTrue(restored.history.orEmpty().isEmpty())
        assertTrue(restored.bookmarks.orEmpty().isEmpty())
        assertTrue(restored.quickLinks.orEmpty().isEmpty())
        assertNull(restored.tabs)
    }
}
