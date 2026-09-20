package com.ilyro.browser.ui

import android.content.SharedPreferences
import android.net.Uri
import java.net.URI

internal object SiteDesktopModeStore {
    private const val KEY_OVERRIDES = "site_desktop_mode_overrides_v1"

    fun effective(
        prefs: SharedPreferences,
        url: String,
        globalDefault: Boolean
    ): Boolean = overrideForUrl(prefs, url) ?: globalDefault

    fun overrideForUrl(prefs: SharedPreferences, url: String): Boolean? {
        val host = hostKey(url) ?: return null
        val entry = prefs.getStringSet(KEY_OVERRIDES, emptySet())
            ?.firstOrNull { it.startsWith("$host=") }
            ?: return null
        return when (entry.substringAfter('=', missingDelimiterValue = "")) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    fun setForUrl(prefs: SharedPreferences, url: String, enabled: Boolean) {
        val host = hostKey(url) ?: return
        val next = prefs.getStringSet(KEY_OVERRIDES, emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$host=") }
            .toMutableSet()
        next += "$host=${if (enabled) 1 else 0}"
        prefs.edit().putStringSet(KEY_OVERRIDES, next).apply()
    }

    fun clearForUrl(prefs: SharedPreferences, url: String) {
        val host = hostKey(url) ?: return
        val next = prefs.getStringSet(KEY_OVERRIDES, emptySet())
            .orEmpty()
            .filterNot { it.startsWith("$host=") }
            .toSet()
        prefs.edit().putStringSet(KEY_OVERRIDES, next).apply()
    }

    /**
     * Prefer the JDK parser for ordinary absolute web URLs so the persistence rules remain easy
     * to exercise in local JVM tests. Gecko may still surface Android-specific/escaped URL forms,
     * so keep android.net.Uri as a tolerant fallback in production.
     *
     * YouTube is special-cased because ILYRO intentionally rewrites between m.youtube.com and
     * www.youtube.com when toggling mobile/desktop presentation. Those hosts must share one
     * persisted override or the rewrite can immediately flip the requested mode back and loop.
     */
    private fun hostKey(url: String): String? {
        val jdkHost = runCatching { URI(url).host }.getOrNull()
        val androidHost = if (jdkHost == null) {
            runCatching { Uri.parse(url).host }.getOrNull()
        } else {
            null
        }
        val host = (jdkHost ?: androidHost)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return when (host) {
            "youtube.com", "www.youtube.com", "m.youtube.com" -> "youtube.com"
            else -> host.removePrefix("www.")
        }
    }
}
