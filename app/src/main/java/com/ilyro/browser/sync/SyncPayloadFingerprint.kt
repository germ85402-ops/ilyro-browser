package com.ilyro.browser.sync

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object SyncPayloadFingerprint {
    fun of(payload: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(payload.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal object SyncConflictPolicy {
    fun hasLocalChanges(
        lastSyncedFingerprint: String?,
        currentFingerprint: String?
    ): Boolean = !lastSyncedFingerprint.isNullOrBlank() &&
        !currentFingerprint.isNullOrBlank() &&
        lastSyncedFingerprint != currentFingerprint
}

/**
 * Orders remote snapshots using the logical revision, not a device wall clock. Android devices
 * can have stale or manually adjusted clocks; a newer wall-clock value must not overwrite a
 * snapshot that has a higher logical revision.
 */
internal object SyncOrderingPolicy {
    fun fingerprint(snapshot: SyncSnapshot): String = SyncPayloadFingerprint.of(
        buildString {
            append(snapshot.revision)
            append('\u0000')
            append(snapshot.deviceId)
            append('\u0000')
            append(snapshot.payload)
        }
    )

    fun isRemoteNewer(
        snapshot: SyncSnapshot,
        lastRevision: Long,
        lastFingerprint: String?
    ): Boolean {
        if (lastRevision == Long.MIN_VALUE) return true
        if (snapshot.revision > lastRevision) return true
        if (snapshot.revision < lastRevision) return false
        return fingerprint(snapshot) != lastFingerprint
    }
}
