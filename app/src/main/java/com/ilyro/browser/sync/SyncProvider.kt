package com.ilyro.browser.sync

/**
 * Versioned payload exchanged with a remote sync provider.
 * The payload is intentionally opaque here so browser settings can evolve independently
 * from Google Drive or any future ILYRO-owned backend.
 */
data class SyncSnapshot(
    val schemaVersion: Int,
    val revision: Long,
    val updatedAtEpochMs: Long,
    val deviceId: String,
    val payload: String
)

sealed interface SyncResult<out T> {
    data class Success<T>(val value: T) : SyncResult<T>
    data class Failure(val message: String?, val recoverable: Boolean = true) : SyncResult<Nothing>
    data object NotAuthorized : SyncResult<Nothing>
}

/**
 * Remote sync abstraction. Google Drive will be the first implementation, but browser code
 * should depend on this interface rather than on Drive APIs directly.
 */
interface SyncProvider {
    val id: String

    suspend fun isAvailable(): Boolean

    suspend fun downloadLatest(): SyncResult<SyncSnapshot?>

    suspend fun upload(snapshot: SyncSnapshot): SyncResult<Unit>

    suspend fun deleteRemoteData(): SyncResult<Unit>
}
