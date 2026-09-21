package com.ilyro.browser.sync

import com.ilyro.browser.ui.AppIcon
import com.ilyro.browser.ui.AppLanguage
import com.ilyro.browser.ui.BrowserAccent
import com.ilyro.browser.ui.BrowserSettings
import com.ilyro.browser.ui.BrowserTheme
import com.ilyro.browser.ui.CustomSearchEngine
import com.ilyro.browser.ui.MAX_CUSTOM_SEARCH_ENGINES
import com.ilyro.browser.ui.normalizeCustomSearchEngine
import com.ilyro.browser.ui.HomeBackground
import com.ilyro.browser.ui.HomeShortcutSize
import com.ilyro.browser.ui.SearchEngine
import com.ilyro.browser.ui.TabLayoutMode
import com.ilyro.browser.ui.TabPreviewSize
import com.ilyro.browser.ui.ToolbarAction
import com.ilyro.browser.ui.ToolbarPosition
import com.ilyro.browser.ui.UiDensity
import com.ilyro.browser.ui.WallpaperBlur
import com.ilyro.browser.ui.WallpaperDim
import com.ilyro.browser.ui.WallpaperFit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable, versioned wire format for the non-sensitive browser preferences we sync.
 * Cookies, site sessions, passwords and private-tab data are intentionally excluded.
 */
internal object BrowserSettingsSyncCodec {
    const val SCHEMA_VERSION = 1

    fun encode(settings: BrowserSettings): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("language", settings.language.name)
        .put("preferredSiteLanguages", settings.preferredSiteLanguages.joinToString(","))
        .put("searchEngine", settings.searchEngine.name)
        .put(
            "customSearchEngines",
            JSONArray().apply {
                settings.customSearchEngines.forEach { engine ->
                    put(
                        JSONObject()
                            .put("id", engine.id)
                            .put("name", engine.displayName)
                            .put("url", engine.queryUrlTemplate)
                    )
                }
            }
        )
        .put("customSearchEngineId", settings.customSearchEngineId.orEmpty())
        .put("theme", settings.theme.name)
        .put("appIcon", settings.appIcon.name)
        .put("accent", settings.accent.name)
        .put("toolbarPosition", settings.toolbarPosition.name)
        .put("toolbarActions", settings.toolbarActions.joinToString(",") { it.name })
        .put("showQuickAccess", settings.showQuickAccess)
        .put("shortcutSize", settings.shortcutSize.name)
        .put("showShortcutLabels", settings.showShortcutLabels)
        .put("homeBackground", settings.homeBackground.name)
        .put("customWallpaperUri", settings.customWallpaperUri.orEmpty())
        .put("useSeparateDarkBackground", settings.useSeparateDarkBackground)
        .put("darkHomeBackground", settings.darkHomeBackground.name)
        .put("darkCustomWallpaperUri", settings.darkCustomWallpaperUri.orEmpty())
        .put("wallpaperFit", settings.wallpaperFit.name)
        .put("wallpaperDim", settings.wallpaperDim.name)
        .put("wallpaperBlur", settings.wallpaperBlur.name)
        .put("tabLayout", settings.tabLayout.name)
        .put("tabPreviewSize", settings.tabPreviewSize.name)
        .put("uiDensity", settings.uiDensity.name)
        .put("adBlockingEnabled", settings.adBlockingEnabled)
        .put("darkWebsitesEnabled", settings.darkWebsitesEnabled)
        .put("historyEnabled", settings.historyEnabled)
        .put("restoreTabs", settings.restoreTabs)
        .put("downloadsOverMetered", settings.downloadsOverMetered)
        .put("desktopMode", settings.desktopMode)
        .put("textScale", settings.textScale.toDouble())
        .put("httpsOnly", settings.httpsOnly)
        .put("thirdPartyCookieIsolation", settings.thirdPartyCookieIsolation)
        .put("globalPrivacyControl", settings.globalPrivacyControl)
        .put("clearPrivateDataOnExit", settings.clearPrivateDataOnExit)
        .toString()

    fun decode(payload: String): BrowserSettings {
        val json = JSONObject(payload)
        val version = json.optInt("schemaVersion", 0)
        require(version == SCHEMA_VERSION) {
            "Unsupported settings sync schema version: $version"
        }

        val defaults = BrowserSettings()
        val customSearchEngines = decodeCustomSearchEngines(json.optJSONArray("customSearchEngines"))
        val customSearchEngineId = json.optString("customSearchEngineId")
            .takeIf { id -> customSearchEngines.any { it.id == id } }

        return BrowserSettings(
            language = enumOrDefault(json.optString("language"), defaults.language),
            preferredSiteLanguages = json.optString("preferredSiteLanguages")
                .split(',')
                .map { it.trim().lowercase() }
                .filter { it.matches(Regex("[a-z]{2,3}")) }
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?: defaults.preferredSiteLanguages,
            searchEngine = enumOrDefault(json.optString("searchEngine"), defaults.searchEngine),
            customSearchEngines = customSearchEngines,
            customSearchEngineId = customSearchEngineId,
            theme = enumOrDefault(json.optString("theme"), defaults.theme),
            appIcon = enumOrDefault(json.optString("appIcon"), defaults.appIcon),
            accent = enumOrDefault(json.optString("accent"), defaults.accent),
            toolbarPosition = enumOrDefault(json.optString("toolbarPosition"), defaults.toolbarPosition),
            toolbarActions = json.optString("toolbarActions")
                .split(',')
                .mapNotNull { name -> ToolbarAction.entries.firstOrNull { it.name == name } }
                .toSet()
                .takeIf { it.isNotEmpty() } ?: defaults.toolbarActions,
            showQuickAccess = json.optBoolean("showQuickAccess", defaults.showQuickAccess),
            shortcutSize = enumOrDefault(json.optString("shortcutSize"), defaults.shortcutSize),
            showShortcutLabels = json.optBoolean("showShortcutLabels", defaults.showShortcutLabels),
            homeBackground = enumOrDefault(json.optString("homeBackground"), defaults.homeBackground),
            customWallpaperUri = json.optString("customWallpaperUri").takeIf { it.isNotBlank() },
            useSeparateDarkBackground = json.optBoolean(
                "useSeparateDarkBackground",
                defaults.useSeparateDarkBackground
            ),
            darkHomeBackground = enumOrDefault(
                json.optString("darkHomeBackground"),
                defaults.darkHomeBackground
            ),
            darkCustomWallpaperUri = json.optString("darkCustomWallpaperUri").takeIf { it.isNotBlank() },
            wallpaperFit = enumOrDefault(json.optString("wallpaperFit"), defaults.wallpaperFit),
            wallpaperDim = enumOrDefault(json.optString("wallpaperDim"), defaults.wallpaperDim),
            wallpaperBlur = enumOrDefault(json.optString("wallpaperBlur"), defaults.wallpaperBlur),
            tabLayout = enumOrDefault(json.optString("tabLayout"), defaults.tabLayout),
            tabPreviewSize = enumOrDefault(json.optString("tabPreviewSize"), defaults.tabPreviewSize),
            uiDensity = enumOrDefault(json.optString("uiDensity"), defaults.uiDensity),
            adBlockingEnabled = json.optBoolean("adBlockingEnabled", defaults.adBlockingEnabled),
            darkWebsitesEnabled = json.optBoolean("darkWebsitesEnabled", defaults.darkWebsitesEnabled),
            historyEnabled = json.optBoolean("historyEnabled", defaults.historyEnabled),
            restoreTabs = json.optBoolean("restoreTabs", defaults.restoreTabs),
            downloadsOverMetered = json.optBoolean("downloadsOverMetered", defaults.downloadsOverMetered),
            desktopMode = json.optBoolean("desktopMode", defaults.desktopMode),
            textScale = json.optDouble("textScale", defaults.textScale.toDouble())
                .toFloat()
                .coerceIn(0.85f, 1.30f),
            httpsOnly = json.optBoolean("httpsOnly", defaults.httpsOnly),
            thirdPartyCookieIsolation = json.optBoolean(
                "thirdPartyCookieIsolation",
                defaults.thirdPartyCookieIsolation
            ),
            globalPrivacyControl = json.optBoolean("globalPrivacyControl", defaults.globalPrivacyControl),
            clearPrivateDataOnExit = json.optBoolean(
                "clearPrivateDataOnExit",
                defaults.clearPrivateDataOnExit
            )
        )
    }

    private fun decodeCustomSearchEngines(array: JSONArray?): List<CustomSearchEngine> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val engine = normalizeCustomSearchEngine(
                    CustomSearchEngine(
                        id = item.optString("id"),
                        displayName = item.optString("name"),
                        queryUrlTemplate = item.optString("url")
                    )
                ) ?: continue
                if (none { it.id == engine.id }) add(engine)
                if (size >= MAX_CUSTOM_SEARCH_ENGINES) break
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
