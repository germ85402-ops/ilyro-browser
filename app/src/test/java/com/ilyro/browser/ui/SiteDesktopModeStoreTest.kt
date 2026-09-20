package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDesktopModeStoreTest {
    @Test
    fun overridePersistsAcrossEquivalentWwwUrls() {
        val prefs = SitePrefs()

        SiteDesktopModeStore.setForUrl(prefs, "https://www.example.com/watch?v=1", true)

        assertTrue(SiteDesktopModeStore.effective(prefs, "https://example.com/other", false))
    }

    @Test
    fun youtubeMobileAndDesktopHostsShareOneOverride() {
        val prefs = SitePrefs()

        SiteDesktopModeStore.setForUrl(prefs, "https://m.youtube.com/watch?v=abc", true)

        assertTrue(SiteDesktopModeStore.effective(prefs, "https://www.youtube.com/watch?v=abc", false))
        assertTrue(SiteDesktopModeStore.effective(prefs, "https://youtube.com/feed/subscriptions", false))

        SiteDesktopModeStore.setForUrl(prefs, "https://www.youtube.com", false)

        assertFalse(SiteDesktopModeStore.effective(prefs, "https://m.youtube.com/shorts/123", true))
    }

    @Test
    fun explicitOffOverridesGlobalDesktopDefault() {
        val prefs = SitePrefs()

        SiteDesktopModeStore.setForUrl(prefs, "https://example.com", false)

        assertFalse(SiteDesktopModeStore.effective(prefs, "https://www.example.com/page", true))
    }

    @Test
    fun replacingOverrideDoesNotLeaveAmbiguousOldValue() {
        val prefs = SitePrefs()

        SiteDesktopModeStore.setForUrl(prefs, "https://example.com", true)
        SiteDesktopModeStore.setForUrl(prefs, "https://www.example.com", false)

        assertFalse(SiteDesktopModeStore.effective(prefs, "https://example.com", true))
    }

    @Test
    fun clearingOverrideFallsBackToGlobalDefault() {
        val prefs = SitePrefs()

        SiteDesktopModeStore.setForUrl(prefs, "https://example.com", false)
        SiteDesktopModeStore.clearForUrl(prefs, "https://www.example.com")

        assertTrue(SiteDesktopModeStore.effective(prefs, "https://example.com", true))
    }
}

private class SitePrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
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
