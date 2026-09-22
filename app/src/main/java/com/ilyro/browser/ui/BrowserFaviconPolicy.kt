package com.ilyro.browser.ui

import java.net.IDN
import java.net.URI

/** URL rules shared by every favicon surface in the browser. */
internal object BrowserFaviconPolicy {
    fun faviconUrl(siteUrl: String): String? {
        val site = parseHttpUri(siteUrl) ?: return null
        val sourceScheme = site.scheme.lowercase()
        val sourcePort = site.port
        val iconPort = when {
            sourcePort == -1 -> -1
            sourceScheme == "http" && sourcePort == 80 -> -1
            sourceScheme == "https" && sourcePort == 443 -> -1
            else -> sourcePort
        }
        val host = site.host.let { value ->
            if (value.startsWith("[") && value.endsWith("]")) value
            else runCatching { IDN.toASCII(value) }.getOrNull() ?: return null
        }
        return runCatching {
            URI("https", null, host, iconPort, "/favicon.ico", null, null).toASCIIString()
        }.getOrNull()
    }

    fun sameOriginRedirect(currentUrl: String, location: String): String? {
        val current = parseHttpUri(currentUrl) ?: return null
        val target = runCatching { current.resolve(location) }.getOrNull()
            ?.let { parseHttpUri(it.toString()) }
            ?: return null
        if (!current.scheme.equals(target.scheme, ignoreCase = true) ||
            !current.host.equals(target.host, ignoreCase = true) ||
            effectivePort(current) != effectivePort(target)
        ) return null
        return target.toASCIIString()
    }

    private fun parseHttpUri(value: String): URI? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if ((scheme != "http" && scheme != "https") || uri.isOpaque ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null
        ) return null
        return uri
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
