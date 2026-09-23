package com.ilyro.browser.sync

import com.ilyro.browser.ui.AppIcon
import com.ilyro.browser.ui.AppLanguage
import com.ilyro.browser.ui.BrowserSettings
import com.ilyro.browser.ui.BrowserTheme
import com.ilyro.browser.ui.CustomSearchEngine
import com.ilyro.browser.ui.HomeBackground
import com.ilyro.browser.ui.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsSyncCodecTest {
    @Test
    fun roundTrip_preservesAllSyncedSettings() {
        val source = BrowserSettings(
            language = AppLanguage.RUSSIAN,
            preferredSiteLanguages = listOf("ru", "en", "hy"),
            searchEngine = SearchEngine.DUCKDUCKGO,
            onlineSearchSuggestionsEnabled = false,
            customSearchEngines = listOf(
                CustomSearchEngine(
                    id = "kagi",
                    displayName = "Kagi",
                    queryUrlTemplate = "https://kagi.com/search?q=%s"
                )
            ),
            customSearchEngineId = "kagi",
            theme = BrowserTheme.DARK,
            appIcon = AppIcon.PINK_DARK,
            adBlockingEnabled = false,
            darkWebsitesEnabled = true,
            historyEnabled = false,
            restoreTabs = false,
            downloadsOverMetered = false,
            desktopMode = true,
            textScale = 1.15f,
            httpsOnly = true,
            thirdPartyCookieIsolation = false,
            globalPrivacyControl = false,
            clearPrivateDataOnExit = false
        )

        val restored = BrowserSettingsSyncCodec.decode(
            BrowserSettingsSyncCodec.encode(source)
        )

        assertEquals(source, restored)
    }

    @Test
    fun roundTrip_preservesExpandedBrowserLanguage() {
        val source = BrowserSettings(
            language = AppLanguage.CHINESE,
            preferredSiteLanguages = listOf("zh", "en")
        )

        val restored = BrowserSettingsSyncCodec.decode(
            BrowserSettingsSyncCodec.encode(source)
        )

        assertEquals(AppLanguage.CHINESE, restored.language)
        assertEquals(listOf("zh", "en"), restored.preferredSiteLanguages)
    }

    @Test
    fun decode_unknownEnumAndMissingFields_fallsBackSafely() {
        val restored = BrowserSettingsSyncCodec.decode(
            """{
                "schemaVersion": 1,
                "language": "UNKNOWN",
                "searchEngine": "UNKNOWN",
                "theme": "UNKNOWN",
                "homeBackground": "MOUNTAIN_DUSK",
                "darkHomeBackground": "AURORA",
                "desktopMode": true,
                "textScale": 9.0
            }""".trimIndent()
        )

        assertEquals(AppLanguage.SYSTEM, restored.language)
        assertEquals(SearchEngine.GOOGLE, restored.searchEngine)
        assertEquals(BrowserTheme.SYSTEM, restored.theme)
        assertEquals(HomeBackground.LIGHT_DAWN_LAKE, restored.homeBackground)
        assertEquals(HomeBackground.STILLWATER, restored.darkHomeBackground)
        assertTrue(restored.desktopMode)
        assertEquals(1.30f, restored.textScale)
        assertTrue(restored.adBlockingEnabled)
        assertTrue(restored.onlineSearchSuggestionsEnabled)
        assertFalse(restored.darkWebsitesEnabled)
        assertTrue(restored.historyEnabled)
        assertTrue(restored.restoreTabs)
        assertTrue(restored.downloadsOverMetered)
        assertFalse(restored.httpsOnly)
        assertTrue(restored.thirdPartyCookieIsolation)
        assertTrue(restored.globalPrivacyControl)
        assertTrue(restored.clearPrivateDataOnExit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_futureSchema_rejectsPayloadInsteadOfApplyingUnknownData() {
        BrowserSettingsSyncCodec.decode("""{"schemaVersion":999}""")
    }
}
