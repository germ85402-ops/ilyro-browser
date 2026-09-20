package com.ilyro.browser.ui

import android.content.SharedPreferences

internal data class SiteProtectionStats(
    val networkBlocked: Int = 0,
    val hiddenElements: Int = 0
)

/**
 * Per-site exceptions belonged to the old custom filtering engine.
 * uBlock Origin is now the single blocking engine, so legacy exceptions are ignored.
 */
internal object SiteProtectionStore {
    fun restore(prefs: SharedPreferences): Set<String> = emptySet()

    fun save(prefs: SharedPreferences, disabledHosts: Set<String>) = Unit
}
