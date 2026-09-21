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

    @Test
    fun logicalRevisionWinsWhenDeviceClockIsWrong() {
        val snapshot = SyncSnapshot(
            schemaVersion = 1,
            revision = 12L,
            updatedAtEpochMs = 1L,
            deviceId = "remote",
            payload = "newer"
        )

        assertTrue(SyncOrderingPolicy.isRemoteNewer(snapshot, 11L, "old"))
        assertFalse(
            SyncOrderingPolicy.isRemoteNewer(
                snapshot.copy(revision = 10L, updatedAtEpochMs = Long.MAX_VALUE),
                11L,
                "old"
            )
        )
    }

    @Test
    fun changedSnapshotWithSameRevisionIsNotHiddenByClockTie() {
        val first = SyncSnapshot(1, 4L, 100L, "one", "first")
        val second = first.copy(updatedAtEpochMs = 1L, deviceId = "two", payload = "second")

        val firstFingerprint = SyncOrderingPolicy.fingerprint(first)
        assertFalse(SyncOrderingPolicy.isRemoteNewer(first, 4L, firstFingerprint))
        assertTrue(SyncOrderingPolicy.isRemoteNewer(second, 4L, firstFingerprint))
    }
}
