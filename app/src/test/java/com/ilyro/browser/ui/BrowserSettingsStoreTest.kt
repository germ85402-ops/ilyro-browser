package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsStoreTest {

    @Test
    fun saveAndRestorePreservesReleaseCriticalSettings() {
        val prefs = MemorySharedPreferences()
        val expected = BrowserSettings(
            language = AppLanguage.RUSSIAN,
            preferredSiteLanguages = listOf("ru", "en"),
            searchEngine = SearchEngine.DUCKDUCKGO,
            theme = BrowserTheme.DARK,
            appIcon = AppIcon.PINK_DARK,
            accent = BrowserAccent.VIOLET,
            toolbarPosition = ToolbarPosition.TOP,
            toolbarActions = setOf(ToolbarAction.BACK, ToolbarAction.FORWARD, ToolbarAction.TABS),
            showQuickAccess = false,
            shortcutSize = HomeShortcutSize.LARGE,
            showShortcutLabels = false,
            homeBackground = HomeBackground.NONE,
            useSeparateDarkBackground = true,
            darkHomeBackground = HomeBackground.AURORA,
            wallpaperFit = WallpaperFit.FIT,
            wallpaperDim = WallpaperDim.MEDIUM,
            wallpaperBlur = WallpaperBlur.SOFT,
            tabLayout = TabLayoutMode.LIST,
            tabPreviewSize = TabPreviewSize.MEDIUM,
            uiDensity = UiDensity.COMPACT,
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

        BrowserSettingsStore.save(prefs, expected)
        val restored = BrowserSettingsStore.restore(prefs)
        val expectedRestored = expected.copy(
            tabLayout = TabLayoutMode.GRID,
            tabPreviewSize = TabPreviewSize.LARGE
        )

        assertEquals(expectedRestored, restored)
        assertEquals(AppIcon.PINK_DARK, restored.appIcon)
        assertEquals(BrowserTheme.DARK, restored.theme)
        assertEquals(ToolbarPosition.TOP, restored.toolbarPosition)
        assertEquals(TabLayoutMode.GRID, restored.tabLayout)
        assertEquals(TabPreviewSize.LARGE, restored.tabPreviewSize)
        assertEquals(UiDensity.COMPACT, restored.uiDensity)
    }

    @Test
    fun restoreFallsBackSafelyForUnknownOrLegacyValues() {
        val prefs = MemorySharedPreferences(
            mutableMapOf(
                "settings_app_icon" to "REMOVED_ICON",
                "settings_theme" to "AMOLED",
                "settings_search_engine" to "REMOVED_ENGINE",
                "settings_text_scale" to 9.0f,
                "settings_site_languages" to "RU|en|ru|bad-code|de",
                "settings_toolbar_actions" to setOf("BACK", "SHIELD", "TABS", "REMOVED_ACTION"),
                "settings_tab_layout" to "COMPACT",
                "settings_tab_preview_size" to "NONE",
                "settings_ui_density" to "COMFORTABLE"
            )
        )

        val restored = BrowserSettingsStore.restore(prefs)

        assertEquals(AppIcon.DEER, restored.appIcon)
        assertEquals(BrowserTheme.SYSTEM, restored.theme)
        assertEquals(SearchEngine.GOOGLE, restored.searchEngine)
        assertEquals(1.30f, restored.textScale)
        assertEquals(listOf("ru", "en", "de"), restored.preferredSiteLanguages)
        assertEquals(setOf(ToolbarAction.BACK, ToolbarAction.TABS), restored.toolbarActions)
        assertFalse(ToolbarAction.SHIELD in restored.toolbarActions)
        assertEquals(TabLayoutMode.GRID, restored.tabLayout)
        assertEquals(TabPreviewSize.LARGE, restored.tabPreviewSize)
        assertEquals(UiDensity.COMFORTABLE, restored.uiDensity)
    }

    @Test
    fun emptyPreferencesUseStandardInterfaceDensity() {
        val restored = BrowserSettingsStore.restore(MemorySharedPreferences())
        assertEquals(UiDensity.STANDARD, restored.uiDensity)
    }

    @Test
    fun emptyOrInvalidToolbarActionSetRestoresSafeDefault() {
        val prefs = MemorySharedPreferences(
            mutableMapOf("settings_toolbar_actions" to setOf("SHIELD", "UNKNOWN"))
        )

        val restored = BrowserSettingsStore.restore(prefs)
        val expected = setOf(ToolbarAction.TABS)

        assertEquals(expected, restored.toolbarActions)
        assertTrue(restored.toolbarActions.isNotEmpty())
        assertFalse(ToolbarAction.SHIELD in restored.toolbarActions)
    }
}

private class MemorySharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        ((values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet())

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val target: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removals += key
        }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun applyChanges() {
            if (clearRequested) target.clear()
            removals.forEach(target::remove)
            pending.forEach { (key, value) ->
                if (value == null) target.remove(key) else target[key] = value
            }
        }
    }
}
