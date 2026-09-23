package com.ilyro.browser.ui

import com.ilyro.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(R.drawable.wallpaper_light_dawn_lake, builtInWallpaperResource(HomeBackground.LIGHT_DAWN_LAKE, false))
        assertEquals(R.drawable.wallpaper_light_dawn_lake, builtInWallpaperResource(HomeBackground.LIGHT_DAWN_LAKE, true))
        assertEquals(R.drawable.wallpaper_light_olive_grove, builtInWallpaperResource(HomeBackground.LIGHT_OLIVE_GROVE, false))
        assertEquals(R.drawable.wallpaper_light_olive_grove, builtInWallpaperResource(HomeBackground.LIGHT_OLIVE_GROVE, true))
        assertEquals(R.drawable.wallpaper_light_mediterranean, builtInWallpaperResource(HomeBackground.LIGHT_MEDITERRANEAN, false))
        assertEquals(R.drawable.wallpaper_light_mediterranean, builtInWallpaperResource(HomeBackground.LIGHT_MEDITERRANEAN, true))
        assertEquals(R.drawable.wallpaper_light_ivory_dunes, builtInWallpaperResource(HomeBackground.LIGHT_IVORY_DUNES, false))
        assertEquals(R.drawable.wallpaper_light_ivory_dunes, builtInWallpaperResource(HomeBackground.LIGHT_IVORY_DUNES, true))
        assertEquals(R.drawable.wallpaper_light_spring_lake, builtInWallpaperResource(HomeBackground.LIGHT_SPRING_LAKE, false))
        assertEquals(R.drawable.wallpaper_light_spring_lake, builtInWallpaperResource(HomeBackground.LIGHT_SPRING_LAKE, true))
        assertEquals(R.drawable.wallpaper_light_limestone, builtInWallpaperResource(HomeBackground.LIGHT_LIMESTONE, false))
        assertEquals(R.drawable.wallpaper_light_limestone, builtInWallpaperResource(HomeBackground.LIGHT_LIMESTONE, true))
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
                HomeBackground.LIGHT_DAWN_LAKE,
                HomeBackground.LIGHT_OLIVE_GROVE,
                HomeBackground.LIGHT_MEDITERRANEAN,
                HomeBackground.LIGHT_IVORY_DUNES,
                HomeBackground.LIGHT_SPRING_LAKE,
                HomeBackground.LIGHT_LIMESTONE,
                HomeBackground.CUSTOM
            ),
            HomeBackground.entries.toSet()
        )
        assertNull(builtInWallpaperResource(HomeBackground.NONE, false))
        assertNull(builtInWallpaperResource(HomeBackground.CUSTOM, true))
    }

    @Test
    fun lightAndDarkPickersEachOfferSixWallpapersPlusNoWallpaperAndCustom() {
        val lightOptions = wallpaperPresetOptions(darkTheme = false)
        val darkOptions = wallpaperPresetOptions(darkTheme = true)

        assertEquals(6, lightOptions.count { it in LIGHT_WALLPAPER_PRESETS })
        assertEquals(6, darkOptions.count { it in DARK_WALLPAPER_PRESETS })
        assertEquals(HomeBackground.NONE, lightOptions.first())
        assertEquals(HomeBackground.CUSTOM, lightOptions.last())
        assertEquals(HomeBackground.NONE, darkOptions.first())
        assertEquals(HomeBackground.CUSTOM, darkOptions.last())
        assertFalse(HomeBackground.LIGHT_DAWN_LAKE in darkOptions)
        assertFalse(HomeBackground.STILLWATER in lightOptions)
    }
}
