package com.ilyro.browser.ui

import android.content.SharedPreferences
import java.util.Locale

internal enum class AppLanguage(val displayName: String) {
    SYSTEM("System"),
    ENGLISH("English"),
    RUSSIAN("Русский")
}

internal enum class SearchEngine(val displayName: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    YANDEX("Yandex", "https://yandex.com/search/?text="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=")
}

internal enum class BrowserTheme(val displayName: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

internal enum class AppIcon { DEER, CLASSIC, MONOCHROME, PINK_LIGHT, PINK_DARK }

internal enum class ToolbarPosition { BOTTOM, TOP }

internal enum class BrowserAccent { ILYRO, BLUE, VIOLET, FOREST }

internal enum class ToolbarAction { BACK, FORWARD, SHIELD, TABS }

internal enum class HomeShortcutSize { SMALL, STANDARD, LARGE }

internal enum class HomeBackground {
    NONE,
    CLEAN,
    SOFT_GRADIENT,
    MOUNTAIN_DUSK,
    BLUE_HORIZON,
    NIGHT_WAVES,
    AURORA,
    CUSTOM
}

internal enum class WallpaperFit { FILL, FIT, CENTER }

internal enum class WallpaperDim(val alpha: Float) {
    OFF(0f), LIGHT(0.20f), MEDIUM(0.35f), STRONG(0.50f)
}

internal enum class WallpaperBlur(val radiusDp: Int) {
    OFF(0), SOFT(6), MEDIUM(14)
}

internal enum class TabLayoutMode { GRID, LIST, COMPACT }

internal enum class TabPreviewSize { LARGE, MEDIUM, NONE }

internal enum class UiDensity { COMPACT, STANDARD, COMFORTABLE }

internal data class BrowserSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val preferredSiteLanguages: List<String> = defaultPreferredSiteLanguages(),
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val theme: BrowserTheme = BrowserTheme.SYSTEM,
    val appIcon: AppIcon = AppIcon.DEER,
    val accent: BrowserAccent = BrowserAccent.ILYRO,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val toolbarActions: Set<ToolbarAction> = setOf(ToolbarAction.TABS),
    val showQuickAccess: Boolean = true,
    val shortcutSize: HomeShortcutSize = HomeShortcutSize.STANDARD,
    val showShortcutLabels: Boolean = true,
    val homeBackground: HomeBackground = HomeBackground.MOUNTAIN_DUSK,
    val customWallpaperUri: String? = null,
    val useSeparateDarkBackground: Boolean = false,
    val darkHomeBackground: HomeBackground = HomeBackground.MOUNTAIN_DUSK,
    val darkCustomWallpaperUri: String? = null,
    val wallpaperFit: WallpaperFit = WallpaperFit.FILL,
    val wallpaperDim: WallpaperDim = WallpaperDim.MEDIUM,
    val wallpaperBlur: WallpaperBlur = WallpaperBlur.OFF,
    val tabLayout: TabLayoutMode = TabLayoutMode.GRID,
    val tabPreviewSize: TabPreviewSize = TabPreviewSize.LARGE,
    val uiDensity: UiDensity = UiDensity.STANDARD,
    val adBlockingEnabled: Boolean = true,
    val darkWebsitesEnabled: Boolean = false,
    val historyEnabled: Boolean = true,
    val restoreTabs: Boolean = true,
    val downloadsOverMetered: Boolean = true,
    val desktopMode: Boolean = false,
    val textScale: Float = 1.0f,
    val httpsOnly: Boolean = false,
    val thirdPartyCookieIsolation: Boolean = true,
    val globalPrivacyControl: Boolean = true,
    val clearPrivateDataOnExit: Boolean = true
)

internal fun defaultPreferredSiteLanguages(): List<String> {
    val primaryLanguage = Locale.getDefault().language
        .lowercase(Locale.ROOT)
        .takeIf { it.matches(Regex("[a-z]{2,3}")) }
    return listOf(primaryLanguage ?: "en")
}

internal object BrowserSettingsStore {
    private const val KEY_LANGUAGE = "settings_language"
    private const val KEY_SITE_LANGUAGES = "settings_site_languages"
    private const val KEY_SEARCH_ENGINE = "settings_search_engine"
    private const val KEY_THEME = "settings_theme"
    private const val KEY_APP_ICON = "settings_app_icon"
    private const val KEY_ACCENT = "settings_accent"
    private const val KEY_TOOLBAR_POSITION = "settings_toolbar_position"
    private const val KEY_TOOLBAR_ACTIONS = "settings_toolbar_actions"
    private const val KEY_TOOLBAR_COMPACT_DEFAULT_MIGRATED = "settings_toolbar_compact_default_v2"
    private const val KEY_SHOW_QUICK_ACCESS = "settings_show_quick_access"
    private const val KEY_SHORTCUT_SIZE = "settings_shortcut_size"
    private const val KEY_SHOW_SHORTCUT_LABELS = "settings_show_shortcut_labels"
    private const val KEY_HOME_BACKGROUND = "settings_home_background"
    private const val KEY_CUSTOM_WALLPAPER_URI = "settings_custom_wallpaper_uri"
    private const val KEY_SEPARATE_DARK_BACKGROUND = "settings_separate_dark_background"
    private const val KEY_DARK_HOME_BACKGROUND = "settings_dark_home_background"
    private const val KEY_DARK_CUSTOM_WALLPAPER_URI = "settings_dark_custom_wallpaper_uri"
    private const val KEY_WALLPAPER_FIT = "settings_wallpaper_fit"
    private const val KEY_WALLPAPER_DIM = "settings_wallpaper_dim"
    private const val KEY_WALLPAPER_BLUR = "settings_wallpaper_blur"
    private const val KEY_TAB_LAYOUT = "settings_tab_layout"
    private const val KEY_TAB_PREVIEW_SIZE = "settings_tab_preview_size"
    private const val KEY_UI_DENSITY = "settings_ui_density"
    private const val KEY_AD_BLOCKING = "settings_ad_blocking"
    private const val KEY_DARK_WEBSITES = "settings_dark_websites"
    private const val KEY_HISTORY_ENABLED = "settings_history_enabled"
    private const val KEY_RESTORE_TABS = "settings_restore_tabs"
    private const val KEY_DOWNLOADS_METERED = "settings_downloads_metered"
    private const val KEY_DESKTOP_MODE = "settings_desktop_mode"
    private const val KEY_TEXT_SCALE = "settings_text_scale"
    private const val KEY_HTTPS_ONLY = "settings_https_only"
    private const val KEY_COOKIE_ISOLATION = "settings_cookie_isolation"
    private const val KEY_GPC = "settings_gpc"
    private const val KEY_CLEAR_PRIVATE = "settings_clear_private"

    private fun restoreToolbarActions(prefs: SharedPreferences): Set<ToolbarAction> {
        val stored = prefs.getStringSet(KEY_TOOLBAR_ACTIONS, null)
            ?.mapNotNull { name -> ToolbarAction.entries.firstOrNull { it.name == name } }
            ?.filterNot { it == ToolbarAction.SHIELD }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }

        val legacyDefault = setOf(
            ToolbarAction.BACK,
            ToolbarAction.FORWARD,
            ToolbarAction.TABS
        )

        if (!prefs.getBoolean(KEY_TOOLBAR_COMPACT_DEFAULT_MIGRATED, false)) {
            val migrated = if (stored == null || stored == legacyDefault) {
                setOf(ToolbarAction.TABS)
            } else {
                stored
            }
            prefs.edit()
                .putStringSet(KEY_TOOLBAR_ACTIONS, migrated.map { it.name }.toSet())
                .putBoolean(KEY_TOOLBAR_COMPACT_DEFAULT_MIGRATED, true)
                .apply()
            return migrated
        }

        return stored?.takeIf { it.isNotEmpty() } ?: setOf(ToolbarAction.TABS)
    }

    fun restore(prefs: SharedPreferences): BrowserSettings = BrowserSettings(
        language = enumValueOrDefault(prefs.getString(KEY_LANGUAGE, null), AppLanguage.SYSTEM),
        preferredSiteLanguages = prefs.getString(KEY_SITE_LANGUAGES, null)
            ?.split('|')
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.matches(Regex("[a-z]{2,3}")) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultPreferredSiteLanguages(),
        searchEngine = enumValueOrDefault(prefs.getString(KEY_SEARCH_ENGINE, null), SearchEngine.GOOGLE),
        theme = enumValueOrDefault(prefs.getString(KEY_THEME, null), BrowserTheme.SYSTEM),
        appIcon = enumValueOrDefault(prefs.getString(KEY_APP_ICON, null), AppIcon.DEER),
        accent = enumValueOrDefault(prefs.getString(KEY_ACCENT, null), BrowserAccent.ILYRO),
        toolbarPosition = enumValueOrDefault(prefs.getString(KEY_TOOLBAR_POSITION, null), ToolbarPosition.BOTTOM),
        toolbarActions = restoreToolbarActions(prefs),
        showQuickAccess = prefs.getBoolean(KEY_SHOW_QUICK_ACCESS, true),
        shortcutSize = enumValueOrDefault(prefs.getString(KEY_SHORTCUT_SIZE, null), HomeShortcutSize.STANDARD),
        showShortcutLabels = prefs.getBoolean(KEY_SHOW_SHORTCUT_LABELS, true),
        homeBackground = enumValueOrDefault(prefs.getString(KEY_HOME_BACKGROUND, null), HomeBackground.MOUNTAIN_DUSK),
        customWallpaperUri = prefs.getString(KEY_CUSTOM_WALLPAPER_URI, null),
        useSeparateDarkBackground = prefs.getBoolean(KEY_SEPARATE_DARK_BACKGROUND, false),
        darkHomeBackground = enumValueOrDefault(
            prefs.getString(KEY_DARK_HOME_BACKGROUND, null),
            HomeBackground.MOUNTAIN_DUSK
        ),
        darkCustomWallpaperUri = prefs.getString(KEY_DARK_CUSTOM_WALLPAPER_URI, null),
        wallpaperFit = enumValueOrDefault(prefs.getString(KEY_WALLPAPER_FIT, null), WallpaperFit.FILL),
        wallpaperDim = enumValueOrDefault(prefs.getString(KEY_WALLPAPER_DIM, null), WallpaperDim.MEDIUM),
        wallpaperBlur = enumValueOrDefault(prefs.getString(KEY_WALLPAPER_BLUR, null), WallpaperBlur.OFF),
        tabLayout = TabLayoutMode.GRID,
        tabPreviewSize = TabPreviewSize.LARGE,
        uiDensity = enumValueOrDefault(prefs.getString(KEY_UI_DENSITY, null), UiDensity.STANDARD),
        adBlockingEnabled = prefs.getBoolean(KEY_AD_BLOCKING, true),
        darkWebsitesEnabled = prefs.getBoolean(KEY_DARK_WEBSITES, false),
        historyEnabled = prefs.getBoolean(KEY_HISTORY_ENABLED, true),
        restoreTabs = prefs.getBoolean(KEY_RESTORE_TABS, true),
        downloadsOverMetered = prefs.getBoolean(KEY_DOWNLOADS_METERED, true),
        desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false),
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f).coerceIn(0.85f, 1.30f),
        httpsOnly = prefs.getBoolean(KEY_HTTPS_ONLY, false),
        thirdPartyCookieIsolation = prefs.getBoolean(KEY_COOKIE_ISOLATION, true),
        globalPrivacyControl = prefs.getBoolean(KEY_GPC, true),
        clearPrivateDataOnExit = prefs.getBoolean(KEY_CLEAR_PRIVATE, true)
    )

    fun save(prefs: SharedPreferences, settings: BrowserSettings) {
        prefs.edit()
            .putString(KEY_LANGUAGE, settings.language.name)
            .putString(KEY_SITE_LANGUAGES, settings.preferredSiteLanguages.joinToString("|"))
            .putString(KEY_SEARCH_ENGINE, settings.searchEngine.name)
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_APP_ICON, settings.appIcon.name)
            .putString(KEY_ACCENT, settings.accent.name)
            .putString(KEY_TOOLBAR_POSITION, settings.toolbarPosition.name)
            .putStringSet(KEY_TOOLBAR_ACTIONS, settings.toolbarActions.map { it.name }.toSet())
            .putBoolean(KEY_TOOLBAR_COMPACT_DEFAULT_MIGRATED, true)
            .putBoolean(KEY_SHOW_QUICK_ACCESS, settings.showQuickAccess)
            .putString(KEY_SHORTCUT_SIZE, settings.shortcutSize.name)
            .putBoolean(KEY_SHOW_SHORTCUT_LABELS, settings.showShortcutLabels)
            .putString(KEY_HOME_BACKGROUND, settings.homeBackground.name)
            .putString(KEY_CUSTOM_WALLPAPER_URI, settings.customWallpaperUri)
            .putBoolean(KEY_SEPARATE_DARK_BACKGROUND, settings.useSeparateDarkBackground)
            .putString(KEY_DARK_HOME_BACKGROUND, settings.darkHomeBackground.name)
            .putString(KEY_DARK_CUSTOM_WALLPAPER_URI, settings.darkCustomWallpaperUri)
            .putString(KEY_WALLPAPER_FIT, settings.wallpaperFit.name)
            .putString(KEY_WALLPAPER_DIM, settings.wallpaperDim.name)
            .putString(KEY_WALLPAPER_BLUR, settings.wallpaperBlur.name)
            .putString(KEY_UI_DENSITY, settings.uiDensity.name)
            .putBoolean(KEY_AD_BLOCKING, settings.adBlockingEnabled)
            .putBoolean(KEY_DARK_WEBSITES, settings.darkWebsitesEnabled)
            .putBoolean(KEY_HISTORY_ENABLED, settings.historyEnabled)
            .putBoolean(KEY_RESTORE_TABS, settings.restoreTabs)
            .putBoolean(KEY_DOWNLOADS_METERED, settings.downloadsOverMetered)
            .putBoolean(KEY_DESKTOP_MODE, settings.desktopMode)
            .putFloat(KEY_TEXT_SCALE, settings.textScale)
            .putBoolean(KEY_HTTPS_ONLY, settings.httpsOnly)
            .putBoolean(KEY_COOKIE_ISOLATION, settings.thirdPartyCookieIsolation)
            .putBoolean(KEY_GPC, settings.globalPrivacyControl)
            .putBoolean(KEY_CLEAR_PRIVATE, settings.clearPrivateDataOnExit)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
