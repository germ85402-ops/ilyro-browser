package com.ilyro.browser.ui

import java.net.URI

internal data class BookmarkHtmlImportPreview(
    val bookmarks: List<BookmarkItem>,
    val sourceRows: Int,
    val skippedRows: Int,
    val duplicateRows: Int,
    val error: String? = null
) {
    val canImport: Boolean
        get() = error == null && bookmarks.isNotEmpty()
}

/** Parses the Netscape bookmark HTML format exported by Chromium, Firefox and most browsers. */
internal object BookmarkHtmlImporter {
    private val anchorRegex = Regex(
        pattern = "<a\\b([^>]*)>(.*?)</a\\s*>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val hrefRegex = Regex(
        pattern = "\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
        option = RegexOption.IGNORE_CASE
    )
    private val tagRegex = Regex("<[^>]+>")
    private val entityRegex = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|#39);")

    fun preview(rawHtml: String): BookmarkHtmlImportPreview {
        if (rawHtml.isBlank()) {
            return BookmarkHtmlImportPreview(
                bookmarks = emptyList(),
                sourceRows = 0,
                skippedRows = 0,
                duplicateRows = 0,
                error = "The bookmark file is empty."
            )
        }

        val matches = anchorRegex.findAll(rawHtml).toList()
        if (matches.isEmpty()) {
            return BookmarkHtmlImportPreview(
                bookmarks = emptyList(),
                sourceRows = 0,
                skippedRows = 0,
                duplicateRows = 0,
                error = "No standard browser bookmarks were found in this HTML file."
            )
        }

        val deduplicated = LinkedHashMap<String, BookmarkItem>()
        var skipped = 0
        var duplicates = 0

        matches.forEach { match ->
            val attributes = match.groupValues[1]
            val hrefMatch = hrefRegex.find(attributes)
            val rawHref = hrefMatch?.let { result ->
                result.groupValues[1].ifBlank {
                    result.groupValues[2].ifBlank { result.groupValues[3] }
                }
            }
            val url = rawHref?.let(::decodeHtmlEntities)?.let(::validatedBookmarkUrl)
            if (url == null) {
                skipped += 1
                return@forEach
            }

            val title = decodeHtmlEntities(
                tagRegex.replace(match.groupValues[2], "")
            ).trim().ifBlank { url }

            if (deduplicated.containsKey(url)) {
                duplicates += 1
            }
            deduplicated[url] = BookmarkItem(url = url, title = title)
        }

        return BookmarkHtmlImportPreview(
            bookmarks = deduplicated.values.toList(),
            sourceRows = matches.size,
            skippedRows = skipped,
            duplicateRows = duplicates,
            error = if (deduplicated.isEmpty()) {
                "No supported HTTP or HTTPS bookmarks were found."
            } else {
                null
            }
        )
    }

    internal fun validatedBookmarkUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
                null
            } else {
                trimmed
            }
        }.getOrNull()
    }

    internal fun decodeHtmlEntities(raw: String): String = entityRegex.replace(raw) { match ->
        when (val token = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos", "#39" -> "'"
            else -> {
                val codePoint = when {
                    token.startsWith("#x", ignoreCase = true) ->
                        token.substring(2).toIntOrNull(16)
                    token.startsWith("#") -> token.substring(1).toIntOrNull(10)
                    else -> null
                }
                codePoint
                    ?.takeIf { Character.isValidCodePoint(it) }
                    ?.let { String(Character.toChars(it)) }
                    ?: match.value
            }
        }
    }
}
