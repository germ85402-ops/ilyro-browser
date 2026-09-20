package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvExporterTest {
    @Test
    fun exportsChromiumCompatibleHeaderAndFields() {
        val csv = PasswordCsvExporter.encode(
            listOf(
                PasswordCredential(
                    guid = "1",
                    origin = "https://example.com",
                    formActionOrigin = null,
                    httpRealm = null,
                    username = "alex",
                    password = "secret"
                )
            )
        )

        assertEquals(
            "name,url,username,password\n" +
                "example.com,https://example.com,alex,secret\n",
            csv
        )
    }

    @Test
    fun quotesCommasQuotesAndNewlinesWithoutLosingSecrets() {
        val csv = PasswordCsvExporter.encode(
            listOf(
                PasswordCredential(
                    guid = "2",
                    origin = "https://accounts.example.com",
                    formActionOrigin = null,
                    httpRealm = null,
                    username = "a,b\"c",
                    password = "line1\nline2,\"quoted\""
                )
            )
        )

        assertTrue(csv.contains("\"a,b\"\"c\""))
        assertTrue(csv.contains("\"line1\nline2,\"\"quoted\"\"\""))
    }

    @Test
    fun emptyVaultExportsOnlyHeader() {
        assertEquals(
            "name,url,username,password\n",
            PasswordCsvExporter.encode(emptyList())
        )
    }
}
