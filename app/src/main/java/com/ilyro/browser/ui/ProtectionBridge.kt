package com.ilyro.browser.ui

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilyro.browser.passwords.PasswordManagerService
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal data class InstalledExtensionUi(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val optionsPageUrl: String?,
    val icon: Image?
)

internal enum class ExtensionPermissionPromptKind {
    INSTALL,
    OPTIONAL,
    UPDATE
}

internal data class ExtensionInstallPermissionRequest(
    val extensionName: String,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String>,
    val kind: ExtensionPermissionPromptKind
)

/**
 * Native bridge into the bundled uBlock Origin instance and ILYRO's WebExtension manager.
 *
 * uBO remains the filtering engine. User-installed Mozilla-signed extensions are owned by
 * GeckoView. ILYRO exposes install, enable/disable and removal controls.
 */
internal object ProtectionBridge {
    private const val NATIVE_APP = "ilyro_shield"
    private const val UBO_ID = "adblock@ilyro"
    private const val YOUTUBE_PERF_ID = "youtube-performance@ilyro"
    private val LEGACY_MEDIA_EXTENSION_IDS = setOf(
        "{b9db16a4-6edc-47ec-a1f4-b86292ed211d}",
        "faisalbhuiyan@mozilla"
    )

    private var port: WebExtension.Port? = null
    private var extensionRuntime: GeckoRuntime? = null
    private var uBlockExtension: WebExtension? = null
    private val siteStateByUrl = mutableStateMapOf<String, Boolean>()
    private val siteBlockedByUrl = mutableStateMapOf<String, Int>()
    private val siteAllowedByUrl = mutableStateMapOf<String, Int>()
    private val siteStatsReadyByUrl = mutableStateMapOf<String, Boolean>()

    private val extensionObjects = mutableMapOf<String, WebExtension>()

    val installedExtensions = mutableStateListOf<InstalledExtensionUi>()

    var uBlockIcon by mutableStateOf<Image?>(null)
        private set

    var uBlockVersion by mutableStateOf<String?>(null)
        private set

    var globalBlockedRequestCount by mutableStateOf<Long?>(null)
        private set

    var globalAllowedRequestCount by mutableStateOf<Long?>(null)
        private set

    var filterUpdateInProgress by mutableStateOf(false)
        private set

    var filterUpdateMessage by mutableStateOf<String?>(null)
        private set

    var lastFilterUpdateAt by mutableStateOf<Long?>(null)
        private set

    var extensionOperationBusy by mutableStateOf(false)
        private set

    var extensionOperationMessage by mutableStateOf<String?>(null)
        private set

    private var suppressAutoOpenUntilElapsedMs: Long = 0L

    fun shouldSuppressExtensionAutoOpen(): Boolean {
        return extensionOperationBusy ||
            SystemClock.elapsedRealtime() < suppressAutoOpenUntilElapsedMs
    }

    private fun suppressExtensionAutoOpenForInstall() {
        suppressAutoOpenUntilElapsedMs = maxOf(
            suppressAutoOpenUntilElapsedMs,
            SystemClock.elapsedRealtime() + 5_000L
        )
    }

    // Gecko delivers install prompts on its own thread, so these gates must be visible there.
    @Volatile
    private var allowNextInstallPrompt = false

    @Volatile
    private var interactiveInstallPrompt = false
    private var pendingInstallPermissionResult:
        GeckoResult<WebExtension.PermissionPromptResponse>? = null
    private var pendingRuntimePermissionResult: GeckoResult<AllowOrDeny>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var pendingInstallPermissionRequest by mutableStateOf<ExtensionInstallPermissionRequest?>(null)
        private set

    var extensionReady by mutableStateOf(false)
        private set

    var bridgeReady by mutableStateOf(false)
        private set

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(connectedPort: WebExtension.Port) {
            port = connectedPort
            bridgeReady = true

            connectedPort.setDelegate(
                object : WebExtension.PortDelegate {
                    override fun onPortMessage(
                        message: Any,
                        sourcePort: WebExtension.Port
                    ) {
                        val json = message as? JSONObject ?: return
                        when (json.optString("type")) {
                            "bridgeReady" -> bridgeReady = true
                            "siteState" -> {
                                val url = json.optString("url").trim()
                                if (url.isBlank()) return
                                siteStateByUrl[url] = json.optBoolean("enabled", true)

                                val statsAvailable = json.optBoolean("statsAvailable", false)
                                siteStatsReadyByUrl[url] = statsAvailable
                                if (statsAvailable) {
                                    siteBlockedByUrl[url] = json.optInt("blocked", 0).coerceAtLeast(0)
                                    siteAllowedByUrl[url] = json.optInt("allowed", 0).coerceAtLeast(0)
                                } else {
                                    siteBlockedByUrl.remove(url)
                                    siteAllowedByUrl.remove(url)
                                }
                            }
                            "globalStats" -> {
                                globalBlockedRequestCount =
                                    json.optLong("blocked", 0L).coerceAtLeast(0L)
                                globalAllowedRequestCount =
                                    json.optLong("allowed", 0L).coerceAtLeast(0L)
                            }
                            "filterUpdateStarted" -> {
                                filterUpdateInProgress = true
                                filterUpdateMessage = "updating"
                            }
                            "filterUpdateFinished" -> {
                                filterUpdateInProgress = false
                                lastFilterUpdateAt = json.optLong("updatedAt", System.currentTimeMillis())
                                    .takeIf { it > 0L }
                                filterUpdateMessage = "updated"
                            }
                            "filterUpdateFailed" -> {
                                filterUpdateInProgress = false
                                filterUpdateMessage = "failed"
                            }
                        }
                    }

                    override fun onDisconnect(sourcePort: WebExtension.Port) {
                        if (port === sourcePort) {
                            port = null
                            bridgeReady = false
                        }
                    }
                }
            )
        }
    }

    private val installPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val allowedToProceed = allowNextInstallPrompt
            allowNextInstallPrompt = false
            if (!allowedToProceed) {
                return GeckoResult.fromValue(permissionResponse(false))
            }
            // Only a request that asks for nothing may be granted without the user seeing it.
            // Everything else has to go through the visible permission prompt, even during the
            // queued onboarding installs.
            if (permissions.isEmpty() &&
                origins.isEmpty() &&
                dataCollectionPermissions.isEmpty()
            ) {
                return GeckoResult.fromValue(permissionResponse(true))
            }

            pendingInstallPermissionResult?.complete(permissionResponse(false))
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            pendingInstallPermissionResult = result
            val request = ExtensionInstallPermissionRequest(
                extensionName = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.id,
                permissions = permissions.map(String::trim).filter(String::isNotBlank),
                origins = origins.map(String::trim).filter(String::isNotBlank),
                dataCollectionPermissions = dataCollectionPermissions
                    .map(String::trim)
                    .filter(String::isNotBlank),
                kind = ExtensionPermissionPromptKind.INSTALL
            )
            mainHandler.post {
                pendingInstallPermissionRequest = request
            }
            return result
        }

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>
        ): GeckoResult<AllowOrDeny> {
            return requestRuntimePermission(
                extension = extension,
                permissions = permissions,
                origins = origins,
                dataCollectionPermissions = dataCollectionPermissions,
                kind = ExtensionPermissionPromptKind.OPTIONAL
            )
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollectionPermissions: Array<out String>
        ): GeckoResult<AllowOrDeny> {
            return requestRuntimePermission(
                extension = extension,
                permissions = newPermissions,
                origins = newOrigins,
                dataCollectionPermissions = newDataCollectionPermissions,
                kind = ExtensionPermissionPromptKind.UPDATE
            )
        }
    }

    private fun requestRuntimePermission(
        extension: WebExtension,
        permissions: Array<out String>,
        origins: Array<out String>,
        dataCollectionPermissions: Array<out String>,
        kind: ExtensionPermissionPromptKind
    ): GeckoResult<AllowOrDeny> {
        pendingRuntimePermissionResult?.complete(AllowOrDeny.DENY)
        pendingRuntimePermissionResult = GeckoResult<AllowOrDeny>()
        val result = pendingRuntimePermissionResult!!

        mainHandler.post {
            pendingInstallPermissionRequest = ExtensionInstallPermissionRequest(
                extensionName = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.id,
                permissions = permissions.map(String::trim).filter(String::isNotBlank),
                origins = origins.map(String::trim).filter(String::isNotBlank),
                dataCollectionPermissions = dataCollectionPermissions
                    .map(String::trim)
                    .filter(String::isNotBlank),
                kind = kind
            )
        }
        return result
    }

    private fun syncUserExtensionFromAddonManager(extension: WebExtension) {
        if (
            !extension.isBuiltIn &&
            extension.id != UBO_ID &&
            extension.id != YOUTUBE_PERF_ID
        ) {
            registerExtension(extension)
        }
    }

    private val addonManagerDelegate = object : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) {
            syncUserExtensionFromAddonManager(extension)
            refreshInstalledExtensions()
        }

        override fun onEnabled(extension: WebExtension) {
            syncUserExtensionFromAddonManager(extension)
            refreshInstalledExtensions()
        }

        override fun onDisabled(extension: WebExtension) {
            syncUserExtensionFromAddonManager(extension)
            refreshInstalledExtensions()
        }

        override fun onUninstalled(extension: WebExtension) {
            removeExtensionRegistration(extension.id)
            refreshInstalledExtensions()
        }

        override fun onOptionalPermissionsChanged(extension: WebExtension) {
            syncUserExtensionFromAddonManager(extension)
            refreshInstalledExtensions()
        }

        override fun onReady(extension: WebExtension) {
            syncUserExtensionFromAddonManager(extension)
            refreshInstalledExtensions()
        }
    }

    private fun permissionResponse(approved: Boolean): WebExtension.PermissionPromptResponse =
        WebExtension.PermissionPromptResponse(approved, false, false)

    fun initialize(runtime: GeckoRuntime) {
        extensionRuntime = runtime
        PasswordManagerService.install(runtime)
        ExtensionHostBridge.initialize(runtime)
        runtime.webExtensionController.setPromptDelegate(installPromptDelegate)
        runtime.webExtensionController.setAddonManagerDelegate(addonManagerDelegate)
    }

    fun attach(runtime: GeckoRuntime, extension: WebExtension) {
        initialize(runtime)
        port = null
        bridgeReady = false
        extensionReady = true
        siteStateByUrl.clear()
        siteBlockedByUrl.clear()
        siteAllowedByUrl.clear()
        siteStatsReadyByUrl.clear()
        uBlockExtension = extension
        uBlockIcon = extension.metaData.icon
        uBlockVersion = extension.metaData.version
        filterUpdateInProgress = false
        extension.setMessageDelegate(messageDelegate, NATIVE_APP)
        refreshInstalledExtensions()
    }

    fun siteEnabled(url: String): Boolean {
        return siteStateByUrl[url] ?: true
    }

    fun blockedRequestCount(url: String): Int? {
        if (siteStatsReadyByUrl[url] != true) return null
        return siteBlockedByUrl[url] ?: 0
    }

    fun allowedRequestCount(url: String): Int? {
        if (siteStatsReadyByUrl[url] != true) return null
        return siteAllowedByUrl[url] ?: 0
    }

    fun requestSiteState(url: String): Boolean {
        if (url.isBlank()) return false
        siteStatsReadyByUrl[url] = false
        return post(
            JSONObject()
                .put("type", "getSiteState")
                .put("url", url)
        )
    }

    fun setSiteEnabled(url: String, enabled: Boolean): Boolean {
        if (url.isBlank()) return false
        val sent = post(
            JSONObject()
                .put("type", "setSiteState")
                .put("url", url)
                .put("enabled", enabled)
        )
        if (sent) {
            siteStateByUrl[url] = enabled
            siteStatsReadyByUrl[url] = false
        }
        return sent
    }

    fun requestGlobalStats(): Boolean =
        post(JSONObject().put("type", "getGlobalStats"))

    fun updateFilterLists(): Boolean {
        if (filterUpdateInProgress) return false
        filterUpdateMessage = null
        val sent = post(JSONObject().put("type", "updateFilters"))
        if (!sent) {
            filterUpdateMessage = "not_ready"
        }
        return sent
    }

    fun removeUnsupportedUserExtensions() {
        val runtime = extensionRuntime ?: return
        runtime.webExtensionController.list().accept(
            { extensions ->
                val userExtensions = extensions.orEmpty().filter { !it.isBuiltIn }
                if (userExtensions.isEmpty()) {
                    installedExtensions.clear()
                    extensionObjects.keys.toList().forEach(::removeExtensionRegistration)
                    return@accept
                }
                userExtensions.forEach { extension ->
                    runtime.webExtensionController.uninstall(extension).accept(
                        {
                            removeExtensionRegistration(extension.id)
                            refreshInstalledExtensions()
                        },
                        { _ -> }
                    )
                }
            },
            { _ -> }
        )
    }

    fun removeLegacyMediaExtensions() {
        val runtime = extensionRuntime ?: return
        runtime.webExtensionController.list().accept(
            { extensions ->
                extensions.orEmpty()
                    .filter { extension ->
                        !extension.isBuiltIn && extension.id in LEGACY_MEDIA_EXTENSION_IDS
                    }
                    .forEach { extension ->
                        runtime.webExtensionController.uninstall(extension).accept(
                            { _ ->
                                removeExtensionRegistration(extension.id)
                                refreshInstalledExtensions()
                            },
                            { _ -> }
                        )
                    }
            },
            { _ -> }
        )
    }

    fun refreshInstalledExtensions() {
        val runtime = extensionRuntime ?: return
        runtime.webExtensionController.list().accept(
            { extensions ->
                val visibleObjects = extensions.orEmpty()
                    .filter { extension ->
                        !extension.isBuiltIn &&
                            extension.id != UBO_ID &&
                            extension.id != YOUTUBE_PERF_ID
                    }

                val currentIds = visibleObjects.map { it.id }.toSet()
                extensionObjects.keys
                    .filter { it !in currentIds }
                    .forEach { removeExtensionRegistration(it) }

                visibleObjects.forEach { registerExtension(it) }

                val visible = visibleObjects
                    .map { extension ->
                        InstalledExtensionUi(
                            id = extension.id,
                            name = extension.metaData.name
                                ?.takeIf { it.isNotBlank() }
                                ?: extension.id,
                            version = extension.metaData.version ?: "?",
                            enabled = extension.metaData.enabled,
                            allowedInPrivateBrowsing = extension.metaData.allowedInPrivateBrowsing,
                            optionsPageUrl = extension.metaData.optionsPageUrl
                                ?.takeIf { it.isNotBlank() },
                            icon = extension.metaData.icon
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                installedExtensions.clear()
                installedExtensions.addAll(visible)
            },
            { error ->
                extensionOperationMessage = error?.message
                    ?: "Couldn't read installed extensions."
            }
        )
    }

    fun installExtensionsSequentially(
        extensions: List<Pair<String, String>>,
        onComplete: () -> Unit
    ) {
        val runtime = extensionRuntime
        if (runtime == null || extensions.isEmpty()) {
            onComplete()
            return
        }
        if (extensionOperationBusy) {
            extensionOperationMessage = "Extension manager is busy. Selected extensions will be retried next start."
            return
        }

        interactiveInstallPrompt = false
        runtime.webExtensionController.list().accept(
            { existing ->
                val installedIds = existing.orEmpty().map { it.id }.toSet()
                val pending = extensions.filterNot { it.first in installedIds }
                installExtensionQueue(runtime, pending, 0, onComplete)
            },
            { _ ->
                installExtensionQueue(runtime, extensions, 0, onComplete)
            }
        )
    }

    private fun installExtensionQueue(
        runtime: GeckoRuntime,
        extensions: List<Pair<String, String>>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (index >= extensions.size) {
            extensionOperationBusy = false
            allowNextInstallPrompt = false
            interactiveInstallPrompt = false
            extensionOperationMessage = if (extensions.isEmpty()) null else "Selected extensions are ready."
            refreshInstalledExtensions()
            onComplete()
            return
        }

        val (_, url) = extensions[index]
        extensionOperationBusy = true
        suppressExtensionAutoOpenForInstall()
        extensionOperationMessage = "Installing selected extensions…"
        allowNextInstallPrompt = true

        runtime.webExtensionController.install(
            url,
            WebExtensionController.INSTALLATION_METHOD_ONBOARDING
        ).accept(
            { extension ->
                allowNextInstallPrompt = false
                if (extension != null) {
                    ExtensionHostBridge.suppressAutoOpenUntilUserAction(extension.id)
                    registerExtension(extension)
                }
                installExtensionQueue(runtime, extensions, index + 1, onComplete)
            },
            { _ ->
                allowNextInstallPrompt = false
                installExtensionQueue(runtime, extensions, index + 1, onComplete)
            }
        )
    }

    fun installExtension(url: String): Boolean {
        val runtime = extensionRuntime ?: return false
        val target = url.trim()
        if (!target.startsWith("https://", ignoreCase = true)) {
            extensionOperationMessage = "Use a direct HTTPS link to a signed Firefox .xpi file."
            return false
        }
        if (extensionOperationBusy) return false

        extensionOperationBusy = true
        suppressExtensionAutoOpenForInstall()
        extensionOperationMessage = "Installing extension…"
        interactiveInstallPrompt = true
        allowNextInstallPrompt = true

        runtime.webExtensionController.install(
            target,
            WebExtensionController.INSTALLATION_METHOD_MANAGER
        ).accept(
            { extension ->
                extensionOperationBusy = false
                suppressExtensionAutoOpenForInstall()
                allowNextInstallPrompt = false
                interactiveInstallPrompt = false
                cancelInstallPermissionPrompt()
                if (extension != null) {
                    ExtensionHostBridge.suppressAutoOpenUntilUserAction(extension.id)
                    registerExtension(extension)
                    val name = extension.metaData.name
                        ?.takeIf { it.isNotBlank() }
                        ?: extension.id
                    extensionOperationMessage = "$name installed."
                } else {
                    extensionOperationMessage = "Extension installed."
                }
                refreshInstalledExtensions()
            },
            { error ->
                extensionOperationBusy = false
                allowNextInstallPrompt = false
                interactiveInstallPrompt = false
                cancelInstallPermissionPrompt()
                extensionOperationMessage = error?.message
                    ?: "Installation failed. The extension must be signed by Mozilla and compatible with GeckoView."
                refreshInstalledExtensions()
            }
        )
        return true
    }

    fun setExtensionEnabled(id: String, enabled: Boolean): Boolean {
        val runtime = extensionRuntime ?: return false
        if (extensionOperationBusy) return false

        extensionOperationBusy = true
        runtime.webExtensionController.list().accept(
            { extensions ->
                val extension = extensions.orEmpty().firstOrNull { it.id == id }
                if (extension == null || extension.isBuiltIn) {
                    extensionOperationBusy = false
                    extensionOperationMessage = "Extension not found."
                    refreshInstalledExtensions()
                    return@accept
                }

                val result = if (enabled) {
                    runtime.webExtensionController.enable(
                        extension,
                        WebExtensionController.EnableSource.USER
                    )
                } else {
                    runtime.webExtensionController.disable(
                        extension,
                        WebExtensionController.EnableSource.USER
                    )
                }
                result.accept(
                    { updated ->
                        extensionOperationBusy = false
                        if (updated != null) {
                            registerExtension(updated)
                        }
                        val name = updated?.metaData?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: updated?.id
                            ?: "Extension"
                        extensionOperationMessage = if (updated?.metaData?.enabled == true) {
                            "$name enabled."
                        } else {
                            "$name disabled."
                        }
                        refreshInstalledExtensions()
                    },
                    { error ->
                        extensionOperationBusy = false
                        extensionOperationMessage = error?.message
                            ?: "Couldn't change extension state."
                        refreshInstalledExtensions()
                    }
                )
            },
            { error ->
                extensionOperationBusy = false
                extensionOperationMessage = error?.message ?: "Couldn't find extension."
            }
        )
        return true
    }

    fun updateExtension(id: String): Boolean {
        val runtime = extensionRuntime ?: return false
        if (extensionOperationBusy) return false

        extensionOperationBusy = true
        extensionOperationMessage = "Checking for extension update…"
        runtime.webExtensionController.list().accept(
            { extensions ->
                val extension = extensions.orEmpty().firstOrNull { it.id == id }
                if (extension == null || extension.isBuiltIn) {
                    extensionOperationBusy = false
                    extensionOperationMessage = "Extension not found."
                    refreshInstalledExtensions()
                    return@accept
                }

                runtime.webExtensionController.update(extension).accept(
                    { updated ->
                        extensionOperationBusy = false
                        if (updated != null) {
                            registerExtension(updated)
                            val name = updated.metaData.name
                                ?.takeIf { it.isNotBlank() }
                                ?: updated.id
                            extensionOperationMessage = "$name is up to date."
                        } else {
                            extensionOperationMessage = "No extension update was applied."
                        }
                        refreshInstalledExtensions()
                    },
                    { error ->
                        extensionOperationBusy = false
                        cancelInstallPermissionPrompt()
                        extensionOperationMessage = error?.message
                            ?: "Couldn't update extension."
                        refreshInstalledExtensions()
                    }
                )
            },
            { error ->
                extensionOperationBusy = false
                extensionOperationMessage = error?.message ?: "Couldn't find extension."
            }
        )
        return true
    }

    fun setExtensionAllowedInPrivateBrowsing(id: String, allowed: Boolean): Boolean {
        val runtime = extensionRuntime ?: return false
        if (extensionOperationBusy) return false

        extensionOperationBusy = true
        runtime.webExtensionController.list().accept(
            { extensions ->
                val extension = extensions.orEmpty().firstOrNull { it.id == id }
                if (extension == null || extension.isBuiltIn) {
                    extensionOperationBusy = false
                    extensionOperationMessage = "Extension not found."
                    refreshInstalledExtensions()
                    return@accept
                }

                runtime.webExtensionController
                    .setAllowedInPrivateBrowsing(extension, allowed)
                    .accept(
                        { updated ->
                            extensionOperationBusy = false
                            if (updated != null) registerExtension(updated)
                            val name = updated?.metaData?.name
                                ?.takeIf { it.isNotBlank() }
                                ?: updated?.id
                                ?: "Extension"
                            extensionOperationMessage = if (updated?.metaData?.allowedInPrivateBrowsing == true) {
                                "$name allowed in private tabs."
                            } else {
                                "$name disabled in private tabs."
                            }
                            refreshInstalledExtensions()
                        },
                        { error ->
                            extensionOperationBusy = false
                            extensionOperationMessage = error?.message
                                ?: "Couldn't change private browsing access."
                            refreshInstalledExtensions()
                        }
                    )
            },
            { error ->
                extensionOperationBusy = false
                extensionOperationMessage = error?.message ?: "Couldn't find extension."
            }
        )
        return true
    }

    fun uninstallExtension(id: String): Boolean {
        val runtime = extensionRuntime ?: return false
        if (extensionOperationBusy) return false

        extensionOperationBusy = true
        runtime.webExtensionController.list().accept(
            { extensions ->
                val extension = extensions.orEmpty().firstOrNull { it.id == id }
                if (extension == null || extension.isBuiltIn) {
                    extensionOperationBusy = false
                    extensionOperationMessage = "System extensions can't be removed."
                    refreshInstalledExtensions()
                    return@accept
                }

                val name = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.id
                runtime.webExtensionController.uninstall(extension).accept(
                    { _ ->
                        extensionOperationBusy = false
                        removeExtensionRegistration(id)
                        extensionOperationMessage = "$name removed."
                        refreshInstalledExtensions()
                    },
                    { error ->
                        extensionOperationBusy = false
                        extensionOperationMessage = error?.message ?: "Couldn't remove extension."
                        refreshInstalledExtensions()
                    }
                )
            },
            { error ->
                extensionOperationBusy = false
                extensionOperationMessage = error?.message ?: "Couldn't find extension."
            }
        )
        return true
    }

    fun respondToInstallPermissionPrompt(approved: Boolean) {
        val installResult = pendingInstallPermissionResult
        val runtimeResult = pendingRuntimePermissionResult
        pendingInstallPermissionResult = null
        pendingRuntimePermissionResult = null
        pendingInstallPermissionRequest = null
        interactiveInstallPrompt = false

        installResult?.complete(permissionResponse(approved))
        runtimeResult?.complete(if (approved) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
    }

    private fun cancelInstallPermissionPrompt() {
        if (
            pendingInstallPermissionResult != null ||
            pendingRuntimePermissionResult != null ||
            pendingInstallPermissionRequest != null
        ) {
            respondToInstallPermissionPrompt(false)
        } else {
            interactiveInstallPrompt = false
        }
    }

    fun clearExtensionOperationMessage() {
        extensionOperationMessage = null
    }

    fun bindSession(session: GeckoSession) {
        ExtensionHostBridge.bindSession(session)
    }

    fun setActiveSession(session: GeckoSession?) {
        ExtensionHostBridge.setActiveSession(session)
    }

    fun unbindSession(session: GeckoSession) {
        ExtensionHostBridge.unbindSession(session)
    }

    fun blockedBadge(@Suppress("UNUSED_PARAMETER") session: GeckoSession): String = ""

    fun openSiteControls(session: GeckoSession): Boolean {
        return runCatching {
            session.reload()
            true
        }.getOrDefault(false)
    }

    fun dashboardUrl(): String? =
        uBlockExtension?.metaData?.optionsPageUrl?.takeIf { it.isNotBlank() }

    fun filterListsUrl(): String? =
        dashboardUrl()?.let { url ->
            when {
                url.contains("#") -> url.substringBefore("#") + "#3p-filters.html"
                else -> url + "#3p-filters.html"
            }
        }

    private fun registerExtension(extension: WebExtension) {
        extensionObjects[extension.id] = extension
        ExtensionHostBridge.registerExtension(extension)
    }

    private fun removeExtensionRegistration(id: String) {
        extensionObjects.remove(id)
        ExtensionHostBridge.unregisterExtension(id)
    }

    private fun post(message: JSONObject): Boolean {
        val currentPort = port ?: return false
        return runCatching {
            currentPort.postMessage(message)
            true
        }.getOrDefault(false)
    }
}