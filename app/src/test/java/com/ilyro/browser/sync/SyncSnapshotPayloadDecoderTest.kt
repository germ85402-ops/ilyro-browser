package com.ilyro.browser.sync

import com.ilyro.browser.ui.BrowserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SyncSnapshotPayloadDecoderTest {
    @Test
    fun decode_legacySettingsSnapshot_keepsSchemaOneCompatible() {
        val settings = BrowserSettings(desktopMode = true)
        val restored = SyncSnapshotPayloadDecoder.decode(snapshot(
            schemaVersion = BrowserSettingsSyncCodec.SCHEMA_VERSION,
            payload = BrowserSettingsSyncCodec.encode(settings)
        ))

        assertEquals(settings, restored.settings)
        assertNull(restored.history)
        assertNull(restored.bookmarks)
        assertNull(restored.quickLinks)
        assertNull(restored.tabs)
    }

    @Test
    fun decode_futureSchemaRejectsBeforeSyncCanOverwriteRemote() {
        try {
            SyncSnapshotPayloadDecoder.decode(snapshot(schemaVersion = 999, payload = "{}"))
            fail("A future schema must not be treated as an empty remote snapshot.")
        } catch (error: IllegalArgumentException) {
            assertNotNull(error.message)
        }
    }

    @Test
    fun decode_incompleteCurrentSchemaRejectsMissingCollections() {
        val payload = """{"settings": ${BrowserSettingsSyncCodec.encode(BrowserSettings())}}"""
        try {
            SyncSnapshotPayloadDecoder.decode(snapshot(
                schemaVersion = BrowserDataSyncCodec.SCHEMA_VERSION,
                payload = payload
            ))
            fail("Missing schema-v2 collections must not decode as empty data.")
        } catch (error: org.json.JSONException) {
            assertNotNull(error.message)
        }
    }

    private fun snapshot(schemaVersion: Int, payload: String) = SyncSnapshot(
        schemaVersion = schemaVersion,
        revision = 1L,
        updatedAtEpochMs = 1L,
        deviceId = "test",
        payload = payload
    )
}
