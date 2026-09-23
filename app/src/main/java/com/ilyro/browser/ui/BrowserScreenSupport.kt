package com.ilyro.browser.ui

import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Small view and address helpers used by the browser screen. */
internal fun requestHighFrameRateTree(view: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)

    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            requestHighFrameRateTree(view.getChildAt(index))
        }
    }
}

internal fun siteHost(url: String): String {
    if (url == HOME_URL) return ""
    return runCatching {
        Uri.parse(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
}

internal fun hostLabel(url: String): String {
    if (url == HOME_URL) return "ILYRO Home"

    return siteHost(url).ifBlank { "New tab" }
}

internal fun normalizeAddress(
    input: String,
    searchEngine: SearchEngine,
    customSearchEngine: CustomSearchEngine? = null
): String {
    val value = input.trim()

    if (value.isEmpty()) return HOME_URL
    val explicitWebScheme = Regex("^(https?)://", RegexOption.IGNORE_CASE).find(value)
    if (explicitWebScheme != null) {
        val scheme = explicitWebScheme.groupValues[1].lowercase()
        return scheme + "://" + value.substring(explicitWebScheme.range.last + 1)
    }
    if (looksLikeNavigation(value)) return "https://$value"

    val query = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    val queryTemplate = customSearchEngine?.queryUrlTemplate
    return if (queryTemplate != null) {
        queryTemplate.replace("%s", query)
    } else {
        searchEngine.queryUrl + query
    }
}
