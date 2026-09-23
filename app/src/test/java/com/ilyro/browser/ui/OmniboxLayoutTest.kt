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

    @Test
    fun popupExpandsToTheAvailableSpaceOnEitherSideOfTheField() {
        assertEquals(
            272,
            calculateOmniboxPopupAvailableHeight(
                placeAbove = true,
                anchorTop = 280,
                anchorBottom = 336,
                windowHeight = 900,
                imeBottom = 360,
                verticalGap = 8
            )
        )
        assertEquals(
            196,
            calculateOmniboxPopupAvailableHeight(
                placeAbove = false,
                anchorTop = 280,
                anchorBottom = 336,
                windowHeight = 900,
                imeBottom = 360,
                verticalGap = 8
            )
        )
    }

    @Test
    fun suggestionsStayInsideSystemBarsAndAboveTheKeyboard() {
        val windowHeight = 900
        val statusBar = 28
        val navigationBar = 24
        val ime = 320

        val belowTopToolbar = calculateOmniboxPopupAvailableHeight(
            placeAbove = false,
            anchorTop = 104,
            anchorBottom = 160,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = 8,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        assertEquals(412, belowTopToolbar)

        val aboveBottomToolbar = calculateOmniboxPopupAvailableHeight(
            placeAbove = true,
            anchorTop = 500,
            anchorBottom = 556,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = 8,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        assertEquals(464, aboveBottomToolbar)

        val y = calculateOmniboxPopupY(
            placeAbove = true,
            anchorTop = 500,
            anchorBottom = 556,
            popupHeight = aboveBottomToolbar,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = 8,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        assertEquals(statusBar, y)
        assert(y + aboveBottomToolbar <= windowHeight - ime)
    }

    @Test
    fun hiddenKeyboardStillKeepsSuggestionsAboveNavigationBar() {
        assertEquals(
            708,
            calculateOmniboxPopupAvailableHeight(
                placeAbove = false,
                anchorTop = 120,
                anchorBottom = 160,
                windowHeight = 900,
                imeBottom = 0,
                verticalGap = 8,
                safeTopInset = 28,
                navigationBarBottomInset = 24
            )
        )
    }

    @Test
    fun phonePortraitKeepsBothToolbarLayoutsBetweenFieldAndKeyboard() {
        // A 720 dp tall phone window at roughly 3.3 px/dp, with the keyboard open.
        val windowHeight = 2400
        val statusBar = 72
        val navigationBar = 72
        val ime = 840
        val gap = 24
        val safeBottom = windowHeight - ime

        val belowTopToolbar = calculateOmniboxPopupAvailableHeight(
            placeAbove = false,
            anchorTop = 260,
            anchorBottom = 410,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = gap,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        val topY = calculateOmniboxPopupY(
            placeAbove = false,
            anchorTop = 260,
            anchorBottom = 410,
            popupHeight = belowTopToolbar,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = gap,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        assertEquals(1126, belowTopToolbar)
        assertEquals(434, topY)
        assertEquals(safeBottom, topY + belowTopToolbar)

        val aboveBottomToolbar = calculateOmniboxPopupAvailableHeight(
            placeAbove = true,
            anchorTop = 1350,
            anchorBottom = 1500,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = gap,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        val bottomY = calculateOmniboxPopupY(
            placeAbove = true,
            anchorTop = 1350,
            anchorBottom = 1500,
            popupHeight = aboveBottomToolbar,
            windowHeight = windowHeight,
            imeBottom = ime,
            verticalGap = gap,
            safeTopInset = statusBar,
            navigationBarBottomInset = navigationBar
        )
        assertEquals(1254, aboveBottomToolbar)
        assertEquals(statusBar, bottomY)
        assert(bottomY + aboveBottomToolbar < 1350)
    }
}
