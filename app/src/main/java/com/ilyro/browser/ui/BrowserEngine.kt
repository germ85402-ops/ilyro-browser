@file:OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)

package com.ilyro.browser.ui

import android.content.Context
import android.util.Log
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
    @Volatile
    private var adBlockingEnabled = true
    private var darkWebsitesEnabled = false
    private var adBlockExtension: WebExtension? = null
    private var darkReaderExtension: WebExtension? = null
    @Volatile
    private var pendingDarkReaderApplied: (() -> Unit)? = null

    fun getRuntime(context: Context, theme: BrowserTheme, requestedLocales: List<String>): GeckoRuntime {
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
                            applyHelperExtensionState(controller, extension, true)
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize YouTube performance helper", error) }
                )
                controller.ensureBuiltIn(
                    MEDIA_FULLSCREEN_EXTENSION_URI,
                    MEDIA_FULLSCREEN_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            applyHelperExtensionState(controller, extension, true)
                            PageGestureBridge.attach(extension)
                            controller.setAllowedInPrivateBrowsing(extension, true)
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize media fullscreen helper", error) }
                )
                controller.ensureBuiltIn(
                    MEDIA_DETECTOR_EXTENSION_URI,
                    MEDIA_DETECTOR_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            applyHelperExtensionState(controller, extension, true)
                            MediaDetectorBridge.attach(extension)
                            controller.setAllowedInPrivateBrowsing(extension, true)
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize media detector helper", error) }
                )
                controller.ensureBuiltIn(
                    AD_BLOCK_EXTENSION_URI,
                    AD_BLOCK_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            adBlockExtension = extension
                            ProtectionBridge.attach(created, extension)
                            controller.setAllowedInPrivateBrowsing(extension, true)
                            applyAdBlockState(controller, extension, adBlockingEnabled)
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize bundled ad blocker", error) }
                )
                controller.ensureBuiltIn(
                    DARK_READER_EXTENSION_URI,
                    DARK_READER_EXTENSION_ID
                ).accept(
                    { extension ->
                        if (extension != null) {
                            darkReaderExtension = extension
                            controller.setAllowedInPrivateBrowsing(extension, true)
                            val pendingCallback = pendingDarkReaderApplied
                            pendingDarkReaderApplied = null
                            applyDarkReaderState(
                                controller,
                                extension,
                                darkWebsitesEnabled,
                                pendingCallback
                            )
                        }
                    },
                    { error -> Log.e(ENGINE_LOG_TAG, "Failed to initialize dark websites helper", error) }
                )
                runtime = created
            }
        }
    }

    fun applyPreferredColorScheme(theme: BrowserTheme) {
        runtime?.settings?.setPreferredColorScheme(preferredColorScheme(theme))
    }

    fun prewarm(context: Context, settings: BrowserSettings) {
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

    private fun applyHelperExtensionState(
        controller: WebExtensionController,
        extension: WebExtension,
        enabled: Boolean
    ) {
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { _ -> },
            { error -> Log.e(ENGINE_LOG_TAG, "Failed to change helper extension state", error) }
        )
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
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { updated ->
                if (updated != null) darkReaderExtension = updated
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
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            { updated ->
                if (updated != null) {
                    adBlockExtension = updated
                    runtime?.let { currentRuntime ->
                        ProtectionBridge.attach(currentRuntime, updated)
                    }
                }
            },
            { error ->
                Log.e(ENGINE_LOG_TAG, "Failed to change bundled ad blocker state", error)
            }
        )
    }
}
