package com.ilyro.browser.passwords

import android.app.assist.AssistStructure
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
        val domain = candidates.firstNotNullOfOrNull { it.domain }
        if (domain.isNullOrBlank()) {
            callback.onSuccess(null)
            return
        }

        val credentials = runCatching {
            PasswordManagerService.vault(this)
                .snapshot()
                .filter { passwordDomainMatches(it.origin, domain) }
        }.getOrDefault(emptyList())
        if (credentials.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val usernameField = candidates.firstOrNull { isUsernameField(it.node) }
        val passwordField = candidates.firstOrNull { isPasswordField(it.node) }
        if (usernameField == null && passwordField == null) {
            callback.onSuccess(null)
            return
        }

        val response = FillResponse.Builder()
        credentials.take(MAX_DATASETS).forEach { credential ->
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(
                    android.R.id.text1,
                    credential.username.ifBlank { credential.origin }
                )
            }
            val dataset = Dataset.Builder(presentation)
            usernameField?.node?.autofillId?.let { id ->
                dataset.setValue(id, AutofillValue.forText(credential.username))
            }
            passwordField?.node?.autofillId?.let { id ->
                dataset.setValue(id, AutofillValue.forText(credential.password))
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

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val candidates = collectCandidates(structure)
        val domain = candidates.firstNotNullOfOrNull { it.domain }
        val usernameField = candidates.firstOrNull { isUsernameField(it.node) }
        val passwordField = candidates.firstOrNull { isPasswordField(it.node) }
        val username = usernameField?.node?.autofillValue?.textValue?.toString()
            ?.trim()
            .orEmpty()
        val password = passwordField?.node?.autofillValue?.textValue?.toString()
            .orEmpty()
        if (domain.isNullOrBlank() || password.isEmpty()) {
            callback.onSuccess()
            return
        }

        val origin = if (domain.startsWith("http://") || domain.startsWith("https://")) {
            domain
        } else {
            "https://" + domain
        }
        runCatching {
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
        }
        callback.onSuccess()
    }

    private data class Candidate(
        val node: AssistStructure.ViewNode,
        val domain: String?
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
        inheritedDomain: String?,
        result: MutableList<Candidate>
    ) {
        val domain = readWebDomain(node) ?: inheritedDomain
        result += Candidate(node, domain)
        for (index in 0 until node.childCount) {
            node.getChildAt(index)?.let { child ->
                collectNode(child, domain, result)
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
        const val MAX_DATASETS = 10
    }
}
