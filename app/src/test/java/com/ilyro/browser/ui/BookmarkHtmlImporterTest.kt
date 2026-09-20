package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlImporterTest {
    @Test
    fun parsesStandardNetscapeBookmarkHtmlAndDeduplicates() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
              <DT><A HREF="https://example.com/path?x=1&amp;y=2">Example &amp; Docs</A>
              <DT><A HREF='https://other.example/'>Other</A>
              <DT><A HREF="https://example.com/path?x=1&amp;y=2">Example duplicate</A>
            </DL><p>
        """.trimIndent()

        val preview = BookmarkHtmlImporter.preview(html)

        assertNull(preview.error)
        assertTrue(preview.canImport)
        assertEquals(3, preview.sourceRows)
        assertEquals(0, preview.skippedRows)
        assertEquals(1, preview.duplicateRows)
        assertEquals(2, preview.bookmarks.size)
        assertEquals("https://example.com/path?x=1&y=2", preview.bookmarks[0].url)
        assertEquals("Example duplicate", preview.bookmarks[0].title)
    }

    @Test
    fun rejectsUnsafeAndUnsupportedBookmarkSchemes() {
        val html = """
            <A HREF="javascript:alert(1)">Bad</A>
            <A HREF="file:///tmp/test.html">Local</A>
            <A HREF="chrome://settings/">Chrome</A>
            <A HREF="https://valid.example/">Valid</A>
        """.trimIndent()

        val preview = BookmarkHtmlImporter.preview(html)

        assertEquals(4, preview.sourceRows)
        assertEquals(3, preview.skippedRows)
        assertEquals(1, preview.bookmarks.size)
        assertEquals("https://valid.example/", preview.bookmarks.single().url)
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        assertEquals(
            "A & B 'test' ✓",
            BookmarkHtmlImporter.decodeHtmlEntities("A &amp; B &#39;test&#39; &#x2713;")
        )
    }

    @Test
    fun emptyOrNonBookmarkHtmlReturnsError() {
        val empty = BookmarkHtmlImporter.preview("   ")
        assertFalse(empty.canImport)
        assertTrue(empty.error != null)

        val page = BookmarkHtmlImporter.preview("<html><body>No bookmarks</body></html>")
        assertFalse(page.canImport)
        assertTrue(page.error != null)
    }
}
