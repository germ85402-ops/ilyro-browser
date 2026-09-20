package com.ilyro.browser.ui

private val YOUTUBE_HOST_PATTERN =
    Regex("""^(https?://)(?:(www|m)\.)?youtube\.com(?=[:/]|$)""", RegexOption.IGNORE_CASE)

/**
 * Keeps YouTube aligned with ILYRO's global page mode.
 *
 * GeckoView's mobile UA/viewport is not always enough to make YouTube choose its compact mobile
 * frontend on wide Android tablets. Route the canonical YouTube host explicitly: mobile mode uses
 * m.youtube.com, while the user's Desktop site mode restores www.youtube.com.
 *
 * The path, query and fragment are left untouched, so watch URLs, playlists and timestamps survive
 * the host rewrite. Other YouTube subdomains (Music, Studio, Kids, etc.) are intentionally ignored.
 */
internal fun preferredYouTubeUrl(rawUrl: String, desktopMode: Boolean): String? {
    val value = rawUrl.trim()
    val match = YOUTUBE_HOST_PATTERN.find(value) ?: return null
    val currentPrefix = match.groupValues[2].lowercase()
    val wantedPrefix = if (desktopMode) "www" else "m"
    if (currentPrefix == wantedPrefix) return null

    val replacement = "${match.groupValues[1]}$wantedPrefix.youtube.com"
    return YOUTUBE_HOST_PATTERN.replaceFirst(value, replacement)
}

