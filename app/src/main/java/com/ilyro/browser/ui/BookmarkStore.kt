package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class BookmarkItem(
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val folder: String? = null
)

internal object BookmarkStore {
    private const val KEY_BOOKMARKS = "bookmarks_v1"
    private const val KEY_EXTERNAL_IMPORT_REFRESH = "bookmarks_external_import_refresh_v1"

    fun restore(prefs: SharedPreferences): List<BookmarkItem> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    if (url.isBlank()) continue
                    val title = item.optString("title").trim().ifBlank { url }
                    val createdAt = item.optLong("createdAt", 0L)
                        .takeIf { it > 0L } ?: System.currentTimeMillis()
                    val folder = item.optString("folder")
                        .trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                    add(
                        BookmarkItem(
                            url = url,
                            title = title,
                            createdAt = createdAt,
                            folder = folder
                        )
                    )
                }
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, bookmarks: List<BookmarkItem>) {
        val array = JSONArray()
        bookmarks.forEach { bookmark ->
            array.put(
                JSONObject()
                    .put("url", bookmark.url)
                    .put("title", bookmark.title)
                    .put("createdAt", bookmark.createdAt)
                    .put("folder", bookmark.folder)
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    fun mergeExternalImport(
        prefs: SharedPreferences,
        imported: Collection<BookmarkItem>
    ): Int {
        if (imported.isEmpty()) return 0
        val existing = restore(prefs)
        val merged = LinkedHashMap<String, BookmarkItem>()
        existing.forEach { bookmark -> merged[bookmark.url] = bookmark }

        var added = 0
        imported.forEach { bookmark ->
            if (!merged.containsKey(bookmark.url)) {
                added += 1
                merged[bookmark.url] = bookmark
            }
        }

        if (added > 0) {
            save(prefs, merged.values.toList())
            prefs.edit().putBoolean(KEY_EXTERNAL_IMPORT_REFRESH, true).apply()
        }
        return added
    }

    fun consumeExternalImportRefresh(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(KEY_EXTERNAL_IMPORT_REFRESH, false)) return false
        prefs.edit().remove(KEY_EXTERNAL_IMPORT_REFRESH).apply()
        return true
    }
}
