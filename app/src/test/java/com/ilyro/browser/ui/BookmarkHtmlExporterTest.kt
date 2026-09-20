package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlExporterTest {
    @Test
    fun `exported bookmarks round trip through importer`() {
        val source = listOf(
            BookmarkItem(
                url = "https://example.com/path?a=1&b=2",
                title = "Example & <Test>",
                createdAt = 1_700_000_000_000L
            ),
            BookmarkItem(
                url = "https://mozilla.org/",
                title = "Mozilla \"Browser\"",
                createdAt = 1_700_000_100_000L
            )
        )

        val html = BookmarkHtmlExporter.encode(source)
        val parsed = BookmarkHtmlImporter.preview(html)

        assertTrue(parsed.canImport)
        assertEquals(source.map { it.url }, parsed.bookmarks.map { it.url })
        assertEquals(source.map { it.title }, parsed.bookmarks.map { it.title })
    }

    @Test
    fun `export escapes sensitive html characters`() {
        val html = BookmarkHtmlExporter.encode(
            listOf(BookmarkItem("https://example.com/?a=1&b=2", "A&B <C> \"D\""))
        )

        assertTrue(html.contains("https://example.com/?a=1&amp;b=2"))
        assertTrue(html.contains("A&amp;B &lt;C&gt; &quot;D&quot;"))
    }
}
