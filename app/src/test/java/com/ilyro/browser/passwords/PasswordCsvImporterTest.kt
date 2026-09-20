package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvImporterTest {
    @Test
    fun parsesChromiumCsvWithQuotedFieldsAndDeduplicates() {
        val csv = listOf(
            "name,url,username,password,note",
            "Example,https://example.com/login,alex,first,",
            "Example,https://example.com/other,alex,second,\"note, with comma\"",
            "Other,https://other.example:8443/sign-in,\"user@example.com\",\"p,\"\"ass\"\"\","
        ).joinToString("\n")

        val preview = PasswordCsvImporter.preview(csv)

        assertNull(preview.error)
        assertTrue(preview.canImport)
        assertEquals(3, preview.sourceRows)
        assertEquals(0, preview.skippedRows)
        assertEquals(1, preview.duplicateRows)
        assertEquals(2, preview.credentials.size)

        val example = preview.credentials.first { it.origin == "https://example.com" }
        assertEquals("alex", example.username)
        assertEquals("second", example.password)

        val other = preview.credentials.first { it.origin == "https://other.example:8443" }
        assertEquals("user@example.com", other.username)
        assertEquals("p,\"ass\"", other.password)
    }

    @Test
    fun parsesFirefoxColumnsAndKeepsRealmAndFormAction() {
        val csv = listOf(
            "url,username,password,httpRealm,formActionOrigin,guid,timeCreated",
            "https://accounts.example.com/path,alex,secret,,https://accounts.example.com/login,abc,1",
            "https://router.example/,admin,router-pass,Router Realm,,,2"
        ).joinToString("\n")

        val preview = PasswordCsvImporter.preview(csv)

        assertNull(preview.error)
        assertEquals(2, preview.credentials.size)
        assertEquals(
            "https://accounts.example.com",
            preview.credentials[0].origin
        )
        assertEquals(
            "https://accounts.example.com",
            preview.credentials[0].formActionOrigin
        )
        assertEquals("Router Realm", preview.credentials[1].httpRealm)
    }

    @Test
    fun rejectsNonHttpOriginsAndBlankPasswords() {
        val csv = listOf(
            "url,username,password",
            "javascript:alert(1),bad,secret",
            "file:///tmp/passwords.txt,local,secret",
            "https://good.example,user,",
            "https://valid.example,user,secret"
        ).joinToString("\n")

        val preview = PasswordCsvImporter.preview(csv)

        assertEquals(4, preview.sourceRows)
        assertEquals(3, preview.skippedRows)
        assertEquals(1, preview.credentials.size)
        assertEquals("https://valid.example", preview.credentials.single().origin)
    }

    @Test
    fun canonicalOriginDropsPathsAndDefaultPortsButPreservesExplicitPort() {
        assertEquals(
            "https://example.com",
            PasswordCsvImporter.canonicalOrigin("HTTPS://EXAMPLE.COM:443/a/b?x=1")
        )
        assertEquals(
            "http://example.com",
            PasswordCsvImporter.canonicalOrigin("http://example.com:80/login")
        )
        assertEquals(
            "https://example.com:8443",
            PasswordCsvImporter.canonicalOrigin("https://example.com:8443/login")
        )
        assertNull(PasswordCsvImporter.canonicalOrigin("ftp://example.com/file"))
    }

    @Test
    fun unsupportedHeaderReturnsErrorWithoutImporting() {
        val preview = PasswordCsvImporter.preview("site,login,secret\nexample.com,alex,pw")

        assertFalse(preview.canImport)
        assertTrue(preview.credentials.isEmpty())
        assertTrue(preview.error?.contains("URL") == true)
    }
}
