package com.ilyro.browser.ui

import com.ilyro.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInWallpaperAssetTest {

    @Test
    fun eachBuiltInThemeSelectsItsMatchingPhoneAndTabletArtwork() {
        assertEquals(R.drawable.wallpaper_phone_stillwater, builtInWallpaperResource(HomeBackground.STILLWATER, false))
        assertEquals(R.drawable.wallpaper_tablet_stillwater, builtInWallpaperResource(HomeBackground.STILLWATER, true))
        assertEquals(R.drawable.wallpaper_phone_pine_dusk, builtInWallpaperResource(HomeBackground.PINE_DUSK, false))
        assertEquals(R.drawable.wallpaper_tablet_pine_dusk, builtInWallpaperResource(HomeBackground.PINE_DUSK, true))
        assertEquals(R.drawable.wallpaper_phone_alpine_dawn, builtInWallpaperResource(HomeBackground.ALPINE_DAWN, false))
        assertEquals(R.drawable.wallpaper_tablet_alpine_dawn, builtInWallpaperResource(HomeBackground.ALPINE_DAWN, true))
        assertEquals(R.drawable.wallpaper_phone_obsidian, builtInWallpaperResource(HomeBackground.OBSIDIAN, false))
        assertEquals(R.drawable.wallpaper_tablet_obsidian, builtInWallpaperResource(HomeBackground.OBSIDIAN, true))
        assertEquals(R.drawable.wallpaper_phone_ink_wash, builtInWallpaperResource(HomeBackground.INK_WASH, false))
        assertEquals(R.drawable.wallpaper_tablet_ink_wash, builtInWallpaperResource(HomeBackground.INK_WASH, true))
        assertEquals(R.drawable.wallpaper_phone_ember_dunes, builtInWallpaperResource(HomeBackground.EMBER_DUNES, false))
        assertEquals(R.drawable.wallpaper_tablet_ember_dunes, builtInWallpaperResource(HomeBackground.EMBER_DUNES, true))
        assertEquals(R.drawable.wallpaper_phone_prism_flow, builtInWallpaperResource(HomeBackground.PRISM_FLOW, false))
        assertEquals(R.drawable.wallpaper_tablet_prism_flow, builtInWallpaperResource(HomeBackground.PRISM_FLOW, true))
    }

    @Test
    fun builtInOptionsContainOnlyTheNewThemes() {
        assertEquals(
            setOf(
                HomeBackground.NONE,
                HomeBackground.STILLWATER,
                HomeBackground.PINE_DUSK,
                HomeBackground.ALPINE_DAWN,
                HomeBackground.OBSIDIAN,
                HomeBackground.INK_WASH,
                HomeBackground.EMBER_DUNES,
                HomeBackground.PRISM_FLOW,
                HomeBackground.CUSTOM
            ),
            HomeBackground.entries.toSet()
        )
        assertNull(builtInWallpaperResource(HomeBackground.NONE, false))
        assertNull(builtInWallpaperResource(HomeBackground.CUSTOM, true))
    }
}
