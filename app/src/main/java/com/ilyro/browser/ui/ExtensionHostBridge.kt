package com.ilyro.browser.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension

internal data class ExtensionActionUi(
    val extensionId: String,
    val title: String,
    val badgeText: String?,
    val icon: Image?,
    val enabled: Boolean
)

/**
 * Hosts the parts of the WebExtension API that require browser chrome.
 *
 * Gecko runs the extension itself; ILYRO only supplies the native browser surfaces that
 * Firefox normally supplies: toolbar/page actions, popup GeckoSessions, active-tab state,
 * and a bridge for browser.downloads requests.
 */
internal object ExtensionHostBridge {
    private var runtime: GeckoRuntime? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSession: GeckoSession? = null
    private val boundSessions = mutableSetOf<GeckoSession>()
    private val extensions = linkedMapOf<String, WebExtension>()

    private val defaultBrowserActions = mutableMapOf<String, WebExtension.Action>()
    private val defaultPageActions = mutableMapOf<String, WebExtension.Action>()
    private val sessionBrowserActions = mutableMapOf<String, MutableMap<GeckoSession, WebExtension.Action>>()
    private val sessionPageActions = mutableMapOf<String, MutableMap<GeckoSession, WebExtension.Action>>()

    private var downloadHandler: ((WebExtension, WebExtension.DownloadRequest) -> GeckoResult<WebExtension.DownloadInitData>)? = null
    private var newTabHandler:
        ((WebExtension, WebExtension.CreateTabDetails) -> GeckoResult<GeckoSession>?)? = null
    private var optionsPageHandler: ((WebExtension, String) -> Unit)? = null
    private var popupExtensionId: String? = null
    private var explicitActionExtensionId: String? = null
    private var explicitActionUntilElapsedMs: Long = 0L
    private val autoOpenBlockedExtensionIds = mutableSetOf<String>()

    val activeActions = mutableStateListOf<ExtensionActionUi>()

    var popupSession by mutableStateOf<GeckoSession?>(null)
        private set

    var popupTitle by mutableStateOf("")
        private set

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action
        ) {
            if (session == null) {
                defaultBrowserActions[extension.id] = action
            } else {
                sessionBrowserActions.getOrPut(extension.id) { mutableMapOf() }[session] = action
            }
            refreshActiveActions()
        }

        override fun onPageAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action
        ) {
            if (session == null) {
                defaultPageActions[extension.id] = action
            } else {
                sessionPageActions.getOrPut(extension.id) { mutableMapOf() }[session] = action
            }
            refreshActiveActions()
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            if (shouldSuppressUnsolicitedOpen(extension.id)) return null
            return openPopup(extension, action, toggle = false)
        }

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action
        ): GeckoResult<GeckoSession>? {
            if (shouldSuppressUnsolicitedOpen(extension.id)) return null
            return openPopup(extension, action, toggle = true)
        }
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(
            source: WebExtension,
            createDetails: WebExtension.CreateTabDetails
        ): GeckoResult<GeckoSession>? {
            val targetUrl = createDetails.url?.takeIf { it.isNotBlank() }

            // Extension install/startup flows often try to open welcome pages. They must not
            // steal focus or create chrome unless the user explicitly asked to open the add-on.
            if (shouldSuppressUnsolicitedOpen(source.id)) {
                return null
            }

            val openedFromVisiblePopup =
                popupSession != null && popupExtensionId == source.id
            val internalOrTemporary =
                targetUrl == null ||
                    targetUrl.equals("about:blank", ignoreCase = true) ||
                    isInternalExtensionUrl(targetUrl)

            if (openedFromVisiblePopup && internalOrTemporary) {
                return createExtensionPagePopupSession(source)
            }

            return newTabHandler?.invoke(source, createDetails)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            val optionsUrl = source.metaData.optionsPageUrl
                ?.takeIf { it.isNotBlank() }
                ?: return

            if (shouldSuppressUnsolicitedOpen(source.id)) return

            if (isInternalExtensionUrl(optionsUrl)) {
                openInternalExtensionPage(source, optionsUrl)
            } else {
                optionsPageHandler?.invoke(source, optionsUrl)
            }
        }
    }

    private val sessionTabDelegate = object : WebExtension.SessionTabDelegate {
        override fun onUpdateTab(
            extension: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails
        ): GeckoResult<AllowOrDeny> {
            // Some add-ons create about:blank first and navigate it with tabs.update().
            // GeckoView performs that navigation after ALLOW.
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        override fun onCloseTab(
            source: WebExtension?,
            session: GeckoSession
        ): GeckoResult<AllowOrDeny> {
            if (popupSession === session) {
                mainHandler.post { closePopup() }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
    }

    private val downloadDelegate = object : WebExtension.DownloadDelegate {
        override fun onDownload(
            source: WebExtension,
            request: WebExtension.DownloadRequest
        ): GeckoResult<WebExtension.DownloadInitData>? {
            val handler = downloadHandler
                ?: return GeckoResult.fromException(
                    IllegalStateException("ILYRO download service is not ready")
                )
            return handler(source, request)
        }
    }

    fun initialize(runtime: GeckoRuntime) {
        this.runtime = runtime
        activeSession?.let { session ->
            runCatching {
                runtime.webExtensionController.setTabActive(session, true)
            }
        }
    }

    fun setDownloadHandler(
        handler: ((WebExtension, WebExtension.DownloadRequest) -> GeckoResult<WebExtension.DownloadInitData>)?
    ) {
        downloadHandler = handler
    }

    fun suppressAutoOpenUntilUserAction(extensionId: String) {
        autoOpenBlockedExtensionIds.add(extensionId)
    }


    fun setTabHandlers(
        onNewTab: ((WebExtension, WebExtension.CreateTabDetails) -> GeckoResult<GeckoSession>?)?,
        onOpenOptionsPage: ((WebExtension, String) -> Unit)?
    ) {
        newTabHandler = onNewTab
        optionsPageHandler = onOpenOptionsPage
    }

    fun openOptionsPage(extensionId: String): Boolean {
        val extension = extensions[extensionId] ?: return false
        autoOpenBlockedExtensionIds.remove(extensionId)
        val optionsUrl = extension.metaData.optionsPageUrl
            ?.takeIf { it.isNotBlank() }
            ?: return false

        return if (isInternalExtensionUrl(optionsUrl)) {
            openInternalExtensionPage(extension, optionsUrl)
        } else {
            val handler = optionsPageHandler ?: return false
            handler(extension, optionsUrl)
            true
        }
    }

    fun registerExtension(extension: WebExtension) {
        extensions[extension.id] = extension
        extension.setDownloadDelegate(downloadDelegate)
        extension.setTabDelegate(tabDelegate)

        extension.setActionDelegate(actionDelegate)
        boundSessions.forEach { session ->
            session.webExtensionController.setActionDelegate(extension, actionDelegate)
            session.webExtensionController.setTabDelegate(extension, sessionTabDelegate)
        }
        refreshActiveActions()
    }

    fun unregisterExtension(id: String) {
        val extension = extensions.remove(id)
        if (extension != null) {
            runCatching { extension.setActionDelegate(null) }
            runCatching { extension.setDownloadDelegate(null) }
            runCatching { extension.setTabDelegate(null) }
            boundSessions.forEach { session ->
                runCatching { session.webExtensionController.setActionDelegate(extension, null) }
                runCatching { session.webExtensionController.setTabDelegate(extension, null) }
            }
        }
        defaultBrowserActions.remove(id)
        defaultPageActions.remove(id)
        sessionBrowserActions.remove(id)
        sessionPageActions.remove(id)
        if (popupTitle.isNotBlank() && popupSession != null && id !in extensions) {
            closePopup()
        }
        refreshActiveActions()
    }

    fun bindSession(session: GeckoSession) {
        if (!boundSessions.add(session)) return
        extensions.values.forEach { extension ->
            session.webExtensionController.setActionDelegate(extension, actionDelegate)
            session.webExtensionController.setTabDelegate(extension, sessionTabDelegate)
        }
        refreshActiveActions()
    }

    fun unbindSession(session: GeckoSession) {
        boundSessions.remove(session)
        extensions.values.forEach { extension ->
            runCatching { session.webExtensionController.setActionDelegate(extension, null) }
            runCatching { session.webExtensionController.setTabDelegate(extension, null) }
        }
        sessionBrowserActions.values.forEach { it.remove(session) }
        sessionPageActions.values.forEach { it.remove(session) }
        if (activeSession === session) {
            setActiveSession(null)
        } else {
            refreshActiveActions()
        }
    }

    fun setActiveSession(session: GeckoSession?) {
        // GeckoView tracks the visible GeckoSession and the active WebExtension tab separately.
        // Notify WebExtensions only when the selected session actually changes. This gives
        // browser-action popups (Dark Reader, Decentraleyes, etc.) a real active tab without
        // reintroducing the old bug where Compose recompositions repeatedly emitted
        // tabs.onActivated and disturbed media-heavy pages.
        val previous = activeSession
        if (previous === session) {
            refreshActiveActions()
            return
        }

        val controller = runtime?.webExtensionController
        if (previous != null) {
            runCatching { controller?.setTabActive(previous, false) }
        }

        activeSession = session

        if (session != null) {
            runCatching { controller?.setTabActive(session, true) }
        }
        refreshActiveActions()
    }

    fun clickAction(extensionId: String): Boolean {
        val action = resolveAction(extensionId) ?: return false
        if (action.enabled == false) return false

        autoOpenBlockedExtensionIds.remove(extensionId)
        explicitActionExtensionId = extensionId
        explicitActionUntilElapsedMs = SystemClock.elapsedRealtime() + 2_500L

        return runCatching {
            action.click()
            true
        }.getOrElse {
            explicitActionExtensionId = null
            explicitActionUntilElapsedMs = 0L
            false
        }
    }

    fun closePopup() {
        val session = popupSession
        popupSession = null
        popupTitle = ""
        popupExtensionId = null
        if (session != null) {
            runCatching { session.setFocused(false) }
            runCatching { session.setActive(false) }
            if (session.isOpen) {
                runCatching { session.close() }
            }
        }
    }

    private fun createExtensionPagePopupSession(
        extension: WebExtension
    ): GeckoResult<GeckoSession> {
        // IMPORTANT: WebExtensionController.newTab() requires an unopened GeckoSession.
        // GeckoView opens it itself with the extension tab's internal newSessionId and then
        // navigates it to CreateTabDetails.url. Returning an already-open session causes
        // IllegalArgumentException and leaves our host showing an empty grey surface.
        val session = createUnopenedPopupSession(extension)
        return runCatching {
            replacePopupSession(
                extension = extension,
                session = session,
                title = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "Extension"
            )
            // These calls are safe before open; GeckoSession queues state until GeckoView
            // opens the session after this result resolves.
            session.setActive(true)
            session.setFocused(true)
            GeckoResult.fromValue(session)
        }.getOrElse { error ->
            GeckoResult.fromException(error)
        }
    }

    private fun openInternalExtensionPage(
        extension: WebExtension,
        url: String
    ): Boolean {
        val existing = popupSession
        if (
            existing != null &&
            existing.isOpen &&
            popupExtensionId == extension.id
        ) {
            return runCatching {
                popupTitle = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "Extension"
                existing.setActive(true)
                existing.setFocused(true)
                existing.loadUri(url)
                true
            }.getOrDefault(false)
        }

        val currentRuntime = runtime ?: return false
        val session = createPopupSession(extension, currentRuntime)

        return runCatching {
            replacePopupSession(
                extension = extension,
                session = session,
                title = extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "Extension"
            )
            session.loadUri(url)
            true
        }.getOrElse {
            runCatching { if (session.isOpen) session.close() }
            false
        }
    }

    private fun createUnopenedPopupSession(
        extension: WebExtension
    ): GeckoSession {
        return GeckoSession().apply {
            installPopupDelegates(extension)
        }
    }

    private fun createPopupSession(
        extension: WebExtension,
        runtime: GeckoRuntime
    ): GeckoSession {
        return createUnopenedPopupSession(extension).apply {
            open(runtime)
            setActive(true)
            setFocused(true)
        }
    }

    private fun GeckoSession.installPopupDelegates(extension: WebExtension) {
        setPermissionDelegate(IlyroPermissionDelegate)
        setPromptDelegate(IlyroPromptDelegate)
        setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                if (popupSession === session) closePopup()
            }

            override fun onCrash(session: GeckoSession) {
                if (popupSession === session) closePopup()
            }

            override fun onKill(session: GeckoSession) {
                if (popupSession === session) closePopup()
            }
        })
        webExtensionController.setTabDelegate(extension, sessionTabDelegate)
    }

    private fun replacePopupSession(
        extension: WebExtension,
        session: GeckoSession,
        title: String
    ) {
        val previous = popupSession
        popupExtensionId = extension.id
        popupTitle = title
        popupSession = session
        explicitActionExtensionId = null
        explicitActionUntilElapsedMs = 0L

        // Let the event that opened the next internal page return before tearing down
        // the previous popup's JavaScript context.
        if (previous != null && previous !== session) {
            mainHandler.post {
                runCatching { previous.setFocused(false) }
                runCatching { previous.setActive(false) }
                runCatching {
                    if (previous.isOpen) previous.close()
                }
            }
        }
    }

    private fun isInternalExtensionUrl(url: String): Boolean {
        return url.startsWith("moz-extension://", ignoreCase = true)
    }

    private fun shouldSuppressUnsolicitedOpen(extensionId: String): Boolean {
        val explicit =
            explicitActionExtensionId == extensionId &&
                SystemClock.elapsedRealtime() <= explicitActionUntilElapsedMs
        val alreadyOpen = popupExtensionId == extensionId && popupSession != null
        val blockedAfterInstall = extensionId in autoOpenBlockedExtensionIds
        return (
            ProtectionBridge.shouldSuppressExtensionAutoOpen() ||
                blockedAfterInstall
            ) && !explicit && !alreadyOpen
    }

    private fun openPopup(
        extension: WebExtension,
        action: WebExtension.Action,
        toggle: Boolean
    ): GeckoResult<GeckoSession>? {
        if (toggle && popupSession != null) {
            closePopup()
            return null
        }

        val currentRuntime = runtime
            ?: return GeckoResult.fromException(IllegalStateException("GeckoRuntime is not ready"))

        val session = createPopupSession(extension, currentRuntime)
        return runCatching {
            replacePopupSession(
                extension = extension,
                session = session,
                title = action.title
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "Extension"
            )
            GeckoResult.fromValue(session)
        }.getOrElse { error ->
            runCatching { if (session.isOpen) session.close() }
            GeckoResult.fromException(error)
        }
    }

    private fun resolveAction(extensionId: String): WebExtension.Action? {
        val session = activeSession
        val browserDefault = defaultBrowserActions[extensionId]
        val browserOverride = session?.let { sessionBrowserActions[extensionId]?.get(it) }
        val browser = mergeAction(browserOverride, browserDefault)
        if (browser != null) return browser

        val pageDefault = defaultPageActions[extensionId]
        val pageOverride = session?.let { sessionPageActions[extensionId]?.get(it) }
        return mergeAction(pageOverride, pageDefault)
    }

    private fun mergeAction(
        override: WebExtension.Action?,
        default: WebExtension.Action?
    ): WebExtension.Action? {
        if (override == null) return default
        if (default == null) return override
        return runCatching { override.withDefault(default) }.getOrDefault(override)
    }

    private fun refreshActiveActions() {
        val next = extensions.values.mapNotNull { extension ->
            if (!extension.metaData.enabled) return@mapNotNull null
            val action = resolveAction(extension.id) ?: return@mapNotNull null
            val enabled = action.enabled != false
            // Page actions that are hidden should not occupy permanent browser chrome.
            if (!enabled && extension.id in defaultPageActions && extension.id !in defaultBrowserActions) {
                return@mapNotNull null
            }
            ExtensionActionUi(
                extensionId = extension.id,
                title = action.title
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.metaData.name
                    ?.takeIf { it.isNotBlank() }
                    ?: extension.id,
                badgeText = action.badgeText?.takeIf { it.isNotBlank() },
                icon = action.icon ?: extension.metaData.icon,
                enabled = enabled
            )
        }.sortedBy { it.title.lowercase() }

        activeActions.clear()
        activeActions.addAll(next)
    }
}
