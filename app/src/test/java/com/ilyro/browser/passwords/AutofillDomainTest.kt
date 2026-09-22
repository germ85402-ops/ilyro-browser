package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillDomainTest {
    @Test
    fun autofillDomainAcceptsFieldsThatAgree() {
        assertEquals("example.com", autofillDomainFor("example.com", "example.com"))
        assertEquals("example.com", autofillDomainFor("example.com", null))
        assertEquals("example.com", autofillDomainFor(null, "example.com"))
        assertEquals("example.com", autofillDomainFor("example.com", "EXAMPLE.COM"))
    }

    @Test
    fun autofillDomainIsRefusedForCrossOriginFields() {
        assertNull(autofillDomainFor("ads.tracker.test", "example.com"))
    }

    @Test
    fun autofillDomainIsRefusedWithoutAnyDomain() {
        assertNull(autofillDomainFor(null, null))
        assertNull(autofillDomainFor("  ", ""))
    }
}
