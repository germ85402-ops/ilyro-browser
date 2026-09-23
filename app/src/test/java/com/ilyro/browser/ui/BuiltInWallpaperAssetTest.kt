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
    }

    @Test
    fun builtInOptionsContainOnlyTheNewThemes() {
        assertEquals(
            setOf(HomeBackground.NONE, HomeBackground.STILLWATER, HomeBackground.PINE_DUSK, HomeBackground.ALPINE_DAWN, HomeBackground.CUSTOM),
            HomeBackground.entries.toSet()
        )
        assertNull(builtInWallpaperResource(HomeBackground.NONE, false))
        assertNull(builtInWallpaperResource(HomeBackground.CUSTOM, true))
    }
}
