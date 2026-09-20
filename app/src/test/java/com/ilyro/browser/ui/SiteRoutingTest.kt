package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteRoutingTest {
    @Test
    fun mobileModeRoutesCanonicalYouTubeToMobileFrontend() {
        assertEquals(
            "https://m.youtube.com/watch?v=abc123&t=12#player",
            preferredYouTubeUrl(
                "https://www.youtube.com/watch?v=abc123&t=12#player",
                desktopMode = false
            )
        )
        assertEquals(
            "https://m.youtube.com/playlist?list=PL123",
            preferredYouTubeUrl("https://youtube.com/playlist?list=PL123", desktopMode = false)
        )
    }

    @Test
    fun desktopModeRoutesMobileYouTubeBackToWww() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc123",
            preferredYouTubeUrl("https://m.youtube.com/watch?v=abc123", desktopMode = true)
        )
    }

    @Test
    fun alreadyPreferredAndOtherYouTubeSubdomainsAreUntouched() {
        assertNull(preferredYouTubeUrl("https://m.youtube.com/", desktopMode = false))
        assertNull(preferredYouTubeUrl("https://www.youtube.com/", desktopMode = true))
        assertNull(preferredYouTubeUrl("https://music.youtube.com/", desktopMode = false))
        assertNull(preferredYouTubeUrl("https://youtu.be/abc123", desktopMode = false))
    }
}

