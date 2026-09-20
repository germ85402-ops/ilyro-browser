package com.ilyro.browser.sync

import org.json.JSONObject

/** Stable envelope stored in the remote provider. */
internal object SyncSnapshotCodec {
    fun encode(snapshot: SyncSnapshot): String = JSONObject()
        .put("schemaVersion", snapshot.schemaVersion)
        .put("revision", snapshot.revision)
        .put("updatedAtEpochMs", snapshot.updatedAtEpochMs)
        .put("deviceId", snapshot.deviceId)
        .put("payload", snapshot.payload)
        .toString()

    fun decode(raw: String): SyncSnapshot {
        val json = JSONObject(raw)
        return SyncSnapshot(
            schemaVersion = json.getInt("schemaVersion"),
            revision = json.getLong("revision"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            deviceId = json.getString("deviceId"),
            payload = json.getString("payload")
        )
    }
}
