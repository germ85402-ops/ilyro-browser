package com.ilyro.browser.ui

/** Encodes ILYRO bookmarks using the Netscape bookmark format understood by major browsers. */
internal object BookmarkHtmlExporter {
    fun encode(bookmarks: List<BookmarkItem>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<!-- This is an automatically generated file. -->")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>ILYRO Bookmarks</TITLE>")
        appendLine("<H1>ILYRO Bookmarks</H1>")
        appendLine("<DL><p>")
        appendLine("    <DT><H3>ILYRO</H3>")
        appendLine("    <DL><p>")
        bookmarks.forEach { bookmark ->
            val addDate = (bookmark.createdAt / 1000L).coerceAtLeast(0L)
            append("        <DT><A HREF=\"")
            append(escapeHtml(bookmark.url))
            append("\" ADD_DATE=\"")
            append(addDate)
            append("\">")
            append(escapeHtml(bookmark.title.ifBlank { bookmark.url }))
            appendLine("</A>")
        }
        appendLine("    </DL><p>")
        appendLine("</DL><p>")
    }

    internal fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}
