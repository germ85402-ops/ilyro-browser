package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class QuickLink(
    val id: String,
    val label: String,
    val url: String
)

internal object QuickLinkStore {
    private const val KEY_QUICK_LINKS = "home_quick_links_v2"
    private const val MAX_LINKS = 12

    private val defaults = listOf(
        QuickLink("youtube", "YouTube", "https://m.youtube.com/"),
        QuickLink("google", "Google", "https://www.google.com/"),
        QuickLink("wikipedia", "Wikipedia", "https://www.wikipedia.org/"),
        QuickLink("github", "GitHub", "https://github.com/"),
        QuickLink("reddit", "Reddit", "https://www.reddit.com/"),
        QuickLink("chatgpt", "ChatGPT", "https://chatgpt.com/")
    )

    fun restore(prefs: SharedPreferences): List<QuickLink> {
        val raw = prefs.getString(KEY_QUICK_LINKS, null) ?: return defaults
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                    val label = item.optString("label").trim()
                    val storedUrl = item.optString("url").trim()
                    if (label.isBlank() || storedUrl.isBlank()) continue
                    val url = if (id == "youtube") {
                        preferredYouTubeUrl(storedUrl, desktopMode = false) ?: storedUrl
                    } else {
                        storedUrl
                    }
                    add(QuickLink(id, label, url))
                    if (size >= MAX_LINKS) break
                }
            }.ifEmpty { defaults }
        }.getOrDefault(defaults)
    }

    fun save(prefs: SharedPreferences, links: List<QuickLink>) {
        val array = JSONArray()
        links.take(MAX_LINKS).forEach { link ->
            array.put(
                JSONObject()
                    .put("id", link.id)
                    .put("label", link.label)
                    .put("url", link.url)
            )
        }
        prefs.edit().putString(KEY_QUICK_LINKS, array.toString()).apply()
    }

    fun newLink(label: String, url: String): QuickLink {
        return QuickLink(UUID.randomUUID().toString(), label.trim(), url.trim())
    }
}


