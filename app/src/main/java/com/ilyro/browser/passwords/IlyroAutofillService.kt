package com.ilyro.browser.passwords

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.IntentSender
import android.os.Build
import android.os.CancellationSignal
import android.view.autofill.AutofillId
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import java.util.UUID

class IlyroAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val candidates = collectCandidates(structure)
        val usernameField = candidates.firstOrNull { isUsernameField(it.node) }
        val passwordField = candidates.firstOrNull { isPasswordField(it.node) }
        if (usernameField == null && passwordField == null) {
            callback.onSuccess(null)
            return
        }

        // Use the origin of the field that is actually being filled. Taking the first web domain
        // found anywhere in the structure would offer a credential to a third-party iframe.
        val origin = autofillOriginFor(passwordField?.origin, usernameField?.origin)
        if (origin.isNullOrBlank()) {
            callback.onSuccess(null)
            return
        }

        val credentials = try {
            PasswordManagerService.vault(this)
                .snapshot()
                .filter { passwordDomainMatches(it.origin, origin) }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read saved passwords for Autofill", error)
            callback.onFailure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Saved passwords are temporarily unavailable. Please try again."
            )
            return
        }
        if (credentials.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val response = FillResponse.Builder()
        val deviceIsSecure = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        credentials.take(MAX_DATASETS).forEachIndexed { index, credential ->
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(
                    android.R.id.text1,
                    credential.username.ifBlank { credential.origin }
                )
            }
            val dataset = Dataset.Builder(presentation)
            // The saved password itself is never handed to the requesting app until the user has
            // confirmed the device screen lock, so a hostile app cannot silently harvest it.
            val authentication = if (deviceIsSecure) {
                unlockIntentSender(
                    credential = credential,
                    usernameId = usernameField?.node?.autofillId,
                    passwordId = passwordField?.node?.autofillId,
                    requestCode = index
                )
            } else {
                null
            }
            when (autofillAuthorizationDecision(deviceIsSecure, authentication != null)) {
                AutofillAuthorizationDecision.REQUIRE_DEVICE_CONFIRMATION -> {
                    val confirmation = authentication ?: return@forEachIndexed
                    usernameField?.node?.autofillId?.let { id -> dataset.setValue(id, null) }
                    passwordField?.node?.autofillId?.let { id -> dataset.setValue(id, null) }
                    dataset.setAuthentication(confirmation)
                }
                AutofillAuthorizationDecision.DIRECT_FILL -> {
                    usernameField?.node?.autofillId?.let { id ->
                        dataset.setValue(id, AutofillValue.forText(credential.username))
                    }
                    passwordField?.node?.autofillId?.let { id ->
                        dataset.setValue(id, AutofillValue.forText(credential.password))
                    }
                }
                AutofillAuthorizationDecision.SUPPRESS_CREDENTIAL -> return@forEachIndexed
            }
            response.addDataset(dataset.build())
        }

        val saveIds = listOfNotNull(usernameField?.node?.autofillId, passwordField?.node?.autofillId)
        if (saveIds.isNotEmpty()) {
            response.setSaveInfo(
                SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    saveIds.toTypedArray()
                ).build()
            )
        }
        callback.onSuccess(response.build())
    }

    private fun unlockIntentSender(
        credential: PasswordCredential,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        requestCode: Int
    ): IntentSender? {
        val intent = AutofillUnlockActivity.intentFor(this, credential.guid, usernameId, passwordId)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_CANCEL_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return runCatching {
            PendingIntent.getActivity(this, requestCode, intent, flags).intentSender
        }.getOrNull()
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val candidates = collectCandidates(structure)
        val usernameField = candidates.firstOrNull { isUsernameField(it.node) }
        val passwordField = candidates.firstOrNull { isPasswordField(it.node) }
        val origin = autofillOriginFor(passwordField?.origin, usernameField?.origin)
        val username = usernameField?.node?.autofillValue?.textValue?.toString()
            ?.trim()
            .orEmpty()
        val password = passwordField?.node?.autofillValue?.textValue?.toString()
            .orEmpty()
        if (origin.isNullOrBlank() || password.isEmpty()) {
            callback.onSuccess()
            return
        }

        try {
            PasswordManagerService.vault(this).upsert(
                PasswordCredential(
                    guid = UUID.randomUUID().toString(),
                    origin = origin,
                    formActionOrigin = null,
                    httpRealm = null,
                    username = username,
                    password = password
                )
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to save an Autofill password", error)
            callback.onFailure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "This password could not be saved. Please try again."
            )
            return
        }
        callback.onSuccess()
    }

    private data class Candidate(
        val node: AssistStructure.ViewNode,
        val origin: String?
    )

    private fun collectCandidates(structure: AssistStructure): List<Candidate> {
        val result = mutableListOf<Candidate>()
        for (windowIndex in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(windowIndex).rootViewNode
            collectNode(root, null, result)
        }
        return result
    }

    private fun collectNode(
        node: AssistStructure.ViewNode,
        inheritedOrigin: String?,
        result: MutableList<Candidate>
    ) {
        val domain = readWebDomain(node)
        val origin = if (domain == null) inheritedOrigin else {
            val scheme = runCatching {
                node.javaClass.getMethod("getWebScheme").invoke(node) as? String
            }.getOrNull()?.lowercase()
            if (scheme == "http" || scheme == "https") "$scheme://$domain" else null
        }
        result += Candidate(node, origin)
        for (index in 0 until node.childCount) {
            node.getChildAt(index)?.let { child ->
                collectNode(child, origin, result)
            }
        }
    }

    private fun readWebDomain(node: AssistStructure.ViewNode): String? =
        runCatching {
            node.javaClass.getMethod("getWebDomain").invoke(node) as? String
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

    private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        val hints = node.autofillHints.orEmpty().map { it.lowercase() }
        val variation = node.inputType and android.text.InputType.TYPE_MASK_VARIATION
        return hints.any { it.contains("password") } ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
        val hints = node.autofillHints.orEmpty().map { it.lowercase() }
        val variation = node.inputType and android.text.InputType.TYPE_MASK_VARIATION
        return hints.any {
            it.contains("username") || it.contains("email") || it.contains("login")
        } ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }

    private companion object {
        const val TAG = "ILYRO.Autofill"
        const val MAX_DATASETS = 10
    }
}

/**
 * Resolves the single web origin a fill/save request belongs to.
 *
 * A request is only trusted when the username and password fields agree on their origin. That
 * keeps a third-party frame on the page from receiving a credential saved for the top-level site.
 */
internal fun autofillOriginFor(passwordOrigin: String?, usernameOrigin: String?): String? {
    val password = passwordOrigin?.trim()?.takeIf { it.isNotBlank() }
    val username = usernameOrigin?.trim()?.takeIf { it.isNotBlank() }
    if (password != null && username != null && !password.equals(username, ignoreCase = true)) {
        return null
    }
    return password ?: username
}
