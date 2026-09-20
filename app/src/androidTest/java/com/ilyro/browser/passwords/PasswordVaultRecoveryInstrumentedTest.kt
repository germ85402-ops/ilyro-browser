package com.ilyro.browser.passwords

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordVaultRecoveryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var vaultDirectory: File
    private lateinit var vaultFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vaultDirectory = File(context.filesDir, "passwords")
        vaultFile = File(vaultDirectory, "ilyro-passwords.v1")
        cleanVaultFiles()
    }

    @After
    fun tearDown() {
        cleanVaultFiles()
    }

    @Test
    fun corruptedVaultIsQuarantinedAndFutureSaveRecovers() {
        val vault = PasswordVault(context)
        vault.upsert(
            PasswordCredential(
                guid = "first",
                origin = "https://example.com",
                formActionOrigin = null,
                httpRealm = null,
                username = "alex",
                password = "before-corruption"
            )
        )
        assertEquals(1, vault.snapshot().size)
        assertTrue(vaultFile.exists())

        vaultFile.writeBytes("not-an-ilyro-vault".toByteArray())

        assertTrue(vault.snapshot().isEmpty())
        assertFalse(vaultFile.exists())
        assertTrue(
            vaultDirectory.listFiles().orEmpty().any { file ->
                file.name.startsWith("ilyro-passwords.v1.corrupt-")
            }
        )

        vault.upsert(
            PasswordCredential(
                guid = "second",
                origin = "https://example.org",
                formActionOrigin = null,
                httpRealm = null,
                username = "recovered",
                password = "after-corruption"
            )
        )

        val recovered = vault.snapshot()
        assertEquals(1, recovered.size)
        assertEquals("https://example.org", recovered.single().origin)
        assertEquals("recovered", recovered.single().username)
        assertTrue(vaultFile.exists())
    }

    private fun cleanVaultFiles() {
        vaultDirectory.mkdirs()
        vaultDirectory.listFiles().orEmpty()
            .filter { file -> file.name.startsWith("ilyro-passwords.v1") }
            .forEach { it.delete() }
    }
}
