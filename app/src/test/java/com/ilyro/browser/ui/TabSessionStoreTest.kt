package com.ilyro.browser.ui

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSessionStoreTest {
    @Test
    fun v2RoundTripPreservesUrlAndSessionStateAlignment() {
        val urls = listOf("https://example.com/one", "https://example.com/two")
        val states = listOf("{state-one}", null)

        val decoded = TabSessionStore.decodeTabs(TabSessionStore.encodeTabs(urls, states))

        assertEquals(urls, decoded.first)
        assertEquals(states, decoded.second)
    }

    @Test
    fun v2PayloadStoresPinnedAndGroupMetadata() {
        val encoded = TabSessionStore.encodeTabs(
            urls = listOf("https://example.com"),
            states = listOf(null),
            metadata = listOf(TabSessionMetadata(pinned = true, group = "Video"))
        )
        assertTrue(encoded.contains("\"pinned\":true"))
        assertTrue(encoded.contains("\"group\":\"Video\""))
    }

    @Test
    fun legacyUrlOnlyPayloadRestoresNullState() {
        val decoded = TabSessionStore.decodeTabs(
            """[{"url":"https://example.com/legacy"}]"""
        )

        assertEquals(listOf("https://example.com/legacy"), decoded.first)
        assertEquals(listOf<String?>(null), decoded.second)
    }

    @Test
    fun oversizedSessionStateFallsBackToUrlOnly() {
        val encoded = TabSessionStore.encodeTabs(
            urls = listOf("https://example.com/large"),
            states = listOf("x".repeat(300_000))
        )
        val decoded = TabSessionStore.decodeTabs(encoded)

        assertEquals(listOf("https://example.com/large"), decoded.first)
        assertNull(decoded.second.single())
    }

    @Test
    fun restoreFallsBackToLegacyWhenV2IsMalformed() {
        val prefs = TestSharedPreferences(
            mutableMapOf(
                "tabs_v2" to "not-json",
                "tabs_v1" to """[{"url":"https://example.com/legacy"}]""",
                "active_tab_index_v1" to 0
            )
        )

        val restored = TabSessionStore.restore(prefs, "about:blank")

        assertEquals(listOf("https://example.com/legacy"), restored.urls)
        assertEquals(listOf<String?>(null), restored.states)
        assertEquals(0, restored.activeIndex)
    }

    @Test
    fun restoreUsesFallbackAndClampsInvalidActiveIndex() {
        val prefs = TestSharedPreferences(
            mutableMapOf("active_tab_index_v1" to 99)
        )

        val restored = TabSessionStore.restore(prefs, "about:blank")

        assertEquals(listOf("about:blank"), restored.urls)
        assertEquals(0, restored.activeIndex)
    }

    @Test
    fun saveNowClampsActiveIndexAndPreservesMetadata() {
        val prefs = TestSharedPreferences()

        TabSessionStore.saveNow(
            prefs = prefs,
            urls = listOf("https://example.com/a", "https://example.com/b"),
            states = listOf("state-a", "state-b"),
            metadata = listOf(
                TabSessionMetadata(pinned = true, group = "Pinned"),
                TabSessionMetadata(group = "Work")
            ),
            activeIndex = 42
        )

        val restored = TabSessionStore.restore(prefs, "about:blank")
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), restored.urls)
        assertEquals(listOf("state-a", "state-b"), restored.states)
        assertEquals(1, restored.activeIndex)
        assertEquals(TabSessionMetadata(pinned = true, group = "Pinned"), restored.metadata[0])
        assertEquals(TabSessionMetadata(group = "Work"), restored.metadata[1])
    }

    @Test
    fun blankRecordsAreIgnoredWithoutBreakingStateAlignment() {
        val decoded = TabSessionStore.decodeTabs(
            """[{"url":"  "},{"url":"https://example.com/ok","sessionState":"state-ok"}]"""
        )

        assertEquals(listOf("https://example.com/ok"), decoded.first)
        assertEquals(listOf("state-ok"), decoded.second)
    }
}

private class TestSharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        ((values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet())

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val target: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {
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
