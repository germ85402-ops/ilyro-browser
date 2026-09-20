package com.ilyro.browser.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DriveAuthorizationResult {
    data class Authorized(val accessToken: String) : DriveAuthorizationResult
    data class ResolutionRequired(val pendingIntent: PendingIntent) : DriveAuthorizationResult
    data class Failure(val message: String?) : DriveAuthorizationResult
}

/**
 * Requests the narrow Drive app-data scope only. Access tokens are intentionally returned to the
 * caller and never persisted by this class.
 */
class GoogleDriveAuthorizationManager(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    suspend fun authorize(accountEmail: String?): DriveAuthorizationResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))

        if (!accountEmail.isNullOrBlank()) {
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }

        return try {
            mapResult(client.authorize(builder.build()).await())
        } catch (error: Exception) {
            DriveAuthorizationResult.Failure(error.message)
        }
    }

    fun resolve(data: Intent?): DriveAuthorizationResult {
        if (data == null) return DriveAuthorizationResult.Failure("Google authorization returned no data")
        return try {
            mapResult(client.getAuthorizationResultFromIntent(data))
        } catch (error: Exception) {
            DriveAuthorizationResult.Failure(error.message)
        }
    }

    private fun mapResult(result: AuthorizationResult): DriveAuthorizationResult {
        val token = result.accessToken
        if (!token.isNullOrBlank()) {
            return DriveAuthorizationResult.Authorized(token)
        }

        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            if (pendingIntent != null) {
                return DriveAuthorizationResult.ResolutionRequired(pendingIntent)
            }
        }

        return DriveAuthorizationResult.Failure("Google Drive authorization did not return an access token")
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
