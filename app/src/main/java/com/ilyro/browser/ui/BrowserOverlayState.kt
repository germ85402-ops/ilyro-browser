package com.ilyro.browser.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

internal enum class BrowserOverlay {
    TABS,
    MENU,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    MEDIA,
    SETTINGS,
    PROTECTION,
    EXTENSIONS,
    FIND_IN_PAGE,
    TRANSLATION
}

/**
 * Single source of truth for ILYRO's primary browser overlays.
 * Opening one primary surface automatically closes the previous one, preventing
 * contradictory combinations such as Settings + Tabs or Downloads + Menu.
 */
internal class BrowserOverlayState {
    private val activeState = mutableStateOf<BrowserOverlay?>(null)

    val active: BrowserOverlay?
        get() = activeState.value

    fun flag(kind: BrowserOverlay): MutableState<Boolean> = BrowserOverlayFlag(this, kind)

    internal fun isActive(kind: BrowserOverlay): Boolean = activeState.value == kind

    internal fun setActive(kind: BrowserOverlay, value: Boolean) {
        if (value) {
            activeState.value = kind
        } else if (activeState.value == kind) {
            activeState.value = null
        }
    }

    fun closeAll() {
        activeState.value = null
    }
}

/**
 * Compose-native mutable state backed by the single active overlay. This keeps local
 * `var ... by ...` declarations on Compose's standard delegate path while preserving
 * the rule that only one primary browser overlay may be open at a time.
 */
internal class BrowserOverlayFlag(
    private val owner: BrowserOverlayState,
    private val kind: BrowserOverlay
) : MutableState<Boolean> {
    override var value: Boolean
        get() = owner.isActive(kind)
        set(value) = owner.setActive(kind, value)

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { updated ->
        value = updated
    }
}
