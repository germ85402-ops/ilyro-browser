package com.ilyro.browser.ui

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStateRecoveryTest {
    @Test
    fun runningDirectDownloadIsRecoveredAsPaused() {
        val raw = """[{"id":-10,"sourceUrl":"https://example.com/file.apk","fileName":"file.apk","localUri":"content://downloads/10","directState":1}]"""

        val recovered = DownloadStateRecovery.recoverJson(raw)!!
        val item = JSONArray(recovered.json).getJSONObject(0)

        assertEquals(DownloadStateRecovery.DIRECT_PAUSED, item.getInt("directState"))
        assertEquals(emptyList<String>(), recovered.discardedLocalUris)
    }

    @Test
    fun interruptedHlsDownloadIsDiscardedInsteadOfUnresumablePause() {
        val raw = """[{"id":-11,"sourceUrl":"https://cdn.example.com/video/master.m3u8?token=abc","fileName":"video.mp4","localUri":"content://downloads/11","directState":1}]"""

        val recovered = DownloadStateRecovery.recoverJson(raw)!!

        assertEquals(0, JSONArray(recovered.json).length())
        assertEquals(listOf("content://downloads/11"), recovered.discardedLocalUris)
    }

    @Test
    fun completedDirectDownloadIsLeftUntouched() {
        val raw = """[{"id":-10,"sourceUrl":"https://example.com/file.apk","fileName":"file.apk","localUri":"content://downloads/10","directState":2}]"""

        assertNull(DownloadStateRecovery.recoverJson(raw))
    }

    @Test
    fun downloadManagerRecordIsNotConvertedToPaused() {
        val raw = """[{"id":42,"sourceUrl":"https://example.com/file.zip","fileName":"file.zip","directState":1}]"""

        assertNull(DownloadStateRecovery.recoverJson(raw))
    }

    @Test
    fun malformedPayloadIsIgnoredSafely() {
        assertNull(DownloadStateRecovery.recoverJson("not-json"))
    }
}
