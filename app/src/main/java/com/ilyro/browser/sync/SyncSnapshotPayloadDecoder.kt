package com.ilyro.browser.sync

/** Decodes only snapshot schemas this app can safely preserve and merge. */
internal object SyncSnapshotPayloadDecoder {
    fun decode(snapshot: SyncSnapshot): RestoredBrowserData = when (snapshot.schemaVersion) {
        BrowserSettingsSyncCodec.SCHEMA_VERSION -> RestoredBrowserData(
            settings = BrowserSettingsSyncCodec.decode(snapshot.payload)
        )

        BrowserDataSyncCodec.SCHEMA_VERSION -> BrowserDataSyncCodec.decode(snapshot.payload)

        else -> throw IllegalArgumentException(
            "Unsupported ILYRO sync schema ${snapshot.schemaVersion}. Local and cloud data were left unchanged."
        )
    }
}
