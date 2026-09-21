@file:OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)

package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilyro.browser.NativeBrowserHostCoordinator
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebResponse
import java.util.UUID

private const val SESSION_STATE_SERIALIZE_INTERVAL_MS = 750L
private const val MAX_IN_MEMORY_SESSION_STATE_CHARS = 256_000

private fun browserTabHostLabel(url: String): String {
    if (url == HOME_URL) return "ILYRO Home"
    val host = runCatching {
        Uri.parse(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }
    return host ?: "New tab"
}

@org.mozilla.geckoview.ExperimentalGeckoViewApi
internal class BrowserTab(
    private val runtime: GeckoRuntime,
    private val initialUrl: String,
    val isPrivate: Boolean = false,
    initialPinned: Boolean = false,
    initialGroup: String? = null,
    settings: BrowserSettings,
    private val desktopModeForUrl: (String) -> Boolean,
    private val onDownload: (WebResponse, String?, Boolean) -> Unit,
    private val onDownloadNavigation: (String, String?, Boolean, GeckoSessionSettings) -> Unit,
    private val onNewTabRequest: (BrowserTab, String, GeckoResult<GeckoSession>) -> Unit,
    private val onLinkContextMenu: (BrowserTab, GeckoSession.ContentDelegate.ContextElement) -> Unit,
    private val restoredSessionState: String? = null,
    private val loadInitialUri: Boolean = true,
    private val openSession: Boolean = true
) {
    val id: String = UUID.randomUUID().toString()
    private val privateContextId: String? = if (isPrivate) "ilyro-private-$id" else null
    private var desktopModeEnabled: Boolean = desktopModeForUrl(initialUrl)
    val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .contextId(privateContextId)
            .suspendMediaWhenInactive(false)
            .userAgentMode(
                if (desktopModeEnabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (desktopModeEnabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .build()
    )

    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(if (initialUrl == HOME_URL) "New tab" else browserTabHostLabel(initialUrl))
    var isPinned by mutableStateOf(initialPinned)
    var groupName by mutableStateOf(initialGroup?.trim()?.takeIf { it.isNotBlank() }?.take(48))
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var loadProgress by mutableIntStateOf(0)
    var isPullRefreshing by mutableStateOf(false)
    var pullDistance by mutableStateOf(0f)
    var lastSuccessfulPageUrl by mutableStateOf<String?>(null)
    private var urlBeforeCurrentLoad: String? = null
    var isFullScreen by mutableStateOf(false)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
    var previewRecency by mutableIntStateOf(0)
    var serializedSessionState by mutableStateOf(restoredSessionState)
        private set
    var loadSequence by mutableIntStateOf(0)
        private set
    var pageStartSequence by mutableIntStateOf(0)
        private set
    var translationState by mutableStateOf<TranslationsController.SessionTranslation.TranslationState?>(null)
        private set
    var translationReady by mutableStateOf(false)
        private set
    var pageReaderable by mutableStateOf(false)
        private set
    var pageLanguage by mutableStateOf<String?>(null)
        private set
    var readerArticle by mutableStateOf<ReaderArticle?>(null)
        private set
    var readerLoading by mutableStateOf(false)
        private set
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sessionOpened = false
    private var lastSessionStateSerializedAt = 0L
    private var pendingSessionState: GeckoSession.SessionState? = null
    private var sessionStatePublishScheduled = false

    private fun publishPendingSessionState() {
        val latest = pendingSessionState ?: return
        pendingSessionState = null
        lastSessionStateSerializedAt = android.os.SystemClock.uptimeMillis()
        serializedSessionState = runCatching { latest.toString() }
            .getOrNull()
            ?.takeIf { it.length <= MAX_IN_MEMORY_SESSION_STATE_CHARS }
    }

    private fun recordSessionState(sessionState: GeckoSession.SessionState) {
        pendingSessionState = sessionState
        val now = android.os.SystemClock.uptimeMillis()
        val elapsed = now - lastSessionStateSerializedAt
        if (elapsed >= SESSION_STATE_SERIALIZE_INTERVAL_MS) {
            publishPendingSessionState()
            return
        }
        if (sessionStatePublishScheduled) return
        sessionStatePublishScheduled = true
        mainHandler.postDelayed({
            sessionStatePublishScheduled = false
            publishPendingSessionState()
        }, (SESSION_STATE_SERIALIZE_INTERVAL_MS - elapsed).coerceAtLeast(1L))
    }

    init {
        session.setPermissionDelegate(IlyroPermissionDelegate)
        session.setPromptDelegate(IlyroPromptDelegate)
        session.setTranslationsSessionDelegate(
            object : TranslationsController.SessionTranslation.Delegate {
                override fun onOfferTranslate(session: GeckoSession) {
                    mainHandler.post { this@BrowserTab.translationReady = true }
                }

                override fun onExpectedTranslate(session: GeckoSession) {
                    mainHandler.post { this@BrowserTab.translationReady = true }
                }

                override fun onTranslationStateChange(
                    session: GeckoSession,
                    translationState: TranslationsController.SessionTranslation.TranslationState?
                ) {
                    mainHandler.post {
                        this@BrowserTab.translationState = translationState
                        if (!translationState?.detectedLanguages?.docLangTag.isNullOrBlank()) {
                            this@BrowserTab.translationReady = true
                        }
                    }
                }
            }
        )
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                NativeBrowserHostCoordinator.coverUntilFirstPaint(session)
                if (!request.isRedirect && request.uri != url) {
                    urlBeforeCurrentLoad = url.takeIf { it != HOME_URL } ?: lastSuccessfulPageUrl
                }

                if (request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    val requestedDesktopMode = desktopModeForUrl(request.uri)
                    if (requestedDesktopMode != desktopModeEnabled) {
                        applyDesktopMode(requestedDesktopMode, reload = false)
                    }
                }

                // GeckoView's mobile UA/viewport is not always enough for wide Android tablets:
                // YouTube may still serve the desktop frontend at www.youtube.com. Keep canonical
                // YouTube navigation aligned with ILYRO's global page mode. Only direct app loads
                // and user-initiated navigations are rewritten: session-history traversal must
                // remain untouched or Back/Forward can become trapped on the rewritten entry.
                if (
                    request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW &&
                    (request.isDirectNavigation || request.hasUserGesture)
                ) {
                    preferredYouTubeUrl(request.uri, desktopModeEnabled)?.let { preferredUrl ->
                        session.loadUri(preferredUrl)
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }

                // Do not guess downloads from the URL. Let Gecko follow the real navigation,
                // redirects, cookies and JavaScript, then deliver the final non-renderable
                // response through ContentDelegate.onExternalResponse. This is GeckoView's
                // native download path and avoids saving intermediary HTML as .apk/.zip files.
                return null
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@BrowserTab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                this@BrowserTab.canGoForward = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val result = GeckoResult<GeckoSession>()
                onNewTabRequest(this@BrowserTab, uri, result)
                return result
            }
        })

        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@BrowserTab.title = if (url == HOME_URL) {
                    "New tab"
                } else {
                    title?.takeIf { it.isNotBlank() } ?: browserTabHostLabel(url)
                }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                if (!element.linkUri.isNullOrBlank()) {
                    onLinkContextMenu(this@BrowserTab, element)
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val returnUrl = urlBeforeCurrentLoad ?: lastSuccessfulPageUrl
                onDownload(response, returnUrl, isPrivate)
                if (!returnUrl.isNullOrBlank()) {
                    url = returnUrl
                }
                urlBeforeCurrentLoad = null
                loadProgress = 100
                isLoading = false
                isPullRefreshing = false
                pullDistance = 0f
            }

            override fun onCrash(session: GeckoSession) {
                recoverAfterProcessFailure(session)
            }

            override fun onKill(session: GeckoSession) {
                recoverAfterProcessFailure(session)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                this@BrowserTab.isFullScreen = fullScreen
            }
        })

        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                NativeBrowserHostCoordinator.coverUntilFirstPaint(session)
                MediaDetectorBridge.clear(session)
                this@BrowserTab.translationState = null
                this@BrowserTab.translationReady = false
                this@BrowserTab.pageReaderable = false
                this@BrowserTab.pageLanguage = null
                this@BrowserTab.readerArticle = null
                this@BrowserTab.readerLoading = false
                this@BrowserTab.url = url
                if (url == HOME_URL) {
                    title = "New tab"
                } else {
                    pageStartSequence += 1
                }
                pullDistance = 0f
                loadProgress = if (url == HOME_URL) 100 else 0
                isLoading = url != HOME_URL
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                loadProgress = progress.coerceIn(0, 100)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                loadProgress = 100
                isLoading = false
                isPullRefreshing = false
                pullDistance = 0f
                if (success && url != HOME_URL) {
                    lastSuccessfulPageUrl = url
                    urlBeforeCurrentLoad = null
                    loadSequence += 1
                    val metadataUrl = url
                    session.sessionPageExtractor.getPageMetadata().accept(
                        { metadata ->
                            mainHandler.post {
                                if (this@BrowserTab.url == metadataUrl && metadata != null) {
                                    this@BrowserTab.pageReaderable = metadata.isReaderable
                                    this@BrowserTab.pageLanguage = metadata.language
                                        .trim()
                                        .takeIf { it.isNotEmpty() }
                                }
                            }
                        },
                        { _ ->
                            mainHandler.post {
                                if (this@BrowserTab.url == metadataUrl) {
                                    this@BrowserTab.pageReaderable = false
                                }
                            }
                        }
                    )
                }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState
            ) {
                if (!isPrivate) {
                    recordSessionState(sessionState)
                }
            }
        })

        GeckoMediaSessionBridge.bind(session)
        PageGestureBridge.bind(session) { progress, refresh ->
            if (!isFullScreen && !isLoading) {
                pullDistance = progress
                if (refresh && !isPullRefreshing) {
                    isPullRefreshing = true
                    session.reload()
                }
            }
        }
        MediaDetectorBridge.bind(session)
        if (openSession) {
            openIfNeeded()
        }
    }

    /**
     * Opens a restored tab only when it becomes visible. Keeping the GeckoSession object and
     * delegates attached lets the tab retain its URL/session state without creating a Gecko
     * content surface for every background tab during cold start.
     */
    fun openIfNeeded() {
        if (sessionOpened || session.isOpen()) return
        sessionOpened = true
        try {
            session.open(runtime)
            ProtectionBridge.bindSession(session)
            val restored = if (!isPrivate && !restoredSessionState.isNullOrBlank()) {
                runCatching { GeckoSession.SessionState.fromString(restoredSessionState) }.getOrNull()
            } else {
                null
            }
            if (restored != null) {
                session.restoreState(restored)
            } else if (loadInitialUri) {
                session.loadUri(initialUrl)
            }
        } catch (error: Throwable) {
            sessionOpened = false
            throw error
        }
    }

    fun applyActiveState(active: Boolean) {
        if (!sessionOpened && !session.isOpen()) return
        session.setActive(active)
        session.setFocused(active)
        session.setPriorityHint(
            if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT
        )
    }

    fun setFocused(focused: Boolean) {
        if (sessionOpened || session.isOpen()) {
            session.setFocused(focused)
        }
    }

    fun flushSessionState() {
        if (!isPrivate && (sessionOpened || session.isOpen())) {
            session.flushSessionState()
        }
    }

    private var processRecoveryInProgress = false

    private fun recoverAfterProcessFailure(failedSession: GeckoSession) {
        if (failedSession !== session) return

        mainHandler.post {
            if (processRecoveryInProgress) return@post
            processRecoveryInProgress = true
            loadProgress = 0
            isLoading = true

            val restored = if (!isPrivate && !serializedSessionState.isNullOrBlank()) {
                runCatching {
                    GeckoSession.SessionState.fromString(serializedSessionState!!)
                }.getOrNull()
            } else {
                null
            }

            val recovered = runCatching {
                // onCrash/onKill can arrive while GeckoSession still reports itself open even
                // though its content process/compositor is gone. Recreate the native session
                // state unconditionally instead of reusing a potentially dead surface.
                if (failedSession.isOpen()) {
                    failedSession.close()
                }
                failedSession.open(runtime)
                ProtectionBridge.bindSession(failedSession)
                if (restored != null) {
                    failedSession.restoreState(restored)
                } else if (url != HOME_URL) {
                    failedSession.loadUri(url)
                }
                failedSession.setActive(true)
            }.isSuccess

            if (!recovered) {
                processRecoveryInProgress = false
                loadProgress = 0
                isLoading = false
            } else {
                mainHandler.postDelayed({
                    processRecoveryInProgress = false
                }, 1500L)
            }
        }
    }

    fun bindPopupSessionAfterOpen() {
        ProtectionBridge.bindSession(session)
    }

    fun applyDesktopMode(enabled: Boolean, reload: Boolean = true) {
        desktopModeEnabled = enabled
        session.settings.setUserAgentMode(
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        )
        session.settings.setViewportMode(
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        )
        if (reload && url != HOME_URL) {
            val preferredUrl = preferredYouTubeUrl(url, desktopModeEnabled)
            if (preferredUrl != null) session.loadUri(preferredUrl) else session.reload()
        }
    }

    fun isDesktopModeEnabled(): Boolean = desktopModeEnabled

    val storageContextId: String?
        get() = privateContextId

    fun clearPrivateStorage() {
        privateContextId?.let { runtime.storageController.clearDataForSessionContext(it) }
    }

    fun enterReaderMode(onError: (String) -> Unit) {
        val sourceUrl = url
        if (readerArticle != null || readerLoading || !isHttpPage(sourceUrl)) return
        readerLoading = true
        loadReaderArticle(
            session = session,
            url = sourceUrl,
            title = title,
            pageLanguage = pageLanguage,
            onSuccess = { article ->
                if (url == sourceUrl) {
                    readerArticle = article
                }
                readerLoading = false
            },
            onError = { message ->
                readerLoading = false
                onError(message)
            }
        )
    }

    fun exitReaderMode() {
        readerArticle = null
        readerLoading = false
    }

    fun close(clearPrivateData: Boolean = true) {
        PageGestureBridge.unbind(session)
        MediaDetectorBridge.unbind(session)
        GeckoMediaSessionBridge.unbind(session)
        ProtectionBridge.unbindSession(session)

        // This Handler belongs only to this tab. Once the undo window has expired and the tab is
        // really closed, no delayed state/recovery callback should keep the BrowserTab reachable.
        mainHandler.removeCallbacksAndMessages(null)
        pendingSessionState = null
        sessionStatePublishScheduled = false
        processRecoveryInProgress = false

        session.setTranslationsSessionDelegate(null)
        preview = null
        readerArticle = null
        translationState = null
        pageLanguage = null
        serializedSessionState = null
        sessionOpened = false
        runCatching { if (session.isOpen()) session.close() }
        if (isPrivate && clearPrivateData) {
            privateContextId?.let { runtime.storageController.clearDataForSessionContext(it) }
        }
    }
}
