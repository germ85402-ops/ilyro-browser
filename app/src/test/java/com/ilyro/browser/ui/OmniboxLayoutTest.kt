package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OmniboxLayoutTest {
    @Test
    fun bottomToolbarPopupStaysAboveLandscapeIme() {
        val y = calculateOmniboxPopupY(
            placeAbove = true,
            anchorTop = 360,
            anchorBottom = 436,
            popupHeight = 280,
            windowHeight = 800,
            imeBottom = 364,
            verticalGap = 8
        )

        assertEquals(72, y)
        assert(y + 280 <= 800 - 364)
    }

    @Test
    fun popupUsesAvailableTopWhenPreferredSideDoesNotFit() {
        val y = calculateOmniboxPopupY(
            placeAbove = true,
            anchorTop = 40,
            anchorBottom = 90,
            popupHeight = 120,
            windowHeight = 800,
            imeBottom = 300,
            verticalGap = 8
        )

        assertEquals(98, y)
    }
}
