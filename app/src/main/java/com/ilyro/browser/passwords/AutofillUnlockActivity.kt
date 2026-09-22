package com.ilyro.browser.passwords

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

/**
 * Confirms the device screen lock before a saved password leaves ILYRO through Autofill.
 *
 * The Autofill dataset offered to another app carries no values. Only after the user passes the
 * device credential check does this activity return the real username and password to the
 * platform, which limits what a hostile app can extract by imitating a website's login form.
 */
internal class AutofillUnlockActivity : Activity() {
    private var credentialGuid: String? = null
    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialGuid = intent.getStringExtra(EXTRA_GUID)
        usernameId = parcelableExtra(EXTRA_USERNAME_ID)
        passwordId = parcelableExtra(EXTRA_PASSWORD_ID)

        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            // Without a screen lock there is nothing to confirm against.
            completeWithCredential()
            return
        }

        val confirmIntent = keyguard.createConfirmDeviceCredentialIntent(
            "ILYRO Passwords",
            "Confirm your screen lock to fill this saved password."
        )
        if (confirmIntent == null) {
            completeWithCredential()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(confirmIntent, REQUEST_CONFIRM_DEVICE_CREDENTIAL)
    }

    @Deprecated("Autofill authentication uses the classic activity result contract.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIRM_DEVICE_CREDENTIAL) return
        if (resultCode == RESULT_OK) {
            completeWithCredential()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun completeWithCredential() {
        val guid = credentialGuid
        val credential = if (guid.isNullOrBlank()) {
            null
        } else {
            runCatching {
                PasswordManagerService.vault(this).snapshot().firstOrNull { it.guid == guid }
            }.onFailure { error ->
                Log.e(TAG, "Unable to read the saved password for Autofill", error)
            }.getOrNull()
        }

        if (credential == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(
                android.R.id.text1,
                credential.username.ifBlank { credential.origin }
            )
        }
        val dataset = Dataset.Builder(presentation).apply {
            usernameId?.let { id -> setValue(id, AutofillValue.forText(credential.username)) }
            passwordId?.let { id -> setValue(id, AutofillValue.forText(credential.password)) }
        }.build()

        runCatching {
            PasswordManagerService.vault(this)
                .markUsed(credential.guid, credential.origin, credential.username)
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        )
        finish()
    }

    private inline fun <reified T> parcelableExtra(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name) as? T
        }

    internal companion object {
        private const val TAG = "ILYRO.Autofill"
        private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL = 4301
        private const val EXTRA_GUID = "com.ilyro.browser.autofill.GUID"
        private const val EXTRA_USERNAME_ID = "com.ilyro.browser.autofill.USERNAME_ID"
        private const val EXTRA_PASSWORD_ID = "com.ilyro.browser.autofill.PASSWORD_ID"

        fun intentFor(
            context: Context,
            guid: String,
            usernameId: AutofillId?,
            passwordId: AutofillId?
        ): Intent = Intent(context, AutofillUnlockActivity::class.java).apply {
            putExtra(EXTRA_GUID, guid)
            putExtra(EXTRA_USERNAME_ID, usernameId)
            putExtra(EXTRA_PASSWORD_ID, passwordId)
        }
    }
}
