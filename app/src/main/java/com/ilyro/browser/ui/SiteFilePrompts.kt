package com.ilyro.browser.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.io.File

internal object SiteFilePromptCoordinator {
    private data class PendingPrompt(
        val prompt: GeckoSession.PromptDelegate.FilePrompt,
        val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>
    )

    private var appContext: Context? = null
    private var intentLauncher: ((Intent) -> Unit)? = null
    private var cameraPermissionLauncher: (() -> Unit)? = null
    private var pending: PendingPrompt? = null
    private var pendingCameraUri: Uri? = null

    fun bind(
        context: Context,
        launchIntent: (Intent) -> Unit,
        requestCameraPermission: () -> Unit
    ) {
        appContext = context.applicationContext
        intentLauncher = launchIntent
        cameraPermissionLauncher = requestCameraPermission
    }

    fun unbind() {
        dismissPending()
        intentLauncher = null
        cameraPermissionLauncher = null
        appContext = null
    }

    fun requestFilePrompt(
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        dismissPending()
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        pending = PendingPrompt(prompt, result)
        pendingCameraUri = null

        if (shouldCaptureImage(prompt)) {
            val context = appContext
            if (context == null) {
                dismissPending()
            } else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraCapture()
            } else {
                val request = cameraPermissionLauncher
                if (request == null) dismissPending() else runCatching(request).onFailure { dismissPending() }
            }
        } else {
            launchDocumentPicker(prompt)
        }
        return result
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (pending == null) return
        if (granted) launchCameraCapture() else dismissPending()
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val current = pending ?: return
        val context = appContext ?: run {
            dismissPending()
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            dismissPending()
            return
        }

        val uris = collectResultUris(data)
        if (uris.isEmpty()) {
            dismissPending()
            return
        }

        val response = runCatching {
            if (current.prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE) {
                current.prompt.confirm(context, uris.toTypedArray())
            } else {
                current.prompt.confirm(context, uris.first())
            }
        }.getOrElse {
            dismissPending()
            return
        }

        pending = null
        pendingCameraUri = null
        current.result.complete(response)
    }

    private fun shouldCaptureImage(prompt: GeckoSession.PromptDelegate.FilePrompt): Boolean {
        if (prompt.capture == GeckoSession.PromptDelegate.FilePrompt.Capture.NONE) return false
        val mimeTypes = prompt.mimeTypes.orEmpty().filter { it.isNotBlank() }
        return mimeTypes.isEmpty() || mimeTypes.any { type ->
            type.equals("image/*", ignoreCase = true) || type.startsWith("image/", ignoreCase = true)
        }
    }

    private fun launchDocumentPicker(prompt: GeckoSession.PromptDelegate.FilePrompt) {
        val launcher = intentLauncher ?: run {
            dismissPending()
            return
        }
        val mimeTypes = prompt.mimeTypes.orEmpty().filter { it.isNotBlank() }.distinct()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
            if (mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            }
            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            )
        }
        runCatching { launcher(intent) }.onFailure { dismissPending() }
    }

    private fun launchCameraCapture() {
        val context = appContext ?: run {
            dismissPending()
            return
        }
        val launcher = intentLauncher ?: run {
            dismissPending()
            return
        }

        val output = runCatching {
            val directory = File(context.cacheDir, "file-prompts").apply { mkdirs() }
            val file = File.createTempFile("ilyro-capture-", ".jpg", directory)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse {
            dismissPending()
            return
        }

        pendingCameraUri = output
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, output)
            clipData = ClipData.newRawUri("ILYRO camera capture", output)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        runCatching { launcher(intent) }.onFailure { dismissPending() }
    }

    private fun collectResultUris(data: Intent?): List<Uri> {
        val result = LinkedHashSet<Uri>()
        data?.data?.let(result::add)
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(result::add)
            }
        }
        if (result.isEmpty()) pendingCameraUri?.let(result::add)
        return result.toList()
    }

    private fun dismissPending() {
        val current = pending ?: return
        pending = null
        pendingCameraUri = null
        if (!current.prompt.isComplete) {
            runCatching { current.result.complete(current.prompt.dismiss()) }
        }
    }
}

internal object PasswordPromptCoordinator {
    private var activity: ComponentActivity? = null
    private var activeDialog: Dialog? = null
    private var cancelActiveRequest: (() -> Unit)? = null

    fun bind(activity: ComponentActivity) {
        this.activity = activity
    }

    fun unbind(owner: ComponentActivity) {
        if (activity !== owner) return
        dismissActivePrompt()
        activity = null
    }

    fun requestLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()

        // Saved passwords may still be used in private browsing, but credentials entered there
        // must never be persisted to the normal vault.
        if (session.settings.usePrivateMode) {
            result.complete(request.dismiss())
            return result
        }

        val option = request.options.firstOrNull()
        if (option == null) {
            result.complete(request.dismiss())
            return result
        }

        val owner = activity
        if (owner == null || owner.isFinishing || owner.isDestroyed) {
            result.complete(request.dismiss())
            return result
        }

        owner.runOnUiThread {
            dismissActivePrompt()

            val login = option.value
            val host = runCatching { Uri.parse(login.origin).host }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: login.origin
            val username = login.username.takeIf { it.isNotBlank() } ?: noUsernameLabel(owner)
            val updating = !login.guid.isNullOrBlank()
            var resolved = false

            val resolve: (Boolean) -> Unit = { accepted ->
                if (!resolved) {
                    resolved = true
                    cancelActiveRequest = null
                    activeDialog = null
                    val response = if (accepted) request.confirm(option) else request.dismiss()
                    result.complete(response)
                }
            }

            cancelActiveRequest = { resolve(false) }
            activeDialog = showIlyroPasswordSavePrompt(
                owner = owner,
                host = host,
                username = username,
                updating = updating,
                onAccept = { resolve(true) },
                onDismiss = { resolve(false) }
            )
        }

        return result
    }

    fun requestLoginSelect(
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val options = request.options
        if (options.isEmpty()) {
            result.complete(request.dismiss())
            return result
        }

        val owner = activity
        if (owner == null || owner.isFinishing || owner.isDestroyed) {
            result.complete(request.dismiss())
            return result
        }

        owner.runOnUiThread {
            dismissActivePrompt()
            var resolved = false
            val noUsername = noUsernameLabel(owner)
            val choices = options.map { option ->
                val login = option.value
                val host = runCatching { Uri.parse(login.origin).host }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: login.origin
                IlyroLoginChoice(
                    username = login.username.takeIf { it.isNotBlank() } ?: noUsername,
                    host = host
                )
            }

            val resolve: (Int?) -> Unit = { index ->
                if (!resolved) {
                    resolved = true
                    cancelActiveRequest = null
                    activeDialog = null
                    val response = if (index != null && index in options.indices) {
                        request.confirm(options[index])
                    } else {
                        request.dismiss()
                    }
                    result.complete(response)
                }
            }

            cancelActiveRequest = { resolve(null) }
            activeDialog = showIlyroLoginSelectPrompt(
                owner = owner,
                choices = choices,
                onSelect = { index -> resolve(index) },
                onDismiss = { resolve(null) }
            )
        }

        return result
    }

    private fun noUsernameLabel(owner: ComponentActivity): String {
        val prefs = owner.getSharedPreferences("ilyro_browser", Context.MODE_PRIVATE)
        val language = BrowserSettingsStore.restore(prefs).language
        return tr(language, "No username", "Без имени пользователя")
    }

    private fun dismissActivePrompt() {
        val cancel = cancelActiveRequest
        cancelActiveRequest = null
        val dialog = activeDialog
        activeDialog = null
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        cancel?.invoke()
    }
}

internal object IlyroPromptDelegate : GeckoSession.PromptDelegate {
    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        SiteFilePromptCoordinator.requestFilePrompt(prompt)

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        PasswordPromptCoordinator.requestLoginSave(session, request)

    override fun onLoginSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        PasswordPromptCoordinator.requestLoginSelect(request)
}
