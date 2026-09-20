package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordVaultCodecTest {
    @Test
    fun codecRoundTripPreservesCredentialFields() {
        val original = listOf(
            PasswordCredential(
                guid = "guid-1",
                origin = "https://example.com",
                formActionOrigin = "https://example.com/login",
                httpRealm = null,
                username = "alex@example.com",
                password = "p@ss,word\nwith unicode ✓",
                timesUsed = 7,
                lastUsedAt = 123456789L
            ),
            PasswordCredential(
                guid = "guid-2",
                origin = "https://accounts.example.org",
                formActionOrigin = null,
                httpRealm = "Restricted",
                username = "",
                password = "secret"
            )
        )

        val restored = PasswordVaultCodec.decode(PasswordVaultCodec.encode(original))

        assertEquals(original, restored)
        assertNull(restored.first().httpRealm)
        assertNull(restored.last().formActionOrigin)
    }

    @Test
    fun codecRejectsMalformedOrUnsupportedVaultData() {
        assertThrows(Exception::class.java) {
            PasswordVaultCodec.decode("not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultCodec.decode("{\"version\":99,\"credentials\":[]}")
        }
    }

    @Test
    fun domainMatcherNeverLeaksAcrossSiblingOrParentDomains() {
        assertTrue(passwordDomainMatches("https://accounts.example.com", "accounts.example.com"))
        assertTrue(passwordDomainMatches("https://accounts.example.com", "https://accounts.example.com"))
        assertFalse(passwordDomainMatches("https://accounts.example.com", "example.com"))
        assertFalse(passwordDomainMatches("https://accounts.example.com", "shop.example.com"))
        assertFalse(passwordDomainMatches("https://example.com", "evil-example.com"))
    }

    @Test
    fun domainMatcherNormalizesCaseAndTrailingDotOnly() {
        assertTrue(passwordDomainMatches("https://EXAMPLE.com", "example.COM."))
        assertFalse(passwordDomainMatches("https://www.example.com", "example.com"))
    }
}
