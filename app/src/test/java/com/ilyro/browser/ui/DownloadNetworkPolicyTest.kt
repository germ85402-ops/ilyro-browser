package com.ilyro.browser.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNetworkPolicyTest {
    @Test
    fun disallowedMeteredTransferIsBlocked() {
        assertTrue(shouldBlockMeteredDownload(allowMetered = false, isMetered = true))
    }

    @Test
    fun allowedOrUnmeteredTransferContinues() {
        assertFalse(shouldBlockMeteredDownload(allowMetered = true, isMetered = true))
        assertFalse(shouldBlockMeteredDownload(allowMetered = false, isMetered = false))
    }
}
