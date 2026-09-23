package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserScreenSupportTest {
    @Test
    fun normalizeAddressAcceptsCaseInsensitiveWebSchemes() {
        assertEquals(
            "https://example.com/path",
            normalizeAddress("HTTPS://example.com/path", SearchEngine.GOOGLE)
        )
        assertEquals(
            "http://example.com/path",
            normalizeAddress("HTTP://example.com/path", SearchEngine.GOOGLE)
        )
    }

    @Test
    fun normalizeAddressRecognizesDomainsAndLocalDevelopmentHosts() {
        assertEquals(
            "https://example.com:8443/path",
            normalizeAddress("example.com:8443/path", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://localhost:3000",
            normalizeAddress("localhost:3000", SearchEngine.GOOGLE)
        )
    }

    @Test
    fun normalizeAddressKeepsNaturalLanguageAsSearch() {
        val result = normalizeAddress("best cafes in Tallinn", SearchEngine.GOOGLE)
        assertEquals(true, result.startsWith(SearchEngine.GOOGLE.queryUrl))
    }
}
