package com.ilyro.browser.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictPolicyTest {
    @Test
    fun missingBaselineDoesNotBlockRemoteSettings() {
        assertFalse(SyncConflictPolicy.hasLocalChanges(null, "current"))
        assertFalse(SyncConflictPolicy.hasLocalChanges("", "current"))
        assertFalse(SyncConflictPolicy.hasLocalChanges("baseline", null))
    }

    @Test
    fun changedControlPayloadProtectsLocalValues() {
        assertFalse(SyncConflictPolicy.hasLocalChanges("same", "same"))
        assertTrue(SyncConflictPolicy.hasLocalChanges("old", "new"))
    }
}
