package com.ilyro.browser.ui

import java.net.URI
import java.util.Locale

internal enum class OmniboxSuggestionKind { HISTORY, BOOKMARK, QUICK_LINK, ADDRESS, SEARCH }

internal data class OmniboxSuggestion(
    val kind: OmniboxSuggestionKind,
    val value: String,
    val title: String,
    val subtitle: String
)

private data class OmniboxSiteCandidate(
    val suggestion: OmniboxSuggestion,
    val score: Int,
    val order: Int
)

/** Shared address/query detection so suggestions and Enter always agree on navigation intent. */
internal fun looksLikeNavigation(value: String): Boolean {
    val text = value.trim()
    if (text.isEmpty()) return false
    if (text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true)
    ) return true
    if (text.any(Char::isWhitespace)) return false

    val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringBefore(':')
    return authority.contains('.') || host.equals("localhost", ignoreCase = true)
}

/** Builds local site matches first and always keeps the exact typed query selectable. */
internal fun buildOmniboxSuggestions(
    query: String,
    engine: SearchEngine,
    history: List<HistoryItem>,
    bookmarks: List<BookmarkItem>,
    quickLinks: List<QuickLink>,
    remote: List<String>
): List<OmniboxSuggestion> {
    val trimmed = query.trim()
    val normalized = trimmed.lowercase(Locale.ROOT)
    val recentHistory = history.distinctBy { it.url }.take(300)
    val savedBookmarks = bookmarks.distinctBy { it.url }.take(200)
    val savedQuickLinks = quickLinks.distinctBy { it.url }.take(12)

    val siteSuggestions = if (trimmed.isBlank()) {
        buildList {
            recentHistory.take(4).forEach { add(it.toSuggestion(OmniboxSuggestionKind.HISTORY)) }
            savedBookmarks.take(3).forEach { add(it.toSuggestion()) }
            savedQuickLinks.take(2).forEach { add(it.toSuggestion()) }
        }.distinctBy { it.value }.take(8)
    } else {
        val candidates = buildList {
            recentHistory.forEachIndexed { index, item ->
                val suggestion = item.toSuggestion(OmniboxSuggestionKind.HISTORY)
                suggestion.matchingScore(normalized)
                    ?.let { score ->
                        add(OmniboxSiteCandidate(suggestion, score + historyRecencyBonus(index), index))
                    }
            }
            savedBookmarks.forEachIndexed { index, item ->
                val suggestion = item.toSuggestion()
                suggestion.matchingScore(normalized)
                    ?.let { score -> add(OmniboxSiteCandidate(suggestion, score + 80, index)) }
            }
            savedQuickLinks.forEachIndexed { index, item ->
                val suggestion = item.toSuggestion()
                suggestion.matchingScore(normalized)
                    ?.let { score -> add(OmniboxSiteCandidate(suggestion, score + 50, index)) }
            }
        }
        candidates
            .sortedWith(compareByDescending<OmniboxSiteCandidate> { it.score }.thenBy { it.order })
            .distinctBy { it.suggestion.value }
            .take(if (trimmed.isNotBlank() && !looksLikeNavigation(trimmed)) 5 else 8)
            .map { it.suggestion }
    }

    val result = siteSuggestions.toMutableList()
    if (trimmed.isNotBlank() && looksLikeNavigation(trimmed)) {
        result.add(
            0,
            OmniboxSuggestion(
                kind = OmniboxSuggestionKind.ADDRESS,
                value = trimmed,
                title = trimmed,
                subtitle = ""
            )
        )
    } else if (trimmed.isNotBlank()) {
        val exactQuery = OmniboxSuggestion(
            kind = OmniboxSuggestionKind.SEARCH,
            value = trimmed,
            title = trimmed,
            subtitle = engine.displayName
        )
        val slotsForRemote = (8 - result.size - 1).coerceAtLeast(0)
        remote.asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(slotsForRemote)
            .forEach { suggestion ->
                result += OmniboxSuggestion(
                    kind = OmniboxSuggestionKind.SEARCH,
                    value = suggestion,
                    title = suggestion,
                    subtitle = engine.displayName
                )
            }
        result += exactQuery
    }

    return result.distinctBy { "${it.kind}|${it.value.lowercase(Locale.ROOT)}" }.take(8)
}

private fun HistoryItem.toSuggestion(kind: OmniboxSuggestionKind): OmniboxSuggestion =
    OmniboxSuggestion(
        kind = kind,
        value = url,
        title = title.ifBlank { suggestionHost(url).ifBlank { url } },
        subtitle = suggestionHost(url).ifBlank { url }
    )

private fun BookmarkItem.toSuggestion(): OmniboxSuggestion =
    OmniboxSuggestion(
        kind = OmniboxSuggestionKind.BOOKMARK,
        value = url,
        title = title.ifBlank { suggestionHost(url).ifBlank { url } },
        subtitle = suggestionHost(url).ifBlank { url }
    )

private fun QuickLink.toSuggestion(): OmniboxSuggestion =
    OmniboxSuggestion(
        kind = OmniboxSuggestionKind.QUICK_LINK,
        value = url,
        title = label.ifBlank { suggestionHost(url).ifBlank { url } },
        subtitle = suggestionHost(url).ifBlank { url }
    )

private fun OmniboxSuggestion.matchingScore(query: String): Int? {
    val title = title.lowercase(Locale.ROOT)
    val url = value.lowercase(Locale.ROOT)
    val host = suggestionHost(value).lowercase(Locale.ROOT)
    return when {
        host.startsWith(query) -> 500
        title.startsWith(query) -> 420
        url.startsWith(query) -> 360
        query in host -> 300
        query in title -> 240
        query in url -> 180
        else -> null
    }
}

private fun suggestionHost(url: String): String =
    runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.") }
        .getOrDefault("")

private fun historyRecencyBonus(index: Int): Int = 50 - index.coerceAtMost(50)
