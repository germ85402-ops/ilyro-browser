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
