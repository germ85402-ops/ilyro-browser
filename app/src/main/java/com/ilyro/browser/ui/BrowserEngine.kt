@file:OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)

package com.ilyro.browser.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

private const val YOUTUBE_PERF_EXTENSION_URI = "resource://android/assets/youtube-performance/"
private const val YOUTUBE_PERF_EXTENSION_ID = "youtube-performance@ilyro"
private const val MEDIA_FULLSCREEN_EXTENSION_URI = "resource://android/assets/media-fullscreen/"
private const val MEDIA_FULLSCREEN_EXTENSION_ID = "media-fullscreen@ilyro"
private const val MEDIA_DETECTOR_EXTENSION_URI = "resource://android/assets/media-detector/"
private const val MEDIA_DETECTOR_EXTENSION_ID = "media-detector@ilyro"
private const val AD_BLOCK_EXTENSION_URI = "resource://android/assets/ilyro-adblock/"
private const val AD_BLOCK_EXTENSION_ID = "adblock@ilyro"
private const val DARK_READER_EXTENSION_URI = "resource://android/assets/ilyro-darkreader/"
private const val DARK_READER_EXTENSION_ID = "darkreader@ilyro"
private const val ENGINE_LOG_TAG = "ILYRO-BrowserEngine"

internal object BrowserEngine {
    private fun preferredColorScheme(theme: BrowserTheme): Int = when (theme) {
        BrowserTheme.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        BrowserTheme.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        BrowserTheme.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
    }

    @Volatile
    private var runtime: GeckoRuntime? = null

    // MainActivity owns uiMode changes because it intentionally handles them without recreating
    // the Gecko host. Expose the current system appearance as Compose state so the browser UI,
    // Gecko runtime and already-open pages observe the same change immediately.
    var systemDarkTheme by mutableStateOf(false)
        private set

    fun updateSystemDarkTheme(configuration: Configuration) {
        val isDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (systemDarkTheme != isDark) {
            systemDarkTheme = isDark
        }
    }

    @Volatile
    private var adBlockingEnabled = true
    private var darkWebsitesEnabled = false
    private var adBlockExtension: WebExtension? = null
    private var darkReaderExtension: WebExtension? = null
    @Volatile
    private var pendingDarkReaderApplied: (() -> Unit)? = null

    var mediaDetectorReady by mutableStateOf(false)
        private set
    var mediaFullscreenReady by mutableStateOf(false)
        private set
    var darkReaderReady by mutableStateOf(false)
        private set

    // Do not restore or open the first tab until every page-affecting helper is active.
    // This barrier is especially important in minified release builds, which can reach navigation
    // before the asynchronous media-fullscreen enable callback otherwise completes.
    val startupExtensionsReady: Boolean
        get() = mediaFullscreenReady && mediaDetectorReady && darkReaderReady

    fun prepareForSettings(settings: BrowserSettings) {
        adBlockingEnabled = settings.adBlockingEnabled
        darkWebsitesEnabled = settings.darkWebsitesEnabled
    }

    fun getRuntime(context: Context, theme: BrowserTheme, requestedLocales: List<String>): GeckoRuntime {
        updateSystemDarkTheme(context.resources.configuration)
        runtime?.let { existing ->
            // A process-wide runtime can outlive a theme change. Refresh the values before any
            // caller gets the runtime so restored/new tabs never start with the previous scheme.
            existing.settings.setLocales(requestedLocales.toTypedArray())
            existing.settings.setPreferredColorScheme(preferredColorScheme(theme))
            return existing
        }
        return runtime ?: synchronized(this) {
            // Set the web preference before Gecko starts or any restored tab can load.
            val initialSettings = GeckoRuntimeSettings.Builder()
                .preferredColorScheme(preferredColorScheme(theme))
                .locales(requestedLocales.toTypedArray())
                .build()
            runtime ?: GeckoRuntime.create(context.applicationContext, initialSettings).also { created ->
                // Enable Gecko's readability fallback immediately for mobile sessions before tabs
                // begin loading. The Builder API in the bundled GeckoView does not expose this
                // runtime preference, but GeckoRuntimeSettings does.
                created.settings.setFontInflationEnabled(true)
                // GeckoView 150+ no longer starts a content child process eagerly. Preallocate it
                // here so the first real navigation does not pay that startup cost.
                created.warmUp()
                val controller = created.webExtensionController
                ProtectionBridge.initialize(created)
                // GeckoView keeps translations behind the Runtime AI feature gate.
                // Enable it explicitly so the session delegate and model downloads are reliable.
                controller.ensureBuiltIn(
                    YOUTUBE_PERF_EXTENSION_URI,
                    YOUTUBE_PERF_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            prepareHelperExtension(controller, extension)
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize YouTube performance helper", error) }
                )
                // The release build can reach the first page faster than debug. Treat the
                // fullscreen helper as part of the startup barrier so YouTube cannot load before
                // its fullscreen CSS/content script is enabled.
                mediaFullscreenReady = false
                controller.ensureBuiltIn(
                    MEDIA_FULLSCREEN_EXTENSION_URI,
                    MEDIA_FULLSCREEN_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            prepareHelperExtension(controller, extension) { stableExtension ->
                                PageGestureBridge.attach(stableExtension)
                                mediaFullscreenReady = true
                            }
                        } else {
                            // Gecko can still provide native fullscreen if the optional helper
                            // is unavailable, but the startup barrier must not wait forever.
                            mediaFullscreenReady = true
                        }
                    },
                    { error ->
                        Log.e(ENGINE_LOG_TAG, "Failed to initialize media fullscreen helper", error)
                        mediaFullscreenReady = true
                    }
                )
                mediaDetectorReady = false
                controller.ensureBuiltIn(
                    MEDIA_DETECTOR_EXTENSION_URI,
                    MEDIA_DETECTOR_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            prepareHelperExtension(controller, extension) { stableExtension ->
                                MediaDetectorBridge.attach(stableExtension)
                                mediaDetectorReady = true
                            }
                        } else {
                            mediaDetectorReady = true
                        }
                    },
                    { error ->
                        Log.e(ENGINE_LOG_TAG, "Failed to initialize media detector helper", error)
                        mediaDetectorReady = true
                    }
                )
                controller.ensureBuiltIn(
                    AD_BLOCK_EXTENSION_URI,
                    AD_BLOCK_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            withPrivateBrowsingAllowed(controller, extension) { stableExtension ->
                                adBlockExtension = stableExtension
                                // Attach the native bridge only after uBO has reached the requested
                                // enabled state. Attaching before controller.enable()/disable() can
                                // leave the already-connected native port orphaned until app restart.
                                applyAdBlockState(controller, stableExtension, adBlockingEnabled)
                            }
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize bundled ad blocker", error) }
                )
                darkReaderReady = false
                controller.ensureBuiltIn(
                    DARK_READER_EXTENSION_URI,
                    DARK_READER_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            withPrivateBrowsingAllowed(controller, extension) { stableExtension ->
                                darkReaderExtension = stableExtension
                                val pendingCallback = pendingDarkReaderApplied
                                pendingDarkReaderApplied = null
                                applyDarkReaderState(
                                    controller,
                                    stableExtension,
                                    darkWebsitesEnabled
                                ) {
                                    darkReaderReady = true
                                    pendingCallback?.invoke()
                                }
                            }
                        } else {
                            darkReaderReady = true
                        }
                    },
                    { error ->
                        Log.e(ENGINE_LOG_TAG, "Failed to initialize dark websites helper", error)
                        darkReaderReady = true
                        pendingDarkReaderApplied?.invoke()
                        pendingDarkReaderApplied = null
                    }
                )
                runtime = created
            }
        }
    }

    fun applyPreferredColorScheme(theme: BrowserTheme) {
        runtime?.settings?.setPreferredColorScheme(preferredColorScheme(theme))
    }

    fun prewarm(context: Context, settings: BrowserSettings) {
        prepareForSettings(settings)
        val currentRuntime = getRuntime(context, settings.theme, settings.preferredSiteLanguages)
        applyRuntimeSettings(settings)
        currentRuntime.warmUp()
    }

    fun applyRuntimeSettings(settings: BrowserSettings) {
        val currentRuntime = runtime ?: return
        currentRuntime.settings.setLocales(settings.preferredSiteLanguages.toTypedArray())
        currentRuntime.settings
            .setFontSizeFactor(settings.textScale)
            .setFontInflationEnabled(true)
            .setAllowInsecureConnections(
                if (settings.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL
            )
            .setGlobalPrivacyControl(settings.globalPrivacyControl)
            .setPreferredColorScheme(preferredColorScheme(settings.theme))
        currentRuntime.settings.contentBlocking
            .setCookieBehavior(
                if (settings.thirdPartyCookieIsolation) {
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
                } else {
                    ContentBlocking.CookieBehavior.ACCEPT_ALL
                }
            )
            .setCookieBehaviorPrivateMode(
                ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            )
    }

    private fun withPrivateBrowsingAllowed(
        controller: WebExtensionController,
        extension: WebExtension,
        onReady: (WebExtension) -> Unit
    ) {
        if (extension.metaData.allowedInPrivateBrowsing) {
            onReady(extension)
            return
        }
        controller.setAllowedInPrivateBrowsing(extension, true).accept(
            { updated -> onReady(updated ?: extension) },
            { error ->
                Log.e(ENGINE_LOG_TAG, "Failed to allow helper extension in private browsing", error)
                onReady(extension)
            }
        )
    }

    private fun applyHelperExtensionState(
        controller: WebExtensionController,
        extension: WebExtension,
        enabled: Boolean,
        onReady: (WebExtension) -> Unit
    ) {
        if (extension.metaData.enabled == enabled) {
            onReady(extension)
            return
        }
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { updated -> onReady(updated ?: extension) },
            { error ->
                Log.e(ENGINE_LOG_TAG, "Failed to change helper extension state", error)
                onReady(extension)
            }
        )
    }

    private fun prepareHelperExtension(
        controller: WebExtensionController,
        extension: WebExtension,
        onReady: (WebExtension) -> Unit = {}
    ) {
        withPrivateBrowsingAllowed(controller, extension) { privateReady ->
            applyHelperExtensionState(controller, privateReady, true, onReady)
        }
    }

    fun setAdBlockingEnabled(enabled: Boolean) {
        adBlockingEnabled = enabled
        val currentRuntime = runtime ?: return
        val extension = adBlockExtension ?: return
        applyAdBlockState(currentRuntime.webExtensionController, extension, enabled)
    }

    fun setDarkWebsitesEnabled(enabled: Boolean, onApplied: (() -> Unit)? = null) {
        darkWebsitesEnabled = enabled
        val currentRuntime = runtime
        if (currentRuntime == null || darkReaderExtension == null) {
            if (onApplied != null) {
                pendingDarkReaderApplied = onApplied
            }
            return
        }
        applyDarkReaderState(
            currentRuntime.webExtensionController,
            darkReaderExtension!!,
            enabled,
            onApplied
        )
    }

    private fun applyDarkReaderState(
        controller: WebExtensionController,
        extension: WebExtension,
        enabled: Boolean,
        onApplied: (() -> Unit)? = null
    ) {
        if (extension.metaData.enabled == enabled) {
            darkReaderExtension = extension
            onApplied?.invoke()
            return
        }
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { updated ->
                darkReaderExtension = updated ?: extension
                onApplied?.invoke()
            },
            { error ->
                Log.e(ENGINE_LOG_TAG, "Failed to change dark websites helper state", error)
                onApplied?.invoke()
            }
        )
    }

    private fun applyAdBlockState(
        controller: WebExtensionController,
        extension: WebExtension,
        enabled: Boolean
    ) {
        fun attachStableExtension(stableExtension: WebExtension) {
            adBlockExtension = stableExtension
            runtime?.let { currentRuntime ->
                ProtectionBridge.attach(currentRuntime, stableExtension)
            }
        }

        // Do not reload an already-correct uBO instance. Reloading it after the bridge has
        // connected can replace the WebExtension object while the native port still belongs to
        // the previous instance, leaving ILYRO Shield stuck on "Starting…" until app restart.
        if (extension.metaData.enabled == enabled) {
            attachStableExtension(extension)
            return
        }

        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { updated ->
                attachStableExtension(updated ?: extension)
            },
            { error ->
                Log.e(ENGINE_LOG_TAG, "Failed to change bundled ad blocker state", error)
            }
        )
    }
}
