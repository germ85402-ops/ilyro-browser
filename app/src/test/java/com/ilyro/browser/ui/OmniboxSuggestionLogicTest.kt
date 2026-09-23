package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxSuggestionLogicTest {
    @Test
    fun blankQueryOffersRecentSitesBookmarksAndQuickLinks() {
        val suggestions = buildOmniboxSuggestions(
            query = "",
            engine = SearchEngine.GOOGLE,
            history = listOf(HistoryItem("https://history.example/", "History", 3L)),
            bookmarks = listOf(BookmarkItem("https://saved.example/", "Saved")),
            quickLinks = listOf(QuickLink("quick", "Quick", "https://quick.example/")),
            remote = emptyList()
        )

        assertEquals(
            listOf(
                OmniboxSuggestionKind.HISTORY,
                OmniboxSuggestionKind.BOOKMARK,
                OmniboxSuggestionKind.QUICK_LINK
            ),
            suggestions.map { it.kind }
        )
    }

    @Test
    fun queryKeepsLocalMatchesAndExactSearchWithinEightRows() {
        val suggestions = buildOmniboxSuggestions(
            query = "kotlin",
            engine = SearchEngine.GOOGLE,
            history = listOf(HistoryItem("https://kotlinlang.org/", "Kotlin", 3L)),
            bookmarks = listOf(BookmarkItem("https://kotlinlang.org/docs", "Kotlin Docs")),
            quickLinks = listOf(QuickLink("quick", "Kotlin Blog", "https://blog.example/")),
            remote = (1..10).map { "kotlin result $it" }
        )

        assertTrue(suggestions.size <= 8)
        assertTrue(suggestions.any { it.kind == OmniboxSuggestionKind.HISTORY })
        assertTrue(suggestions.any { it.kind == OmniboxSuggestionKind.BOOKMARK })
        assertEquals("kotlin", suggestions.last().value)
        assertTrue(suggestions.dropLast(1).any { it.value == "kotlin result 1" })
    }

    @Test
    fun directAddressOffersNavigationWithoutSearchSuggestions() {
        val suggestions = buildOmniboxSuggestions(
            query = "LOCALHOST:3000",
            engine = SearchEngine.GOOGLE,
            history = emptyList(),
            bookmarks = emptyList(),
            quickLinks = emptyList(),
            remote = listOf("localhost result")
        )

        assertEquals(OmniboxSuggestionKind.ADDRESS, suggestions.first().kind)
        assertFalse(suggestions.any { it.kind == OmniboxSuggestionKind.SEARCH })
    }

    @Test
    fun navigationDetectionHandlesSchemesDomainsAndLocalhost() {
        assertTrue(looksLikeNavigation("HTTPS://example.com/path"))
        assertTrue(looksLikeNavigation("example.com:8443/path"))
        assertTrue(looksLikeNavigation("localhost:3000"))
        assertFalse(looksLikeNavigation("best local cafes"))
    }

    @Test
    fun recentSearchesShowDecodedQueriesAndSkipOrdinaryPages() {
        val recent = buildRecentSearchSuggestions(
            listOf(
                HistoryItem("https://www.google.com/search?q=tasklet+ai", "tasklet ai - Поиск в Google", 4L),
                HistoryItem("https://google.com/search?q=github", "github - Search Google", 3L),
                HistoryItem("https://shop.example/items?q=boots", "Boots", 2L)
            )
        )

        assertEquals(listOf("tasklet ai", "github"), recent.map { it.title })
        assertEquals("https://www.google.com/search?q=tasklet+ai", recent.first().value)
    }

    @Test
    fun backHidesKeyboardBeforeClosingFocusedSuggestions() {
        assertEquals(
            OmniboxBackAction.HIDE_KEYBOARD,
            nextOmniboxBackAction(isFocused = true, imeVisible = true)
        )
        assertEquals(
            OmniboxBackAction.CLOSE_SUGGESTIONS,
            nextOmniboxBackAction(isFocused = true, imeVisible = false)
        )
        assertEquals(
            OmniboxBackAction.NONE,
            nextOmniboxBackAction(isFocused = false, imeVisible = false)
        )
    }

    @Test
    fun outsideTapDismissalAndPopupWidthAdaptToPhoneAndTablet() {
        assertFalse(shouldDismissOmniboxOnOutsideTap(411))
        assertTrue(shouldDismissOmniboxOnOutsideTap(600))
        assertEquals(
            968,
            calculateOmniboxPopupWidthPx(
                viewportWidthPx = 1000,
                wideLayout = false,
                maxWideWidthPx = 1200,
                phoneSideMarginPx = 16,
                wideSideMarginPx = 24
            )
        )
        assertEquals(
            780,
            calculateOmniboxPopupWidthPx(
                viewportWidthPx = 1000,
                wideLayout = true,
                maxWideWidthPx = 900,
                phoneSideMarginPx = 16,
                wideSideMarginPx = 24
            )
        )
    }
}
