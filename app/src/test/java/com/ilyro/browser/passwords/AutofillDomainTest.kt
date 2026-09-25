package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillDomainTest {
    @Test
    fun autofillOriginAcceptsFieldsThatAgree() {
        assertEquals("https://example.com", autofillOriginFor("https://example.com", "https://example.com"))
        assertEquals("https://example.com", autofillOriginFor("https://example.com", null))
        assertEquals("https://example.com", autofillOriginFor(null, "https://example.com"))
        assertEquals("https://example.com", autofillOriginFor("https://example.com", "https://EXAMPLE.COM"))
        assertEquals("http://example.com", autofillOriginFor("http://example.com", "http://example.com"))
    }

    @Test
    fun autofillOriginIsRefusedForCrossOriginFields() {
        assertNull(autofillOriginFor("https://ads.tracker.test", "https://example.com"))
        assertNull(autofillOriginFor("http://example.com", "https://example.com"))
    }

    @Test
    fun autofillOriginIsRefusedWithoutAnyOrigin() {
        assertNull(autofillOriginFor(null, null))
        assertNull(autofillOriginFor("  ", ""))
    }
}
