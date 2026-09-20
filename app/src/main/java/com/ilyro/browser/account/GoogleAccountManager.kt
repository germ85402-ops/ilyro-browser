package com.ilyro.browser.account

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ilyro.browser.R
import java.security.SecureRandom

/** Lightweight profile data that is safe to keep locally between app launches. */
data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)

sealed interface GoogleSignInResult {
    data class Success(val profile: GoogleAccountProfile) : GoogleSignInResult
    data object NotConfigured : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoGoogleAccount : GoogleSignInResult
    data object UnsupportedCredential : GoogleSignInResult
    data object InvalidCredential : GoogleSignInResult
    data class Failure(val message: String?) : GoogleSignInResult
}

/**
 * Owns Google identity state only. Drive authorization deliberately lives outside this class.
 * That separation lets ILYRO replace Google Drive with another sync provider later without
 * changing account UI or browser internals.
 */
class GoogleAccountManager(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = serverClientId().isNotBlank()

    fun currentProfile(): GoogleAccountProfile? {
        val uniqueId = prefs.getString(KEY_UNIQUE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val email = prefs.getString(KEY_EMAIL, null).orEmpty()
        return GoogleAccountProfile(
            uniqueId = uniqueId,
            email = email,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        )
    }

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        val clientId = serverClientId()
        if (clientId.isBlank()) return GoogleSignInResult.NotConfigured

        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(generateSecureRandomNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = response.credential
            if (
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleSignInResult.UnsupportedCredential
            } else {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val profile = GoogleAccountProfile(
                    uniqueId = googleCredential.uniqueId,
                    email = googleCredential.email.orEmpty(),
                    displayName = googleCredential.displayName,
                    photoUrl = googleCredential.profilePictureUri?.toString()
                )
                persist(profile)
                GoogleSignInResult.Success(profile)
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoGoogleAccount
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.InvalidCredential
        } catch (error: GetCredentialException) {
            GoogleSignInResult.Failure(error.message)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } finally {
            prefs.edit().clear().apply()
        }
    }

    private fun persist(profile: GoogleAccountProfile) {
        prefs.edit()
            .putString(KEY_UNIQUE_ID, profile.uniqueId)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_PHOTO_URL, profile.photoUrl)
            .apply()
    }

    private fun serverClientId(): String =
        appContext.getString(R.string.google_web_client_id).trim()

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private companion object {
        const val PREFS_NAME = "ilyro_google_account"
        const val KEY_UNIQUE_ID = "unique_id"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHOTO_URL = "photo_url"
    }
}
