package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserFaviconPolicyTest {
    @Test
    fun iconUrlStripsPagePathQueryAndFragment() {
        assertEquals(
            "https://example.com/favicon.ico",
            BrowserFaviconPolicy.faviconUrl("https://example.com/private/page?q=secret#section")
        )
    }

    @Test
    fun iconUrlPreservesNonDefaultPort() {
        assertEquals(
            "https://example.com:8080/favicon.ico",
            BrowserFaviconPolicy.faviconUrl("http://example.com:8080/private")
        )
    }

    @Test
    fun iconUrlRejectsNonWebAndCredentialBearingUrls() {
        assertNull(BrowserFaviconPolicy.faviconUrl("javascript:alert(1)"))
        assertNull(BrowserFaviconPolicy.faviconUrl("file:///tmp/page.html"))
        assertNull(BrowserFaviconPolicy.faviconUrl("https:///missing-host"))
        assertNull(BrowserFaviconPolicy.faviconUrl("https://user:pass@example.com/path"))
    }

    @Test
    fun redirectMustRemainOnTheSameOrigin() {
        assertEquals(
            "https://example.com/assets/favicon.png",
            BrowserFaviconPolicy.sameOriginRedirect(
                "https://example.com/favicon.ico",
                "/assets/favicon.png"
            )
        )
        assertNull(
            BrowserFaviconPolicy.sameOriginRedirect(
                "https://example.com/favicon.ico",
                "https://www.google.com/favicon.ico"
            )
        )
        assertNull(
            BrowserFaviconPolicy.sameOriginRedirect(
                "https://example.com/favicon.ico",
                "http://example.com/favicon.ico"
            )
        )
        assertNull(
            BrowserFaviconPolicy.sameOriginRedirect(
                "https://example.com:8443/favicon.ico",
                "https://example.com:9443/favicon.ico"
            )
        )
    }
}
