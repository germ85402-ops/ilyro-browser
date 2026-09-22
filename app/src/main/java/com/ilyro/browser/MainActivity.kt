package com.ilyro.browser

import android.Manifest
import android.content.SharedPreferences
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.ilyro.browser.passwords.PasswordManagerService
import com.ilyro.browser.ui.BookmarkStore
import com.ilyro.browser.ui.BrowserEngine
import com.ilyro.browser.ui.BrowserIconCache
import com.ilyro.browser.ui.BrowserSettingsStore
import com.ilyro.browser.ui.DownloadStateRecovery
import com.ilyro.browser.ui.HomeOmniboxTouchCoordinator
import com.ilyro.browser.ui.AddressOmniboxTouchCoordinator
import com.ilyro.browser.ui.GeckoMediaSessionBridge
import com.ilyro.browser.ui.IlyroApp
import com.ilyro.browser.ui.PasswordPromptCoordinator
import com.ilyro.browser.ui.QuickLinkStore
import com.ilyro.browser.ui.SiteFilePromptCoordinator
import com.ilyro.browser.ui.SitePermissionCoordinator
import java.util.ArrayDeque
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

internal const val ACTION_OPEN_DOWNLOADS = "com.ilyro.browser.action.OPEN_DOWNLOADS"

internal object BrowserUiCommandCoordinator {
    private var pendingOpenDownloads = false
    private var downloadsHandler: (() -> Unit)? = null

    fun openDownloads() {
        val callback = synchronized(this) {
            val active = downloadsHandler
            if (active == null) pendingOpenDownloads = true
            active
        }
        callback?.invoke()
    }

    fun bindDownloads(callback: () -> Unit): () -> Unit {
        val deliverPending = synchronized(this) {
            downloadsHandler = callback
            val pending = pendingOpenDownloads
            pendingOpenDownloads = false
            pending
        }
        if (deliverPending) callback()
        return {
            synchronized(this) {
                if (downloadsHandler === callback) downloadsHandler = null
            }
        }
    }
}

internal object ExternalNavigationCoordinator {
    private const val MAX_PENDING_URLS = 8
    private val pendingUrls = ArrayDeque<String>()
    private var handler: ((String) -> Unit)? = null

    fun submit(rawUrl: String?) {
        val target = rawUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> runCatching { Uri.parse(value) }.getOrNull() }
            ?.takeIf { uri ->
                val scheme = uri.scheme?.lowercase()
                scheme == "http" || scheme == "https"
            }
            ?.toString()
            ?: return

        val currentHandler = synchronized(this) {
            val activeHandler = handler
            if (activeHandler == null) {
                if (pendingUrls.peekLast() != target) {
                    while (pendingUrls.size >= MAX_PENDING_URLS) {
                        pendingUrls.removeFirst()
                    }
                    pendingUrls.addLast(target)
                }
            }
            activeHandler
        }

        currentHandler?.invoke(target)
    }

    fun bind(callback: (String) -> Unit): () -> Unit {
        val queued = synchronized(this) {
            handler = callback
            buildList {
                while (pendingUrls.isNotEmpty()) add(pendingUrls.removeFirst())
            }
        }
        queued.forEach(callback)
        return {
            synchronized(this) {
                if (handler === callback) handler = null
            }
        }
    }
}

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var browserPrefs: SharedPreferences
    private var externalMediaHandoffPending = false
    private var externalMediaRestoreScheduled = false
    private var mediaWindowFocusRestoreScheduled = false
    private var lostFocusDuringMediaPlayback = false
    private var lastDisplayConfigurationKey = ""

    private val sitePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            SitePermissionCoordinator.onAndroidPermissionsResult(grants)
        }
    private val siteFilePromptLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            SiteFilePromptCoordinator.onActivityResult(result.resultCode, result.data)
        }
    private val siteFilePromptCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            SiteFilePromptCoordinator.onCameraPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ILYRO_Starting)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lastDisplayConfigurationKey = displayConfigurationKey(resources.configuration)

        // Avoid double IME resizing on edge-to-edge Android 11+: Compose animates the
        // keyboard inset itself. Pre-R devices keep the proven adjustResize fallback.
        window.setSoftInputMode(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        )


        browserPrefs = getSharedPreferences("ilyro_browser", MODE_PRIVATE)

        // Warm GeckoView and bundled extensions before Compose restores the first tab.
        // The call is idempotent; onboarding will apply any newly selected settings later.
        runCatching {
            BrowserEngine.prewarm(
                applicationContext,
                BrowserSettingsStore.restore(browserPrefs)
            )
        }
        DownloadStateRecovery.recoverInterruptedDirectDownloadsOnce(applicationContext, browserPrefs)
        PasswordManagerService.prepare(applicationContext)
        BrowserIconCache.initialize(
            applicationContext,
            QuickLinkStore.restore(browserPrefs).map { it.url }
        )
        browserPrefs.registerOnSharedPreferenceChangeListener(this)
        SitePermissionCoordinator.bindAndroidPermissionLauncher(applicationContext) { permissions ->
            sitePermissionLauncher.launch(permissions)
        }
        SiteFilePromptCoordinator.bind(
            context = applicationContext,
            launchIntent = { intent -> siteFilePromptLauncher.launch(intent) },
            requestCameraPermission = {
                siteFilePromptCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
        PasswordPromptCoordinator.bind(this)
        applySystemChrome()

        NativeBrowserHost.install(
            this,
            initialTransitionColor = initialBrowserTransitionColor()
        ) { IlyroApp() }
        handleBrowserUiIntent(intent)

        val relaunchedFromHistory =
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!relaunchedFromHistory) handleExternalNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBrowserUiIntent(intent)
        handleExternalNavigationIntent(intent)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        val externalMedia = isExternalMediaIntent(intent)
        if (externalMedia) {
            externalMediaHandoffPending = true
            NativeBrowserHostCoordinator.markExternalMediaHandoff()
        }

        val adjusted = if (externalMedia && intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) {
            Intent(intent).apply { flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv() }
        } else {
            intent
        }

        try {
            super.startActivity(adjusted, options)
        } catch (error: Throwable) {
            if (externalMedia) {
                externalMediaHandoffPending = false
                NativeBrowserHostCoordinator.clearExternalMediaHandoff()
            }
            throw error
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        GeckoMediaSessionBridge.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            HomeOmniboxTouchCoordinator.onWindowTouchDown(event.x, event.y)
            AddressOmniboxTouchCoordinator.onWindowTouchDown(event.x, event.y)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (::browserPrefs.isInitialized) {
            if (BookmarkStore.consumeExternalImportRefresh(browserPrefs)) {
                recreate()
                return
            }
            applySystemChrome()
            window.decorView.post { applySystemChrome() }
            resumePendingApkInstallIfAllowed()
        }
        if (externalMediaHandoffPending && hasWindowFocus()) {
            scheduleExternalMediaRestore()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && GeckoMediaSessionBridge.hasPlayingPlayback()) {
            lostFocusDuringMediaPlayback = true
        }
        if (hasFocus && ::browserPrefs.isInitialized) {
            applySystemChrome()
            window.decorView.post { applySystemChrome() }
        }
        if (hasFocus && externalMediaHandoffPending) {
            lostFocusDuringMediaPlayback = false
            scheduleExternalMediaRestore()
        } else if (hasFocus && lostFocusDuringMediaPlayback) {
            lostFocusDuringMediaPlayback = false
            scheduleMediaWindowFocusRestore()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val previousDisplayKey = lastDisplayConfigurationKey
        val nextDisplayKey = displayConfigurationKey(newConfig)
        lastDisplayConfigurationKey = nextDisplayKey
        BrowserEngine.updateSystemDarkTheme(newConfig)
        if (::browserPrefs.isInitialized) {
            applySystemChrome()
            window.decorView.post { applySystemChrome() }
            if (
                previousDisplayKey.isNotBlank() &&
                    previousDisplayKey != nextDisplayKey &&
                    GeckoMediaSessionBridge.hasPlayingPlayback()
            ) {
                // GeckoView already owns its compositor across handled configuration changes.
                // Recreating the display here can momentarily detach the video surface and make
                // YouTube pause/buffer. Keep the surface attached and only refresh layout/insets.
                window.decorView.post {
                    if (!hasWindowFocus()) return@post
                    NativeBrowserHostCoordinator.restoreVisible()
                    restoreAttachedGeckoSurface(window.decorView)
                }
            }
        }
    }

    override fun onDestroy() {
        PasswordPromptCoordinator.unbind(this)
        SiteFilePromptCoordinator.unbind()
        SitePermissionCoordinator.unbindAndroidPermissionLauncher()
        if (::browserPrefs.isInitialized) {
            browserPrefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        NativeBrowserHostCoordinator.detach()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "settings_theme") {
            applySystemChrome()
            window.decorView.post { applySystemChrome() }
        }
    }

    private fun isExternalMediaIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val scheme = intent.data?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val mime = intent.type?.lowercase().orEmpty()
        return mime.startsWith("video/") ||
            mime.startsWith("audio/") ||
            mime == "application/vnd.apple.mpegurl" ||
            mime == "application/x-mpegurl" ||
            mime == "application/dash+xml"
    }

    private fun scheduleExternalMediaRestore() {
        if (externalMediaRestoreScheduled) return
        externalMediaRestoreScheduled = true
        fun restoreAfter(delayMs: Long, remainingAttempts: Int) {
            window.decorView.postDelayed({
                if (!externalMediaHandoffPending || !hasWindowFocus()) {
                    externalMediaRestoreScheduled = false
                    return@postDelayed
                }

                NativeBrowserHostCoordinator.recreateDisplay()
                NativeBrowserHostCoordinator.restoreVisible()
                restoreAttachedGeckoSurface(window.decorView)

                if (remainingAttempts == 0) {
                    externalMediaRestoreScheduled = false
                    NativeBrowserHostCoordinator.clearExternalMediaHandoff()
                    externalMediaHandoffPending = false
                } else {
                    restoreAfter(280L, remainingAttempts - 1)
                }
            }, delayMs)
        }
        restoreAfter(140L, remainingAttempts = 1)
    }

    private fun scheduleMediaWindowFocusRestore() {
        if (mediaWindowFocusRestoreScheduled || externalMediaHandoffPending) return
        mediaWindowFocusRestoreScheduled = true
        window.decorView.postDelayed({
            mediaWindowFocusRestoreScheduled = false
            if (!hasWindowFocus()) return@postDelayed
            NativeBrowserHostCoordinator.restoreVisible()
            restoreAttachedGeckoSurface(window.decorView)
            GeckoMediaSessionBridge.resumeSelectedPlaybackIfNeeded()
        }, 140L)
    }

    private fun displayConfigurationKey(configuration: Configuration): String =
        listOf(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.smallestScreenWidthDp
        ).joinToString(":")

    /**
     * Restore only the Gecko compositor after Android changed window focus/orientation.
     *
     * Do not call GeckoSession.setFocused(true) here. Session focus is input focus, not render
     * visibility; forcing it during fullscreen restoration can re-focus a previously editable
     * element and make Android reopen the IME. BrowserScreen owns normal tab focus state.
     */
    private fun restoreAttachedGeckoSurface(view: View) {
        if (view is GeckoView) {
            view.getSession()?.let { session ->
                runCatching {
                    session.setActive(true)
                    session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
                    view.requestLayout()
                    ViewCompat.requestApplyInsets(view)
                    view.invalidate()
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                restoreAttachedGeckoSurface(view.getChildAt(index))
            }
        }
    }

    private fun handleBrowserUiIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != ACTION_OPEN_DOWNLOADS) return
        BrowserUiCommandCoordinator.openDownloads()
        incomingIntent.action = null
    }

    private fun handleExternalNavigationIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != Intent.ACTION_VIEW) return
        ExternalNavigationCoordinator.submit(incomingIntent.dataString)
    }

    private fun resumePendingApkInstallIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) return

        val pendingUri = browserPrefs.getString("pending_apk_install_uri_v1", null) ?: return
        browserPrefs.edit().remove("pending_apk_install_uri_v1").apply()
        val uri = runCatching { Uri.parse(pendingUri) }.getOrNull() ?: return
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(installIntent) }
    }

    private fun applySystemChrome() {
        val dark = resolvedDarkTheme()
        val barColor = if (dark) Color.rgb(0x10, 0x1B, 0x33) else Color.WHITE
        val style = if (dark) {
            SystemBarStyle.dark(barColor)
        } else {
            SystemBarStyle.light(barColor, barColor)
        }

        enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun resolvedDarkTheme(): Boolean {
        val configuredTheme = browserPrefs.getString("settings_theme", "SYSTEM") ?: "SYSTEM"
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return when (configuredTheme) {
            "LIGHT" -> false
            "DARK" -> true
            else -> systemDark
        }
    }

    private fun initialBrowserTransitionColor(): Int =
        if (resolvedDarkTheme()) {
            Color.rgb(0x07, 0x12, 0x25)
        } else {
            Color.rgb(0xF7, 0xF8, 0xFA)
        }

}
