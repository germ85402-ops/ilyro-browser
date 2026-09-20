@file:OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)

package com.ilyro.browser.ui

import com.ilyro.browser.sync.BrowserAutoSyncScheduler

import androidx.compose.material.icons.automirrored.rounded.ArrowForward

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import android.Manifest
import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebResponse
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ilyro.browser.ExternalNavigationCoordinator
import com.ilyro.browser.NativeBrowserHostCoordinator
import com.ilyro.browser.BrowserUiCommandCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal const val HOME_URL = "about:blank"
private const val PREFS_NAME = "ilyro_browser"
private const val PREF_RESTORED_WEB_COLOR_SCHEME = "restored_web_color_scheme_v1"
private const val PREF_LAST_URL = "last_url"
private const val PREF_ONBOARDING_COMPLETE = "onboarding_complete_v1"
private const val PREF_PENDING_ONBOARDING_EXTENSIONS = "pending_onboarding_extensions_v1"
private const val PREF_DOWNLOAD_NOTIFICATION_ASKED = "download_notification_asked_v1"
private const val DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST = 1701
private const val YOUTUBE_PERF_EXTENSION_URI = "resource://android/assets/youtube-performance/"
private const val YOUTUBE_PERF_EXTENSION_ID = "youtube-performance@ilyro"
private const val MEDIA_FULLSCREEN_EXTENSION_URI = "resource://android/assets/media-fullscreen/"
private const val MEDIA_FULLSCREEN_EXTENSION_ID = "media-fullscreen@ilyro"
private const val MEDIA_DETECTOR_EXTENSION_URI = "resource://android/assets/media-detector/"
private const val MEDIA_DETECTOR_EXTENSION_ID = "media-detector@ilyro"
private const val AD_BLOCK_EXTENSION_URI = "resource://android/assets/ilyro-adblock/"
private const val AD_BLOCK_EXTENSION_ID = "adblock@ilyro"

internal enum class BrowserTopNoticeKind { FULLSCREEN, DOWNLOAD, TAB_CLOSED }

internal data class BrowserTopNotice(
    val id: Long,
    val kind: BrowserTopNoticeKind,
    val title: String,
    val message: String,
    val actionLabel: String? = null
)

private data class PendingNewTabRequest(
    val sourceTabId: String,
    val uri: String,
    val result: GeckoResult<GeckoSession>
)

private data class LinkContextMenuRequest(
    val sourceTabId: String,
    val url: String,
    val title: String?
)

private fun ensureDownloadNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = context.findActivity() ?: return
    if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) return

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getBoolean(PREF_DOWNLOAD_NOTIFICATION_ASKED, false)) return
    prefs.edit().putBoolean(PREF_DOWNLOAD_NOTIFICATION_ASKED, true).apply()
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST
    )
}

@Composable
fun IlyroApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var settings by remember { mutableStateOf(BrowserSettingsStore.restore(prefs)) }

    LaunchedEffect(Unit) {
        BrowserAutoSyncScheduler.ensureScheduled(context.applicationContext)
    }

    LaunchedEffect(settings.appIcon) {
        if (!AppIconManager.apply(context, settings.appIcon)) {
            Toast.makeText(context, "Не удалось обновить иконку приложения", Toast.LENGTH_LONG).show()
        }
    }

    val existingInstall = remember {
        prefs.contains(PREF_LAST_URL) ||
            prefs.contains("settings_search_engine") ||
            prefs.contains("settings_theme")
    }
    var onboardingComplete by remember {
        mutableStateOf(prefs.getBoolean(PREF_ONBOARDING_COMPLETE, existingInstall))
    }
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (settings.theme) {
        BrowserTheme.SYSTEM -> systemDarkTheme
        BrowserTheme.LIGHT -> false
        BrowserTheme.DARK -> true
    }
    val wallpaperAccent = rememberWallpaperAccent(settings, darkTheme)
    val accentColor = wallpaperAccent.resolveAccent(darkTheme)
    val accentOnColor = wallpaperAccent.resolveOnAccent(darkTheme)
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = accentOnColor,
            primaryContainer = Color(0xFF263A70),
            onPrimaryContainer = Color(0xFFF4F4F5),
            secondary = Color(0xFFB9C5E6),
            onSecondary = Color(0xFF0B1326),
            secondaryContainer = Color(0xFF1B2947),
            onSecondaryContainer = Color(0xFFF2F5FF),
            tertiary = Color(0xFFB9C5E6),
            onTertiary = Color(0xFF0B1326),
            tertiaryContainer = Color(0xFF223253),
            onTertiaryContainer = Color(0xFFF2F5FF),
            background = Color(0xFF071225),
            onBackground = Color(0xFFF4F4F5),
            surface = Color(0xFF101B33),
            onSurface = Color(0xFFF4F4F5),
            surfaceVariant = Color(0xFF1B2947),
            onSurfaceVariant = Color(0xFFAAB7D4),
            outline = Color(0xFF657CA9),
            outlineVariant = Color(0xFF334466),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            inverseSurface = Color(0xFFE3E2E6),
            inverseOnSurface = Color(0xFF303034),
            inversePrimary = accentColor,
            scrim = Color.Black,
            surfaceTint = Color.Transparent
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = accentOnColor,
            primaryContainer = Color(0xFFE8EBF0),
            onPrimaryContainer = Color(0xFF15171A),
            secondary = Color(0xFF555B63),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE7E9ED),
            onSecondaryContainer = Color(0xFF1B1D20),
            tertiary = Color(0xFF5B6068),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE9EBEF),
            onTertiaryContainer = Color(0xFF1B1D20),
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF15171A),
            surface = Color.White,
            onSurface = Color(0xFF15171A),
            surfaceVariant = Color(0xFFF0F2F5),
            onSurfaceVariant = Color(0xFF62676F),
            outline = Color(0xFF8B9097),
            outlineVariant = Color(0xFFD8DBE0),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            inverseSurface = Color(0xFF303034),
            inverseOnSurface = Color(0xFFF2F0F4),
            inversePrimary = accentColor,
            scrim = Color.Black,
            surfaceTint = Color.Transparent
        )
    }

    SideEffect {
        context.findActivity()?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val updateSettings: (BrowserSettings) -> Unit = { updated ->
        val iconChanged = settings.appIcon != updated.appIcon
        val themeChanged = settings.theme != updated.theme
        if (themeChanged) {
            // Keep Gecko's own SYSTEM mode intact. This avoids resolving the Android theme twice
            // during a cold start and lets Gecko track system appearance directly.
            BrowserEngine.applyPreferredColorScheme(updated.theme)
        }
        settings = updated
        BrowserSettingsStore.save(prefs, updated)
        if (iconChanged) {
            prefs.edit()
                .putString("settings_app_icon", updated.appIcon.name)
                .commit()
        }
    }

    // First install: initialize and preallocate Gecko while onboarding is visible so the first
    // real navigation does not also have to start the runtime/content process.
    LaunchedEffect(onboardingComplete) {
        if (!onboardingComplete) {
            BrowserEngine.prewarm(context, settings)
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (onboardingComplete) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            CompositionLocalProvider(
                LocalIlyroLanguage provides resolveAppLanguage(settings.language),
                LocalAppIcon provides settings.appIcon,
                LocalIlyroUiDensity provides settings.uiDensity
            ) {
                if (onboardingComplete) {
                    BrowserScreen(
                        settings = settings,
                        darkTheme = darkTheme,
                        onSettingsChange = updateSettings
                    )
                } else {
                    OnboardingScreen(
                        settings = settings,
                        onSettingsChange = updateSettings,
                        onFinish = { _ ->
                            // Guarantee that the runtime is fully configured with the selected
                            // theme before BrowserScreen opens its first GeckoSession.
                            BrowserEngine.prewarm(context, settings)
                            prefs.edit()
                                .putBoolean(PREF_ONBOARDING_COMPLETE, true)
                                .remove(PREF_PENDING_ONBOARDING_EXTENSIONS)
                                .apply()
                            onboardingComplete = true
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    settings: BrowserSettings,
    darkTheme: Boolean,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val browserScope = rememberCoroutineScope()
    val showTabletTabStrip = configuration.smallestScreenWidthDp >= 600
    val runtime = remember {
        BrowserEngine.prepareForSettings(settings)
        BrowserEngine.getRuntime(context, settings.theme, settings.preferredSiteLanguages)
    }
    SideEffect {
        BrowserEngine.applyPreferredColorScheme(settings.theme)
    }

    // Do not restore/open web sessions until bundled extensions have reached their requested
    // startup state. This prevents a previously-enabled Dark Reader from touching pages for a
    // moment before the saved "off" setting is applied, and guarantees the media detector is
    // attached to the final WebExtension instance before YouTube starts loading.
    if (!BrowserEngine.startupExtensionsReady) {
        // Keep this placeholder transparent: GeckoView is Activity-owned and the project
        // contract forbids an opaque BrowserScreen root from covering it.
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val downloadController = remember(runtime) { DownloadController(context, prefs, runtime) }
    val downloads = remember { mutableStateListOf<DownloadUiItem>() }
    fun isDownloadActive(item: DownloadUiItem): Boolean = when (item.status) {
        android.app.DownloadManager.STATUS_PENDING,
        android.app.DownloadManager.STATUS_RUNNING,
        android.app.DownloadManager.STATUS_PAUSED -> true
        else -> false
    }

    val overlayState = remember { BrowserOverlayState() }
    var showDownloads by remember(overlayState) { overlayState.flag(BrowserOverlay.DOWNLOADS) }
    var topNotice by remember { mutableStateOf<BrowserTopNotice?>(null) }
    var renderedTopNotice by remember { mutableStateOf<BrowserTopNotice?>(null) }
    var pendingClosedTab by remember { mutableStateOf<BrowserTab?>(null) }
    var pendingClosedIndex by remember { mutableStateOf(-1) }
    var pendingClosedWasActive by remember { mutableStateOf(false) }
    var pendingClosedReplacementId by remember { mutableStateOf<String?>(null) }
    var pendingClosedClearPrivateData by remember { mutableStateOf(false) }
    var pendingClosedToken by remember { mutableStateOf(0L) }
    var pendingClosedAllTabs by remember { mutableStateOf<List<BrowserTab>>(emptyList()) }
    var pendingClosedAllActiveId by remember { mutableStateOf<String?>(null) }
    var pendingClosedAllReplacementId by remember { mutableStateOf<String?>(null) }
    var pendingClosedAllClearPrivateData by remember { mutableStateOf(false) }
    var pendingClosedAllToken by remember { mutableStateOf(0L) }
    var lastRestoredTabId by remember { mutableStateOf<String?>(null) }
    var tabRestoreGeneration by remember { mutableStateOf(0L) }

    fun pushTopNotice(
        kind: BrowserTopNoticeKind,
        title: String,
        message: String,
        actionLabel: String? = null
    ) {
        topNotice = BrowserTopNotice(
            id = System.nanoTime(),
            kind = kind,
            title = title,
            message = message,
            actionLabel = actionLabel
        )
    }

    var downloadsActive by remember {
        mutableStateOf(DownloadKeepAliveService.hasActiveDownloads())
    }

    fun applyDownloadsSnapshot(latest: List<DownloadUiItem>) {
        downloads.clear()
        downloads.addAll(latest)
        downloadsActive = latest.any(::isDownloadActive) ||
            DownloadKeepAliveService.hasActiveDownloads()
    }

    val refreshDownloads: () -> Unit = {
        browserScope.launch {
            val latest = withContext(Dispatchers.IO) {
                runCatching { downloadController.snapshot() }.getOrDefault(emptyList())
            }
            applyDownloadsSnapshot(latest)
        }
    }

    LaunchedEffect(downloadController) {
        val latest = withContext(Dispatchers.IO) {
            runCatching { downloadController.snapshot() }.getOrDefault(emptyList())
        }
        applyDownloadsSnapshot(latest)
    }
    var pendingMoveDownload by remember { mutableStateOf<DownloadUiItem?>(null) }

    val moveDownloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination ->
        val item = pendingMoveDownload
        pendingMoveDownload = null
        if (item == null || destination == null) return@rememberLauncherForActivityResult
        val moved = downloadController.moveTo(item, destination)
        refreshDownloads()
        Toast.makeText(
            context,
            if (moved) {
                tr(settings.language, "File moved", "Файл перемещён")
            } else {
                tr(settings.language, "Couldn't move file", "Не удалось переместить файл")
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    val markDownloadsActive: () -> Unit = {
        downloadsActive = true
    }

    DisposableEffect(downloadController) {
        val unbind = BrowserUiCommandCoordinator.bindDownloads {
            refreshDownloads()
            showDownloads = true
        }
        onDispose { unbind() }
    }

    LaunchedEffect(downloadsActive, showDownloads) {
        while (downloadsActive || showDownloads) {
            delay(if (showDownloads) 500L else 650L)
            val latest = withContext(Dispatchers.IO) {
                runCatching { downloadController.snapshot() }.getOrDefault(emptyList())
            }
            applyDownloadsSnapshot(latest)
        }
    }

    LaunchedEffect(topNotice?.id) {
        val notice = topNotice
        if (notice == null) {
            delay(220L)
            renderedTopNotice = null
        } else {
            renderedTopNotice = notice
            delay(4_000L)
            if (topNotice?.id == notice.id) {
                topNotice = null
            }
        }
    }

    LaunchedEffect(renderedTopNotice?.id) {
        if (renderedTopNotice == null) {
            NativeBrowserHostCoordinator.clearInputExclusion()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NativeBrowserHostCoordinator.clearInputExclusion()
        }
    }
    val currentSettings by rememberUpdatedState(settings)
    val onDownloadResponse: (WebResponse, String?, Boolean) -> Unit = { response, referrer, isPrivate ->
        ensureDownloadNotificationPermission(context)
        val record = downloadController.enqueue(
            response = response,
            allowMetered = currentSettings.downloadsOverMetered,
            referrer = referrer,
            isPrivate = isPrivate
        )
        refreshDownloads()
        if (record != null) {
            markDownloadsActive()
            pushTopNotice(
                kind = BrowserTopNoticeKind.DOWNLOAD,
                title = tr(currentSettings.language, "Download started", "Загрузка началась"),
                message = record.fileName,
                actionLabel = tr(currentSettings.language, "View download", "Показать загрузку")
            )
        } else {
            Toast.makeText(
                context,
                tr(currentSettings.language, "Couldn't start download", "Не удалось начать загрузку"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val onDownloadNavigation: (String, String?, Boolean, GeckoSessionSettings) -> Unit =
        { url, referrer, isPrivate, sourceSettings ->
            ensureDownloadNotificationPermission(context)
            val started = downloadController.enqueueNavigationWithSession(
                url = url,
                sourceSettings = sourceSettings,
                allowMetered = currentSettings.downloadsOverMetered,
                referrer = referrer,
                isPrivate = isPrivate,
                onRecordsChanged = refreshDownloads
            )
            refreshDownloads()
            if (started) {
                markDownloadsActive()
                val fileLabel = runCatching {
                    Uri.decode(Uri.parse(url).lastPathSegment.orEmpty())
                }.getOrDefault("").takeIf { it.isNotBlank() }
                    ?: tr(currentSettings.language, "Preparing file…", "Подготавливаем файл…")
                pushTopNotice(
                    kind = BrowserTopNoticeKind.DOWNLOAD,
                    title = tr(currentSettings.language, "Download started", "Загрузка началась"),
                    message = fileLabel,
                    actionLabel = tr(currentSettings.language, "Open", "Открыть")
                )
            } else {
                Toast.makeText(
                    context,
                    tr(currentSettings.language, "Couldn't start download", "Не удалось начать загрузку"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    val legacyUrl = remember { prefs.getString(PREF_LAST_URL, HOME_URL) ?: HOME_URL }
    val restoredSession = remember {
        if (settings.restoreTabs) {
            TabSessionStore.restore(prefs, legacyUrl)
        } else {
            RestoredTabSession(urls = listOf(HOME_URL), states = listOf(null), metadata = listOf(TabSessionMetadata()), activeIndex = 0)
        }
    }
    val desiredWebColorScheme = if (darkTheme) "dark" else "light"
    val forceFreshRestoredWebContent = remember {
        // SessionState can contain a document created under the previous color scheme.
        // Reopen its URL once when the scheme changes; subsequent launches keep fast restores.
        prefs.getString(PREF_RESTORED_WEB_COLOR_SCHEME, null) != desiredWebColorScheme
    }
    LaunchedEffect(desiredWebColorScheme) {
        prefs.edit().putString(PREF_RESTORED_WEB_COLOR_SCHEME, desiredWebColorScheme).apply()
    }

    val restoredBookmarks = remember { BookmarkStore.restore(prefs) }
    val restoredHistory = remember { HistoryStore.restore(prefs) }

    DisposableEffect(downloadController) {
        ExtensionHostBridge.initialize(runtime)
        ExtensionHostBridge.setDownloadHandler { _, request ->
            ensureDownloadNotificationPermission(context)
            val result = downloadController.enqueueExtensionDownload(
                request = request,
                allowMetered = currentSettings.downloadsOverMetered,
                onRecordsChanged = refreshDownloads
            )
            markDownloadsActive()
            val requestedName = request.filename
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() }
                ?: tr(currentSettings.language, "Preparing file…", "Подготавливаем файл…")
            pushTopNotice(
                kind = BrowserTopNoticeKind.DOWNLOAD,
                title = tr(currentSettings.language, "Download started", "Загрузка началась"),
                message = requestedName,
                actionLabel = tr(currentSettings.language, "Open", "Открыть")
            )
            result
        }
        onDispose {
            ExtensionHostBridge.setDownloadHandler(null)
            ExtensionHostBridge.closePopup()
        }
    }

    LaunchedEffect(settings.adBlockingEnabled) {
        BrowserEngine.setAdBlockingEnabled(settings.adBlockingEnabled)
    }

    LaunchedEffect(
        settings.preferredSiteLanguages,
        settings.textScale,
        settings.httpsOnly,
        settings.thirdPartyCookieIsolation,
        settings.globalPrivacyControl,
        settings.theme,
        darkTheme
    ) {
        BrowserEngine.applyRuntimeSettings(settings)
    }

    LaunchedEffect(Unit) {
        prefs.edit().remove(PREF_PENDING_ONBOARDING_EXTENSIONS).apply()
        ProtectionBridge.removeUnsupportedUserExtensions()
    }

    var pendingNewTabRequest by remember { mutableStateOf<PendingNewTabRequest?>(null) }
    val handleNewTabRequest: (BrowserTab, String, GeckoResult<GeckoSession>) -> Unit =
        { sourceTab, uri, result ->
            pendingNewTabRequest?.let { previous ->
                runCatching { previous.result.complete(null) }
            }
            pendingNewTabRequest = PendingNewTabRequest(sourceTab.id, uri, result)
        }
    var linkContextMenu by remember { mutableStateOf<LinkContextMenuRequest?>(null) }
    val handleLinkContextMenu: (BrowserTab, GeckoSession.ContentDelegate.ContextElement) -> Unit =
        { sourceTab, element ->
            element.linkUri?.takeIf { it.isNotBlank() }?.let { linkUrl ->
                linkContextMenu = LinkContextMenuRequest(
                    sourceTabId = sourceTab.id,
                    url = linkUrl,
                    title = element.linkText?.takeIf { it.isNotBlank() }
                        ?: element.title?.takeIf { it.isNotBlank() }
                )
            }
        }

    val tabs = remember(runtime) {
        mutableStateListOf<BrowserTab>().apply {
            restoredSession.urls.forEachIndexed { index, url ->
                add(
                    BrowserTab(
                        runtime = runtime,
                        initialUrl = url,
                        initialPinned = restoredSession.metadata.getOrNull(index)?.pinned == true,
                        initialGroup = restoredSession.metadata.getOrNull(index)?.group,
                        settings = settings,
                        desktopModeForUrl = { target -> SiteDesktopModeStore.effective(prefs, target, currentSettings.desktopMode) },
                        onDownload = onDownloadResponse,
                        onDownloadNavigation = onDownloadNavigation,
                        onNewTabRequest = handleNewTabRequest,
                        onLinkContextMenu = handleLinkContextMenu,
                        restoredSessionState = if (forceFreshRestoredWebContent) {
                            null
                        } else {
                            restoredSession.states.getOrNull(index)
                        },
                        openSession = index == restoredSession.activeIndex
                    )
                )
            }
        }
    }
    val bookmarks = remember {
        mutableStateListOf<BookmarkItem>().apply {
            addAll(restoredBookmarks.sortedByDescending { it.createdAt })
        }
    }
    val history = remember {
        mutableStateListOf<HistoryItem>().apply {
            addAll(restoredHistory)
        }
    }

    var activeTabId by remember {
        mutableStateOf(tabs[restoredSession.activeIndex].id)
    }
    var showTabs by remember(overlayState) { overlayState.flag(BrowserOverlay.TABS) }
    var showMenu by remember(overlayState) { overlayState.flag(BrowserOverlay.MENU) }
    var showBookmarks by remember(overlayState) { overlayState.flag(BrowserOverlay.BOOKMARKS) }
    var showHistory by remember(overlayState) { overlayState.flag(BrowserOverlay.HISTORY) }
    var showMedia by remember(overlayState) { overlayState.flag(BrowserOverlay.MEDIA) }
    var showSettings by remember(overlayState) { overlayState.flag(BrowserOverlay.SETTINGS) }
    var showProtection by remember(overlayState) { overlayState.flag(BrowserOverlay.PROTECTION) }
    var showFindInPage by remember(overlayState) { overlayState.flag(BrowserOverlay.FIND_IN_PAGE) }
    var showTranslation by remember(overlayState) { overlayState.flag(BrowserOverlay.TRANSLATION) }
    var sitePermissions by remember { mutableStateOf<List<GeckoSession.PermissionDelegate.ContentPermission>>(emptyList()) }
    var siteDesktopRevision by remember { mutableIntStateOf(0) }
    var addressFocused by remember { mutableStateOf(false) }
    var currentGeckoView by remember { mutableStateOf<GeckoView?>(null) }
    var previewRecencyCounter by remember { mutableIntStateOf(0) }
    var textScaleReloadRevision by remember { mutableIntStateOf(0) }
    var fullscreenForcedLandscape by remember { mutableStateOf(false) }
    var fullscreenPreviousOrientation by remember { mutableStateOf<Int?>(null) }
    val pageTransitionColor = MaterialTheme.colorScheme.background.toArgb()

    SideEffect {
        NativeBrowserHostCoordinator.setTransitionColor(pageTransitionColor)
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    val isFullScreen = activeTab.isFullScreen
    var addressText by remember(activeTabId) {
        mutableStateOf(if (activeTab.url == HOME_URL) "" else activeTab.url)
    }
    val isHome = activeTab.url == HOME_URL
    val readerModeActive = activeTab.readerArticle != null

    var darkWebsitesReloadRevision by remember { mutableIntStateOf(0) }
    var darkWebsitesSettingInitialized by remember { mutableStateOf(false) }
    var lastAppliedDarkTheme by remember { mutableStateOf(darkTheme) }

    fun reloadActivePageForAppearance() {
        val activity = context.findActivity()
        val inPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity?.isInPictureInPictureMode == true
        if (!isHome && !readerModeActive && !isFullScreen && !inPictureInPicture) {
            activeTab.session.reload()
        }
    }

    LaunchedEffect(settings.darkWebsitesEnabled) {
        val reloadAfterApply = darkWebsitesSettingInitialized
        darkWebsitesSettingInitialized = true
        BrowserEngine.setDarkWebsitesEnabled(settings.darkWebsitesEnabled) {
            if (reloadAfterApply) {
                context.findActivity()?.runOnUiThread {
                    darkWebsitesReloadRevision += 1
                }
            }
        }
    }

    LaunchedEffect(darkWebsitesReloadRevision) {
        if (darkWebsitesReloadRevision > 0) {
            reloadActivePageForAppearance()
        }
    }

    // Many sites only evaluate prefers-color-scheme while creating the document. Update Gecko
    // first, then reload each already-open web session exactly once. There is no arbitrary delay:
    // the runtime preference has already been set by updateSettings for manual theme changes.
    LaunchedEffect(darkTheme) {
        if (lastAppliedDarkTheme == darkTheme) return@LaunchedEffect
        lastAppliedDarkTheme = darkTheme
        BrowserEngine.applyPreferredColorScheme(settings.theme)

        val activity = context.findActivity()
        val inPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity?.isInPictureInPictureMode == true

        tabs.forEach { tab ->
            val canReloadForTheme =
                tab.session.isOpen() &&
                    tab.url != HOME_URL &&
                    tab.readerArticle == null &&
                    !tab.isFullScreen &&
                    !(inPictureInPicture && tab.id == activeTab.id)

            if (canReloadForTheme) {
                tab.session.reload()
            }
        }
    }

    LaunchedEffect(textScaleReloadRevision) {
        if (textScaleReloadRevision > 0) {
            delay(280L)
            if (activeTab.url != HOME_URL) {
                activeTab.session.reload()
            }
        }
    }

    LaunchedEffect(activeTab.id, isHome, readerModeActive, pageTransitionColor) {
        if (!isHome && !readerModeActive) {
            runCatching { activeTab.session.compositorController.setClearColor(pageTransitionColor) }
            NativeBrowserHostCoordinator.render(activeTab.session)
            currentGeckoView = NativeBrowserHostCoordinator.geckoView()
            currentGeckoView?.let(::requestHighFrameRateTree)
        } else {
            NativeBrowserHostCoordinator.hideAndRelease()
            currentGeckoView = null
        }
    }
    val effectivePageUrl = activeTab.url
    val activeHost = siteHost(effectivePageUrl)
    val activeSiteDesktopMode = siteDesktopRevision.let {
        SiteDesktopModeStore.effective(prefs, effectivePageUrl, settings.desktopMode)
    }
    val isBookmarked = !isHome && bookmarks.any { it.url == effectivePageUrl }
    val restorableTabs = tabs.filterNot { it.isPrivate }
    val restorableTabUrls = restorableTabs.map { it.url }
    val restorableTabStates = restorableTabs.map { it.serializedSessionState }
    val restorableTabMetadata = restorableTabs.map { TabSessionMetadata(it.isPinned, it.groupName) }
    val blockedBadge = ProtectionBridge.blockedBadge(activeTab.session)
    val extensionReady = ProtectionBridge.extensionReady
    val extensionPopupSession = ExtensionHostBridge.popupSession
    val chromeHiddenByOverlay = false
    val mediaRevision = MediaDetectorBridge.revision
    val detectedMedia = remember(activeTab.session, mediaRevision) {
        MediaDetectorBridge.itemsFor(activeTab.session)
    }
    val mediaEnabledForPage = !isHome && !MediaDetectorBridge.isExcludedUrl(activeTab.url)
    val latestDetectedMedia = remember(detectedMedia) {
        MediaDetectorBridge.selectPrimaryCandidate(detectedMedia)
    }
    val detectedVideoCount = if (
        mediaEnabledForPage &&
        latestDetectedMedia != null &&
        latestDetectedMedia.kind != DetectedMediaKind.AUDIO
    ) {
        1
    } else {
        0
    }
    var resolvedMedia by remember(activeTab.id) { mutableStateOf<List<DetectedMedia>>(emptyList()) }
    var resolvingMediaQualities by remember(activeTab.id) { mutableStateOf(false) }
    var mediaResolveRequest by remember { mutableIntStateOf(0) }
    val activeSiteProtection = settings.adBlockingEnabled &&
        !isHome && activeHost.isNotBlank() &&
        ProtectionBridge.siteEnabled("https://$activeHost/")

    LaunchedEffect(showMedia, activeTab.id, mediaRevision) {
        if (!showMedia || !mediaEnabledForPage) {
            resolvingMediaQualities = false
            return@LaunchedEffect
        }
        val candidate = MediaDetectorBridge.selectPrimaryCandidate(detectedMedia)
        val requestId = mediaResolveRequest + 1
        mediaResolveRequest = requestId
        val tabId = activeTab.id
        resolvedMedia = listOfNotNull(candidate)
        resolvingMediaQualities = candidate?.kind == DetectedMediaKind.HLS
        if (candidate == null) return@LaunchedEffect

        downloadController.resolveMediaQualities(
            media = listOf(candidate),
            isPrivate = activeTab.isPrivate
        ) { resolved ->
            if (mediaResolveRequest == requestId && activeTabId == tabId && showMedia) {
                resolvedMedia = listOfNotNull(
                    MediaDetectorBridge.selectPrimaryCandidate(resolved)
                )
                resolvingMediaQualities = false
            }
        }
    }

    LaunchedEffect(activeTab.id, activeTab.pageStartSequence, pageTransitionColor) {
        runCatching { activeTab.session.compositorController.setClearColor(pageTransitionColor) }
    }

    LaunchedEffect(activeTab.id, isFullScreen) {
        if (isFullScreen) {
            pushTopNotice(
                kind = BrowserTopNoticeKind.FULLSCREEN,
                title = tr(settings.language, "Full screen", "Полноэкранный режим"),
                message = tr(settings.language, "Press Back to exit", "Нажмите «Назад», чтобы выйти")
            )
        } else if (topNotice?.kind == BrowserTopNoticeKind.FULLSCREEN) {
            // Start the existing slide/fade exit immediately instead of waiting for the timer.
            topNotice = null
        }
    }

    LaunchedEffect(isFullScreen, darkTheme) {
        context.findActivity()?.let { activity ->
            val controller = WindowCompat.getInsetsController(
                activity.window,
                activity.window.decorView
            )
            val isPhone = activity.resources.configuration.smallestScreenWidthDp < 600

            if (isFullScreen) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())

                activity.window.decorView.requestLayout()
                currentGeckoView?.let { view ->
                    view.requestLayout()
                    view.invalidate()
                    delay(180L)
                    if (activeTab.isFullScreen && currentGeckoView === view) {
                        activity.window.decorView.requestLayout()
                        view.requestLayout()
                        ViewCompat.requestApplyInsets(activity.window.decorView)
                        ViewCompat.requestApplyInsets(view)
                        view.setVerticalClipping(0)
                        view.invalidate()
                    }
                }

                if (isPhone && !fullscreenForcedLandscape) {
                    fullscreenPreviousOrientation = activity.requestedOrientation
                    activity.requestedOrientation =
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    fullscreenForcedLandscape = true
                }
            } else {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme

                if (isPhone && fullscreenForcedLandscape) {
                    activity.requestedOrientation =
                        fullscreenPreviousOrientation
                            ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    fullscreenPreviousOrientation = null
                    fullscreenForcedLandscape = false
                }
            }
        }
    }

    DisposableEffect(context.findActivity(), activeTabId, isHome, readerModeActive) {
        val lifecycleOwner = context.findActivity() as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                val selectedTab = tabs.firstOrNull { it.id == activeTabId }
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        tabs.forEach { candidate ->
                            candidate.applyActiveState(candidate.id == activeTabId)
                        }
                        if (selectedTab != null && !isHome && !readerModeActive) {
                            NativeBrowserHostCoordinator.render(selectedTab.session)
                            currentGeckoView = NativeBrowserHostCoordinator.geckoView()
                        }
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        tabs.forEach { candidate ->
                            candidate.applyActiveState(candidate.id == activeTabId)
                        }
                        ProtectionBridge.setActiveSession(selectedTab?.session)
                        if (selectedTab != null && !isHome && !readerModeActive) {
                            NativeBrowserHostCoordinator.render(selectedTab.session)
                            currentGeckoView = NativeBrowserHostCoordinator.geckoView()
                            currentGeckoView?.let(::requestHighFrameRateTree)
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        tabs.forEach { it.setFocused(false) }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        tabs.filterNot { it.isPrivate }.forEach { candidate ->
                            runCatching { candidate.flushSessionState() }
                        }
                        tabs.forEach { it.setFocused(false) }
                        NativeBrowserHostCoordinator.release(hide = true)
                        currentGeckoView = null
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.let { activity ->
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun applyActiveState(selectedId: String) {
        val selectedTab = tabs.firstOrNull { candidate -> candidate.id == selectedId }
        selectedTab?.openIfNeeded()
        tabs.forEach { candidate ->
            candidate.applyActiveState(candidate.id == selectedId)
        }
        val selectedSession = selectedTab?.session
        ProtectionBridge.setActiveSession(selectedSession)
        GeckoMediaSessionBridge.setSelectedSession(selectedSession)
    }

    fun trimTabPreviews() {
        tabs.asSequence()
            .filter { it.preview != null }
            .sortedByDescending { it.previewRecency }
            .drop(MAX_TAB_PREVIEWS)
            .forEach { stale ->
                stale.preview = null
                stale.previewRecency = 0
            }
    }

    DisposableEffect(activeTabId) {
        val unbindMemoryTrim = BrowserMemoryCoordinator.bind {
            // Keep the current preview for a smooth return to the tab overview, but drop every
            // background thumbnail first. At 640x480 this can still release several MiB at once.
            tabs.forEach { candidate ->
                if (candidate.id != activeTabId) {
                    candidate.preview = null
                    candidate.previewRecency = 0
                }
            }
        }
        onDispose { unbindMemoryTrim() }
    }

    fun captureActivePreview() {
        val previewTab = activeTab
        if (previewTab.url == HOME_URL) return
        val geckoView = currentGeckoView ?: return

        geckoView.capturePixels().accept(
            { bitmap ->
                if (bitmap != null) {
                    browserScope.launch {
                        val scaled = withContext(Dispatchers.Default) {
                            downscaleTabPreview(bitmap)
                        }
                        if (scaled !== bitmap) {
                            // capturePixels() gives us a fresh bitmap; once a smaller copy exists,
                            // releasing the full-size source avoids a large transient allocation.
                            runCatching { bitmap.recycle() }
                        }
                        if (tabs.none { it.id == previewTab.id } || previewTab.url == HOME_URL) {
                            runCatching { scaled.recycle() }
                            return@launch
                        }
                        previewRecencyCounter += 1
                        previewTab.previewRecency = previewRecencyCounter
                        previewTab.preview = scaled
                        trimTabPreviews()
                    }
                }
            },
            { _ -> }
        )
    }

    // Keep a current thumbnail while the page surface is still visible. Capturing only when the
    // tabs dialog opens can race SurfaceView composition (especially after switching to the light
    // color scheme) and return an empty frame. A post-load/theme capture gives the overview a
    // stable bitmap before any overlay is shown.
    LaunchedEffect(activeTab.id, activeTab.loadSequence, darkTheme, currentGeckoView) {
        if (activeTab.url != HOME_URL && !activeTab.isLoading && currentGeckoView != null) {
            delay(180L)
            captureActivePreview()
        }
    }

    fun selectTab(tab: BrowserTab) {
        if (tab.id != activeTabId) captureActivePreview()
        focusManager.clearFocus()
        addressFocused = false
        currentGeckoView = null
        // Keep Gecko's web color scheme current before a lazy/restored session is opened.
        // This lets prefers-color-scheme be correct for the document from its first render.
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        tab.openIfNeeded()
        applyActiveState(tab.id)
        activeTabId = tab.id
        addressText = if (tab.url == HOME_URL) "" else tab.url
        showTabs = false
        showFindInPage = false
        showTranslation = false
    }

    fun createTab(
        isPrivate: Boolean = false,
        initialUrl: String = HOME_URL,
        select: Boolean = true,
        initialGroup: String? = null
    ) {
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        val tab = BrowserTab(
            runtime = runtime,
            initialUrl = initialUrl,
            isPrivate = isPrivate,
            initialGroup = initialGroup,
            settings = settings,
            desktopModeForUrl = { target -> SiteDesktopModeStore.effective(prefs, target, currentSettings.desktopMode) },
            onDownload = onDownloadResponse,
            onDownloadNavigation = onDownloadNavigation,
            onNewTabRequest = handleNewTabRequest,
            onLinkContextMenu = handleLinkContextMenu
        )
        tabs.add(tab)
        if (select) {
            selectTab(tab)
            addressText = if (initialUrl == HOME_URL) "" else initialUrl
        }
    }

    fun clearPendingClosedTabState() {
        pendingClosedTab = null
        pendingClosedIndex = -1
        pendingClosedWasActive = false
        pendingClosedReplacementId = null
        pendingClosedClearPrivateData = false
        pendingClosedToken = 0L
    }

    fun finalizePendingClosedTab() {
        val pending = pendingClosedTab ?: return
        val clearPrivateData = pendingClosedClearPrivateData
        clearPendingClosedTabState()
        pending.close(clearPrivateData)
    }

    fun clearPendingClosedAllState() {
        pendingClosedAllTabs = emptyList()
        pendingClosedAllActiveId = null
        pendingClosedAllReplacementId = null
        pendingClosedAllClearPrivateData = false
        pendingClosedAllToken = 0L
    }

    fun finalizePendingClosedAllTabs() {
        val pending = pendingClosedAllTabs
        if (pending.isEmpty()) return
        val clearPrivateData = pendingClosedAllClearPrivateData
        clearPendingClosedAllState()
        pending.forEach { it.close(clearPrivateData) }
    }

    fun undoClosedTab() {
        val closedTab = pendingClosedTab ?: return
        val restoreIndex = pendingClosedIndex
        val restoreAsActive = pendingClosedWasActive
        val replacementId = pendingClosedReplacementId

        val disposableReplacement = replacementId
            ?.let { id -> tabs.firstOrNull { it.id == id } }
            ?.takeIf { replacement ->
                tabs.size == 1 && replacement.url == HOME_URL
            }

        if (disposableReplacement != null) {
            tabs.remove(disposableReplacement)
            disposableReplacement.close(false)
        }

        val safeIndex = restoreIndex.coerceIn(0, tabs.size)
        tabs.add(safeIndex, closedTab)

        // A LazyGrid can keep a dismissed card composition alive for a short time. Give the
        // restored tab a new presentation generation so its swipe/removal state is recreated
        // instead of immediately dismissing the same tab again.
        lastRestoredTabId = closedTab.id
        tabRestoreGeneration += 1L
        clearPendingClosedTabState()

        if (restoreAsActive || tabs.size == 1) {
            activeTabId = closedTab.id
            applyActiveState(closedTab.id)
            addressText = if (closedTab.url == HOME_URL) "" else closedTab.url
        } else {
            applyActiveState(activeTabId)
        }

        topNotice = null
    }

    fun undoClosedAllTabs() {
        val closedTabs = pendingClosedAllTabs
        if (closedTabs.isEmpty()) return

        val restoreActiveId = pendingClosedAllActiveId
        val replacementId = pendingClosedAllReplacementId
        val disposableReplacement = replacementId
            ?.let { id -> tabs.firstOrNull { it.id == id } }
            ?.takeIf { replacement ->
                tabs.size == 1 && replacement.url == HOME_URL
            }

        if (disposableReplacement != null) {
            tabs.remove(disposableReplacement)
            disposableReplacement.close(false)
        }

        tabs.addAll(closedTabs)
        clearPendingClosedAllState()

        val restoredActive = closedTabs.firstOrNull { it.id == restoreActiveId }
            ?: closedTabs.firstOrNull()
        if (restoredActive != null) {
            activeTabId = restoredActive.id
            applyActiveState(restoredActive.id)
            addressText = if (restoredActive.url == HOME_URL) "" else restoredActive.url
        }

        // Recreate any tab-card composition that could still hold a stale close animation.
        lastRestoredTabId = restoredActive?.id
        tabRestoreGeneration += 1L
        topNotice = null
    }

    fun undoPendingClosedTabs() {
        if (pendingClosedAllTabs.isNotEmpty()) {
            undoClosedAllTabs()
        } else {
            undoClosedTab()
        }
    }

    fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return

        // Starting a new close operation commits any older undo window first.
        finalizePendingClosedAllTabs()
        finalizePendingClosedTab()

        val wasActive = tab.id == activeTabId
        tab.applyActiveState(false)
        tabs.removeAt(index)

        var replacementTabId: String? = null
        if (tabs.isEmpty()) {
            val replacement = BrowserTab(
                runtime = runtime,
                initialUrl = HOME_URL,
                settings = settings,
                desktopModeForUrl = { target -> SiteDesktopModeStore.effective(prefs, target, currentSettings.desktopMode) },
                onDownload = onDownloadResponse,
                onDownloadNavigation = onDownloadNavigation,
                onNewTabRequest = handleNewTabRequest,
                onLinkContextMenu = handleLinkContextMenu
            )
            tabs.add(replacement)
            replacementTabId = replacement.id
            activeTabId = replacement.id
            applyActiveState(replacement.id)
            addressText = ""
        } else if (wasActive) {
            val next = tabs[index.coerceAtMost(tabs.lastIndex)]
            activeTabId = next.id
            applyActiveState(next.id)
            addressText = if (next.url == HOME_URL) "" else next.url
        }

        pendingClosedTab = tab
        pendingClosedIndex = index
        pendingClosedWasActive = wasActive
        pendingClosedReplacementId = replacementTabId
        pendingClosedClearPrivateData = settings.clearPrivateDataOnExit

        val closeToken = System.nanoTime()
        pendingClosedToken = closeToken
        pushTopNotice(
            kind = BrowserTopNoticeKind.TAB_CLOSED,
            title = tr(settings.language, "Tab closed", "Вкладка закрыта"),
            message = tab.title.ifBlank { hostLabel(tab.url) },
            actionLabel = tr(settings.language, "Undo", "Отменить")
        )

        browserScope.launch {
            delay(4_200L)
            if (pendingClosedToken == closeToken && pendingClosedTab?.id == tab.id) {
                finalizePendingClosedTab()
            }
        }
    }

    fun closeAllTabs() {
        val closedTabs = tabs.toList()
        if (closedTabs.isEmpty()) return

        finalizePendingClosedTab()
        finalizePendingClosedAllTabs()

        val previouslyActiveId = activeTabId
        closedTabs.forEach { tab ->
            tab.applyActiveState(false)
        }
        tabs.clear()

        val replacement = BrowserTab(
            runtime = runtime,
            initialUrl = HOME_URL,
            settings = settings,
            desktopModeForUrl = { target ->
                SiteDesktopModeStore.effective(prefs, target, currentSettings.desktopMode)
            },
            onDownload = onDownloadResponse,
            onDownloadNavigation = onDownloadNavigation,
            onNewTabRequest = handleNewTabRequest,
            onLinkContextMenu = handleLinkContextMenu
        )
        tabs.add(replacement)
        activeTabId = replacement.id
        applyActiveState(replacement.id)
        addressText = ""

        pendingClosedAllTabs = closedTabs
        pendingClosedAllActiveId = previouslyActiveId
        pendingClosedAllReplacementId = replacement.id
        pendingClosedAllClearPrivateData = settings.clearPrivateDataOnExit

        val closeToken = System.nanoTime()
        pendingClosedAllToken = closeToken
        pushTopNotice(
            kind = BrowserTopNoticeKind.TAB_CLOSED,
            title = tr(settings.language, "Tabs closed", "Вкладки закрыты"),
            message = tr(
                settings.language,
                "${closedTabs.size} tabs closed",
                "Закрыто вкладок: ${closedTabs.size}"
            ),
            actionLabel = tr(settings.language, "Undo", "Отменить")
        )

        browserScope.launch {
            delay(4_200L)
            if (pendingClosedAllToken == closeToken && pendingClosedAllTabs === closedTabs) {
                finalizePendingClosedAllTabs()
            }
        }
    }

    fun navigateInput(input: String) {
        val target = normalizeAddress(input, settings.searchEngine)
        addressText = if (target == HOME_URL) "" else target
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        runCatching { activeTab.session.compositorController.setClearColor(pageTransitionColor) }
        activeTab.session.loadUri(target)
        focusManager.clearFocus()
        showFindInPage = false
        showTranslation = false
    }

    fun openHome() {
        addressText = ""
        activeTab.preview = null
        activeTab.previewRecency = 0
        activeTab.session.loadUri(HOME_URL)
        focusManager.clearFocus()
        showFindInPage = false
        showTranslation = false
    }

    fun refreshSitePermissions() {
        if (isHome || activeHost.isBlank()) {
            sitePermissions = emptyList()
            return
        }
        runtime.storageController
            .getPermissions(effectivePageUrl, activeTab.storageContextId, activeTab.isPrivate)
            .accept(
                { permissions -> sitePermissions = permissions?.toList().orEmpty() },
                { _ -> sitePermissions = emptyList() }
            )
    }

    fun openProtectionPanel() {
        sitePermissions = emptyList()
        if (!isHome && activeHost.isNotBlank()) {
            refreshSitePermissions()
        }
        showProtection = true
    }

    fun toggleBookmark() {
        if (activeTab.url == HOME_URL) return
        val bookmarkUrl = activeTab.url

        val existingIndex = bookmarks.indexOfFirst { it.url == bookmarkUrl }
        if (existingIndex >= 0) {
            bookmarks.removeAt(existingIndex)
        } else {
            bookmarks.add(
                0,
                BookmarkItem(
                    url = bookmarkUrl,
                    title = activeTab.title.ifBlank { hostLabel(bookmarkUrl) }
                )
            )
        }
        BookmarkStore.save(prefs, bookmarks)
    }

    fun openBookmark(bookmark: BookmarkItem) {
        showBookmarks = false
        addressText = bookmark.url
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        runCatching { activeTab.session.compositorController.setClearColor(pageTransitionColor) }
        activeTab.session.loadUri(bookmark.url)
        focusManager.clearFocus()
    }

    fun removeBookmark(bookmark: BookmarkItem) {
        bookmarks.removeAll { it.url == bookmark.url }
        BookmarkStore.save(prefs, bookmarks)
    }

    fun editBookmark(bookmark: BookmarkItem, title: String, folder: String?) {
        val index = bookmarks.indexOfFirst { it.url == bookmark.url }
        if (index < 0) return
        bookmarks[index] = bookmark.copy(
            title = title.trim().ifBlank { bookmark.url },
            folder = folder?.trim()?.takeIf { it.isNotBlank() }
        )
        BookmarkStore.save(prefs, bookmarks)
    }

    fun openHistoryEntry(entry: HistoryItem) {
        showHistory = false
        addressText = entry.url
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        runCatching { activeTab.session.compositorController.setClearColor(pageTransitionColor) }
        activeTab.session.loadUri(entry.url)
        focusManager.clearFocus()
    }

    fun removeHistoryEntry(entry: HistoryItem) {
        history.remove(entry)
        HistoryStore.save(prefs, history)
    }

    val externalNavigationHandler by rememberUpdatedState<(String) -> Unit> { target ->
        createTab(isPrivate = false, initialUrl = target)
    }

    DisposableEffect(Unit) {
        // Apply the selected browser theme before the initial/restored tab is opened.
        BrowserEngine.applyPreferredColorScheme(settings.theme)
        applyActiveState(activeTabId)
        val unbindExternalNavigation = ExternalNavigationCoordinator.bind { target ->
            externalNavigationHandler(target)
        }

        onDispose {
            unbindExternalNavigation()
            tabs.forEach { it.close(currentSettings.clearPrivateDataOnExit) }
        }
    }

    LaunchedEffect(activeTab.id, activeTab.url, addressFocused) {
        if (!addressFocused) {
            addressText = if (activeTab.url == HOME_URL) "" else activeTab.url
        }
        if (!activeTab.isPrivate) {
            prefs.edit().putString(PREF_LAST_URL, activeTab.url).apply()
        }
    }

    LaunchedEffect(activeTab.id, readerModeActive) {
        if (readerModeActive) {
            fun dismissReaderOmnibox() {
                focusManager.clearFocus(force = true)
                addressFocused = false
                context.findActivity()?.let { activity ->
                    activity.currentFocus?.clearFocus()
                    WindowInsetsControllerCompat(
                        activity.window,
                        activity.window.decorView
                    ).hide(WindowInsetsCompat.Type.ime())
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as?
                        android.view.inputmethod.InputMethodManager)
                        ?.hideSoftInputFromWindow(
                            activity.window.decorView.windowToken,
                            0
                        )
                }
            }

            dismissReaderOmnibox()
            delay(64L)
            dismissReaderOmnibox()
            delay(160L)
            dismissReaderOmnibox()
        }
    }

    LaunchedEffect(restorableTabUrls, restorableTabStates, restorableTabMetadata, activeTabId) {
        delay(250L)
        val activeIndex = restorableTabs
            .indexOfFirst { it.id == activeTabId }
            .coerceAtLeast(0)
        TabSessionStore.save(
            prefs = prefs,
            urls = restorableTabUrls.ifEmpty { listOf(HOME_URL) },
            states = restorableTabStates.ifEmpty { listOf(null) },
            metadata = restorableTabMetadata.ifEmpty { listOf(TabSessionMetadata()) },
            activeIndex = activeIndex
        )
    }

    LaunchedEffect(settings.desktopMode, siteDesktopRevision) {
        tabs.forEach { tab ->
            val desired = SiteDesktopModeStore.effective(prefs, tab.url, settings.desktopMode)
            if (tab.isDesktopModeEnabled() != desired) {
                tab.applyDesktopMode(desired, reload = true)
            }
        }
    }

    LaunchedEffect(activeTab.id, activeTab.loadSequence) {
        if (
            settings.historyEnabled &&
            !activeTab.isPrivate &&
            activeTab.loadSequence > 0 &&
            activeTab.url != HOME_URL
        ) {
            history.add(
                0,
                HistoryItem(
                    url = activeTab.url,
                    title = activeTab.title.ifBlank { hostLabel(activeTab.url) },
                    visitedAt = System.currentTimeMillis()
                )
            )
            while (history.size > 1000) {
                history.removeAt(history.lastIndex)
            }
            HistoryStore.saveAsync(prefs, history)
        }
    }

    SideEffect {
        NativeBrowserHostCoordinator.setInputEnabled(
            !isHome &&
                !readerModeActive &&
                !addressFocused &&
                !showTabs &&
                !showMenu &&
                !showBookmarks &&
                !showHistory &&
                !showDownloads &&
                !showMedia &&
                !showSettings &&
                !showProtection &&
                !showFindInPage &&
                !showTranslation &&
                pendingNewTabRequest == null &&
                linkContextMenu == null &&
                extensionPopupSession == null
        )
    }

    val browserBackEnabled =
        isFullScreen || extensionPopupSession != null || pendingNewTabRequest != null || linkContextMenu != null || addressFocused ||
            showTabs || showMenu || showBookmarks || showHistory || showDownloads || showMedia ||
            showSettings || showProtection || showFindInPage || showTranslation || readerModeActive || activeTab.canGoBack || !isHome

    BackHandler(enabled = browserBackEnabled) {
        when {
            isFullScreen -> activeTab.session.exitFullScreen()
            extensionPopupSession != null -> ExtensionHostBridge.closePopup()
            pendingNewTabRequest != null -> {
                pendingNewTabRequest?.let { pending ->
                    runCatching { pending.result.complete(null) }
                }
                pendingNewTabRequest = null
            }
            linkContextMenu != null -> linkContextMenu = null
            showMenu -> showMenu = false
            showTabs -> showTabs = false
            showBookmarks -> showBookmarks = false
            showHistory -> showHistory = false
            showDownloads -> showDownloads = false
            showMedia -> showMedia = false
            showSettings -> showSettings = false
            showProtection -> showProtection = false
            showTranslation -> showTranslation = false
            showFindInPage -> showFindInPage = false
            addressFocused -> focusManager.clearFocus()
            readerModeActive -> activeTab.exitReaderMode()
            activeTab.canGoBack -> activeTab.session.goBack()
            !isHome -> openHome()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isFullScreen) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {}
            }

            Column(
                modifier = if (isFullScreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                }
            ) {
                if (!isFullScreen && !chromeHiddenByOverlay && showTabletTabStrip) {
                    TabletTabStrip(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onSelect = { tab -> selectTab(tab) },
                        onClose = { tab -> closeTab(tab) },
                        onNewTab = { createTab() }
                    )
                }

                if (!isFullScreen && settings.toolbarPosition == ToolbarPosition.BOTTOM) {
                    BrowserPageLoadingLine(
                        loading = activeTab.isLoading,
                        progress = activeTab.loadProgress,
                        isPrivate = activeTab.isPrivate
                    )
                }

                if (!isFullScreen && !chromeHiddenByOverlay && settings.toolbarPosition == ToolbarPosition.TOP) {
                    BrowserBottomBar(
                        address = addressText,
                        onAddressChange = { addressText = it },
                        onAddressFocusChanged = { addressFocused = it },
                        onNavigate = { input -> navigateInput(input) },
                        searchEngine = settings.searchEngine,
                        history = if (activeTab.isPrivate) emptyList() else history,
                        canGoBack = activeTab.canGoBack,
                        canGoForward = activeTab.canGoForward,
                        onBack = {
                            if (readerModeActive) activeTab.exitReaderMode()
                            else activeTab.session.goBack()
                        },
                        onForward = { activeTab.session.goForward() },
                        onTabs = { captureActivePreview(); showTabs = true },
                        onMenu = { showMenu = true },
                        tabCount = tabs.size,
                        isPrivate = activeTab.isPrivate,
                        darkTheme = darkTheme,
                        mediaCount = detectedVideoCount,
                        onMedia = {
                            focusManager.clearFocus()
                            showMedia = true
                        },
                        homeMode = isHome,
                        position = settings.toolbarPosition,
                        toolbarActions = settings.toolbarActions,
                        uiDensity = settings.uiDensity
                    )
                }

                if (!isFullScreen && settings.toolbarPosition == ToolbarPosition.TOP) {
                    BrowserPageLoadingLine(
                        loading = activeTab.isLoading,
                        progress = activeTab.loadProgress,
                        isPrivate = activeTab.isPrivate
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            NativeBrowserHostCoordinator.setBounds(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt()
                            )
                        }
                        .then(
                            if (!isFullScreen && settings.toolbarPosition == ToolbarPosition.TOP) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (isHome) {
                        IlyroHomePage(
                            settings = settings,
                            searchEngine = settings.searchEngine,
                            history = if (activeTab.isPrivate) emptyList() else history,
                            isPrivate = activeTab.isPrivate,
                            onSearchEngineChange = { engine ->
                                onSettingsChange(settings.copy(searchEngine = engine))
                            },
                            onNavigate = { navigateInput(it) }
                        )
                    } else if (readerModeActive) {
                        ReaderModeView(
                            article = activeTab.readerArticle!!,
                            language = settings.language,
                            darkTheme = darkTheme,
                            onExit = { activeTab.exitReaderMode() }
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }

                    if (!isHome && activeTab.pullDistance > 0f) {
                        val indicatorOffset = (8 + 30 * activeTab.pullDistance.coerceIn(0f, 1f)).dp
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = indicatorOffset)
                                .size(42.dp),
                            shape = RoundedCornerShape(21.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 7.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = tr("Pull to refresh", "Потяните для обновления"),
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = topNotice != null,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp)
                            .padding(top = 10.dp)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                NativeBrowserHostCoordinator.setInputExclusion(
                                    bounds.left.toInt(),
                                    bounds.top.toInt(),
                                    bounds.right.toInt(),
                                    bounds.bottom.toInt()
                                )
                            }
                    ) {
                        val notice = renderedTopNotice ?: topNotice
                        if (notice != null) {
                            val runNoticeAction: () -> Unit = {
                                when (notice.kind) {
                                    BrowserTopNoticeKind.DOWNLOAD -> {
                                        refreshDownloads()
                                        showDownloads = true
                                    }
                                    BrowserTopNoticeKind.TAB_CLOSED -> undoPendingClosedTabs()
                                    BrowserTopNoticeKind.FULLSCREEN -> Unit
                                }
                                topNotice = null
                            }
                            BrowserTopNoticeCard(
                                notice = notice,
                                // For closed tabs the card itself is informational. Undo is only
                                // allowed from the clearly outlined action button.
                                onClick = {
                                    if (notice.kind != BrowserTopNoticeKind.TAB_CLOSED) {
                                        runNoticeAction()
                                    }
                                },
                                onActionClick = runNoticeAction
                            )
                        }
                    }
                }

                if (!isFullScreen && !chromeHiddenByOverlay && settings.toolbarPosition == ToolbarPosition.BOTTOM) {
                    BrowserBottomBar(
                        address = addressText,
                        onAddressChange = { addressText = it },
                        onAddressFocusChanged = { addressFocused = it },
                        onNavigate = { input -> navigateInput(input) },
                        searchEngine = settings.searchEngine,
                        history = if (activeTab.isPrivate) emptyList() else history,
                        canGoBack = activeTab.canGoBack,
                        canGoForward = activeTab.canGoForward,
                        onBack = {
                            if (readerModeActive) activeTab.exitReaderMode()
                            else activeTab.session.goBack()
                        },
                        onForward = { activeTab.session.goForward() },
                        onTabs = { captureActivePreview(); showTabs = true },
                        onMenu = { showMenu = true },
                        tabCount = tabs.size,
                        isPrivate = activeTab.isPrivate,
                        darkTheme = darkTheme,
                        mediaCount = detectedVideoCount,
                        onMedia = {
                            focusManager.clearFocus()
                            showMedia = true
                        },
                        homeMode = isHome,
                        position = settings.toolbarPosition,
                        toolbarActions = settings.toolbarActions,
                        uiDensity = settings.uiDensity
                    )
                }
            }
        }
    }

    SitePermissionPromptHost()

    val openedNewTabMessage = tr("Opened in new tab", "Открыто в новой вкладке")
    val openedPrivateTabMessage = tr("Opened in private tab", "Открыто в приватной вкладке")
    val linkCopiedMessage = tr("Link copied", "Ссылка скопирована")
    val shareLinkTitle = tr("Share link", "Поделиться ссылкой")

    linkContextMenu?.let { request ->
        LinkContextMenuSheet(
            url = request.url,
            title = request.title,
            onDismiss = { linkContextMenu = null },
            onOpenNewTab = {
                val sourceTab = tabs.firstOrNull { it.id == request.sourceTabId }
                createTab(
                    isPrivate = sourceTab?.isPrivate == true,
                    initialUrl = request.url,
                    select = false,
                    initialGroup = sourceTab?.groupName
                )
                linkContextMenu = null
                Toast.makeText(context, openedNewTabMessage, Toast.LENGTH_SHORT).show()
            },
            onOpenPrivateTab = {
                createTab(
                    isPrivate = true,
                    initialUrl = request.url,
                    select = false
                )
                linkContextMenu = null
                Toast.makeText(context, openedPrivateTabMessage, Toast.LENGTH_SHORT).show()
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", request.url))
                linkContextMenu = null
                Toast.makeText(context, linkCopiedMessage, Toast.LENGTH_SHORT).show()
            },
            onShare = {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, request.url)
                }
                runCatching {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            shareIntent,
                            shareLinkTitle
                        )
                    )
                }
                linkContextMenu = null
            }
        )
    }

    if (showFindInPage && !isHome) {
        FindInPageDialog(
            session = activeTab.session,
            onDismiss = { showFindInPage = false }
        )
    }

    if (showTranslation && !isHome && !readerModeActive) {
        TranslationDialog(
            session = activeTab.session,
            state = activeTab.translationState,
            pageLanguage = activeTab.pageLanguage,
            translationReady = activeTab.translationReady,
            pageLoading = activeTab.isLoading,
            language = settings.language,
            onDismiss = { showTranslation = false }
        )
    }

    extensionPopupSession?.let { popup ->
        ExtensionPopupHost(
            session = popup,
            title = ExtensionHostBridge.popupTitle,
            onDismiss = { ExtensionHostBridge.closePopup() }
        )
    }

    if (showTabs) {
        val overviewItems = tabs.map { tab ->
            TabOverviewItem(
                id = tab.id,
                title = if (tab.url == HOME_URL) tr("New tab", "Новая вкладка") else tab.title.ifBlank { hostLabel(tab.url) },
                host = if (tab.url == HOME_URL) tr("ILYRO Home", "Главная ILYRO") else hostLabel(tab.url),
                isHome = tab.url == HOME_URL,
                isPrivate = tab.isPrivate,
                selected = tab.id == activeTabId,
                isPinned = tab.isPinned,
                groupName = tab.groupName,
                preview = tab.preview
            )
        }

        TabOverviewSheet(
            settings = settings,
            tabs = overviewItems,
            onDismiss = { showTabs = false },
            onNewTab = { createTab() },
            onNewPrivateTab = { createTab(isPrivate = true) },
            onSelect = { id ->
                tabs.firstOrNull { it.id == id }?.let { selectTab(it) }
            },
            onClose = { id ->
                tabs.firstOrNull { it.id == id }?.let { closeTab(it) }
            },
            onCloseAll = { closeAllTabs() },
            tabCloseNotice = topNotice?.takeIf { it.kind == BrowserTopNoticeKind.TAB_CLOSED },
            onUndoClose = { undoPendingClosedTabs() },
            restoredTabId = lastRestoredTabId,
            restoreGeneration = tabRestoreGeneration,
            onTogglePinned = { id ->
                tabs.firstOrNull { it.id == id }?.let { it.isPinned = !it.isPinned }
            },
            onUpdateGroup = { id, group ->
                tabs.firstOrNull { it.id == id }?.let { it.groupName = group }
            }
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            settings = settings,
            bookmarks = bookmarks,
            onDismiss = { showBookmarks = false },
            onOpen = { bookmark -> openBookmark(bookmark) },
            onRemove = { bookmark -> removeBookmark(bookmark) },
            onEdit = { bookmark, title, folder ->
                editBookmark(bookmark, title, folder)
            },
            onClearAll = {
                bookmarks.clear()
                BookmarkStore.save(prefs, bookmarks)
            }
        )
    }

    if (showHistory) {
        HistorySheet(
            settings = settings,
            history = history,
            onDismiss = { showHistory = false },
            onOpen = { entry -> openHistoryEntry(entry) },
            onRemove = { entry -> removeHistoryEntry(entry) },
            onClearAll = {
                history.clear()
                HistoryStore.save(prefs, history)
            }
        )
    }

    if (showMedia && mediaEnabledForPage) {
        val mediaDownloadStartedTitle = tr("Download started", "Загрузка началась")
        val mediaFileLabel = tr("Media file", "Медиафайл")
        val mediaOpenLabel = tr("View download", "Показать загрузку")
        val mediaDownloadFailedMessage = tr("Couldn't start media download", "Не удалось начать загрузку медиа")
        MediaSheet(
            media = if (resolvedMedia.isNotEmpty() || detectedMedia.isEmpty()) {
                resolvedMedia
            } else {
                listOfNotNull(latestDetectedMedia)
            },
            resolvingQualities = resolvingMediaQualities,
            onDismiss = { showMedia = false },
            onDownload = { item ->
                if (!item.canDownload) return@MediaSheet
                ensureDownloadNotificationPermission(context)
                val started = when (item.kind) {
                    DetectedMediaKind.HLS -> downloadController.enqueueHlsDownload(
                        url = item.url,
                        suggestedTitle = activeTab.title.takeIf { it.isNotBlank() } ?: item.title,
                        referrer = item.pageUrl ?: activeTab.url,
                        isPrivate = activeTab.isPrivate,
                        onRecordsChanged = refreshDownloads,
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    )
                    DetectedMediaKind.VIDEO -> {
                        if (item.isYouTubeStream && item.requiresSeparateAudio) {
                            val videoContainer = item.mimeType
                                ?.substringBefore(';')
                                ?.substringAfter('/')
                                ?.lowercase()
                            val audio = detectedMedia
                                .asSequence()
                                .filter {
                                    it.kind == DetectedMediaKind.AUDIO &&
                                        it.isYouTubeStream &&
                                        (item.pageUrl == null || it.pageUrl == null ||
                                            MediaDetectorBridge.sameYoutubePage(item.pageUrl, it.pageUrl))
                                }
                                .sortedWith(
                                    compareByDescending<DetectedMedia> {
                                        val audioContainer = it.mimeType
                                            ?.substringBefore(';')
                                            ?.substringAfter('/')
                                            ?.lowercase()
                                        audioContainer == videoContainer
                                    }.thenByDescending { it.lastSeenAt }
                                )
                                .firstOrNull()
                            downloadController.enqueueYouTubeDownload(
                                video = item,
                                audio = audio,
                                suggestedTitle = activeTab.title.takeIf { it.isNotBlank() } ?: item.title,
                                referrer = item.pageUrl ?: activeTab.url,
                                isPrivate = activeTab.isPrivate,
                                onRecordsChanged = refreshDownloads,
                                onError = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            downloadController.enqueueYouTubeProgressiveDownload(
                                video = item,
                                suggestedTitle = activeTab.title.takeIf { it.isNotBlank() } ?: item.title,
                                allowMetered = currentSettings.downloadsOverMetered,
                                referrer = item.pageUrl ?: activeTab.url,
                                isPrivate = activeTab.isPrivate,
                                onRecordsChanged = refreshDownloads
                            ) || downloadController.enqueueNavigationWithSession(
                                url = item.url,
                                sourceSettings = activeTab.session.settings,
                                suggestedName = activeTab.title.takeIf { it.isNotBlank() } ?: item.title,
                                allowMetered = currentSettings.downloadsOverMetered,
                                referrer = item.pageUrl ?: activeTab.url,
                                isPrivate = activeTab.isPrivate,
                                onRecordsChanged = refreshDownloads
                            )
                        }
                    }
                    DetectedMediaKind.AUDIO ->
                        downloadController.enqueueNavigationWithSession(
                            url = item.url,
                            sourceSettings = activeTab.session.settings,
                            suggestedName = activeTab.title.takeIf { it.isNotBlank() } ?: item.title,
                            allowMetered = currentSettings.downloadsOverMetered,
                            referrer = item.pageUrl ?: activeTab.url,
                            isPrivate = activeTab.isPrivate,
                            onRecordsChanged = refreshDownloads
                        )
                    DetectedMediaKind.DASH -> false
                }
                refreshDownloads()
                if (started) {
                    markDownloadsActive()
                    pushTopNotice(
                        kind = BrowserTopNoticeKind.DOWNLOAD,
                        title = mediaDownloadStartedTitle,
                        message = item.title?.takeIf { it.isNotBlank() } ?: mediaFileLabel,
                        actionLabel = mediaOpenLabel
                    )
                } else {
                    Toast.makeText(
                        context,
                        mediaDownloadFailedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onOpenExternal = { item ->
                val mime = item.mimeType ?: when (item.kind) {
                    DetectedMediaKind.VIDEO -> "video/*"
                    DetectedMediaKind.AUDIO -> "audio/*"
                    DetectedMediaKind.HLS -> "application/vnd.apple.mpegurl"
                    DetectedMediaKind.DASH -> "application/dash+xml"
                }
                val uri = Uri.parse(item.url)
                val activity = context.findActivity()
                val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                }
                val opened = runCatching {
                    requireNotNull(activity) { "Browser Activity is unavailable" }
                    activity.startActivity(viewIntent)
                    true
                }.getOrDefault(false)
                if (!opened) {
                    val fallbackOpened = runCatching {
                        requireNotNull(activity) { "Browser Activity is unavailable" }
                        activity.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        )
                        true
                    }.getOrDefault(false)
                    if (!fallbackOpened) {
                        Toast.makeText(
                            context,
                            tr(currentSettings.language, "No external media player found", "Внешний видеоплеер не найден"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    if (showDownloads) {
        val downloadPausedMessage = tr("Download paused", "Загрузка приостановлена")
        val downloadCannotPauseMessage = tr("This download can't be paused", "Эту загрузку нельзя поставить на паузу")
        val downloadResumedMessage = tr("Download resumed", "Загрузка продолжена")
        val downloadCannotResumeMessage = tr("Couldn't resume download", "Не удалось продолжить загрузку")
        DownloadsSheet(
            settings = settings,
            downloads = downloads,
            onDismiss = { showDownloads = false },
            onOpen = { item ->
                if (!downloadController.open(item)) {
                    Toast.makeText(
                        context,
                        tr(currentSettings.language, "No app can open this file", "Нет приложения для открытия этого файла"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRetry = { item ->
                val restarted = downloadController.retry(item, settings.downloadsOverMetered)
                refreshDownloads()
                if (restarted != null) markDownloadsActive()
                Toast.makeText(
                    context,
                    if (restarted != null) {
                        tr(currentSettings.language, "Download restarted", "Загрузка перезапущена")
                    } else {
                        tr(currentSettings.language, "Couldn't restart download", "Не удалось перезапустить загрузку")
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPause = { item ->
                val paused = downloadController.pause(item)
                refreshDownloads()
                Toast.makeText(
                    context,
                    if (paused) downloadPausedMessage else downloadCannotPauseMessage,
                    Toast.LENGTH_SHORT
                ).show()
            },
            onResume = { item ->
                val resumed = downloadController.resume(item)
                refreshDownloads()
                if (resumed) markDownloadsActive()
                Toast.makeText(
                    context,
                    if (resumed) downloadResumedMessage else downloadCannotResumeMessage,
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCancel = { item ->
                downloadController.cancel(item)
                refreshDownloads()
                Toast.makeText(
                    context,
                    tr(currentSettings.language, "Download stopped", "Загрузка остановлена"),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onRemove = { item ->
                downloadController.remove(item)
                refreshDownloads()
            },
            onRename = { item, name ->
                val renamed = downloadController.rename(item, name)
                refreshDownloads()
                Toast.makeText(
                    context,
                    if (renamed) {
                        tr(currentSettings.language, "File renamed", "Файл переименован")
                    } else {
                        tr(currentSettings.language, "Couldn't rename file", "Не удалось переименовать файл")
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },
            onShare = { item ->
                if (!downloadController.share(item)) {
                    Toast.makeText(
                        context,
                        tr(currentSettings.language, "Couldn't share file", "Не удалось поделиться файлом"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onMove = { item ->
                pendingMoveDownload = item
                moveDownloadLauncher.launch(item.record.fileName)
            }
        )
    }

    if (showProtection) {
        ProtectionSheet(
            host = activeHost,
            globalEnabled = settings.adBlockingEnabled,
            extensionReady = extensionReady,
            blockedBadge = blockedBadge,
            permissions = sitePermissions,
            isPrivate = activeTab.isPrivate,
            onDismiss = { showProtection = false },
            onGlobalEnabledChange = { enabled ->
                onSettingsChange(settings.copy(adBlockingEnabled = enabled))
            },
            onUpdateFilters = {
                if (!ProtectionBridge.updateFilterLists()) {
                    Toast.makeText(
                        context,
                        tr(settings.language, "uBlock Origin is not ready", "uBlock Origin ещё не готов"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onOpenUBlockSettings = {
                val target = ProtectionBridge.dashboardUrl()
                if (target.isNullOrBlank()) {
                    Toast.makeText(
                        context,
                        tr(settings.language, "uBlock settings are not ready", "Настройки uBlock ещё не готовы"),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showProtection = false
                    createTab(isPrivate = false, initialUrl = target, select = true)
                }
            },
            onOpenSiteControls = {
                if (!ProtectionBridge.openSiteControls(activeTab.session)) {
                    Toast.makeText(context, "uBlock Origin is still starting", Toast.LENGTH_SHORT).show()
                }
            },
            onPermissionChange = { permission, value ->
                runtime.storageController.setPermission(permission, value)
                refreshSitePermissions()
            },
            onResetPermissions = {
                sitePermissions.forEach { permission ->
                    runtime.storageController.setPermission(
                        permission,
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                    )
                }
                refreshSitePermissions()
            },
            onClearSiteData = {
                if (activeTab.isPrivate) {
                    activeTab.clearPrivateStorage()
                    sitePermissions = emptyList()
                    activeTab.session.reload()
                    Toast.makeText(context, tr(settings.language, "Private site data cleared", "Данные приватного сайта очищены"), Toast.LENGTH_SHORT).show()
                } else {
                    runtime.storageController
                        .clearDataFromHost(activeHost, StorageController.ClearFlags.SITE_DATA)
                        .accept(
                            {
                                sitePermissions = emptyList()
                                activeTab.session.reload()
                                Toast.makeText(context, tr(settings.language, "Site data cleared", "Данные сайта очищены"), Toast.LENGTH_SHORT).show()
                            },
                            {
                                Toast.makeText(context, tr(settings.language, "Couldn't clear site data", "Не удалось очистить данные сайта"), Toast.LENGTH_SHORT).show()
                            }
                        )
                }
            }
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            versionName = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "0.7.0",
            onDismiss = { showSettings = false },
            onSettingsChange = onSettingsChange,
            onClearHistory = {
                history.clear()
                HistoryStore.save(prefs, history)
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
            },
            onClearBookmarks = {
                bookmarks.clear()
                BookmarkStore.save(prefs, bookmarks)
                Toast.makeText(context, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
            },
            onClearDownloads = {
                downloadController.clearAll()
                refreshDownloads()
                Toast.makeText(context, "Downloads cleared", Toast.LENGTH_SHORT).show()
            },
            onClearSiteData = {
                runtime.storageController.clearData(StorageController.ClearFlags.ALL)
                Toast.makeText(context, "Site data clearing started", Toast.LENGTH_SHORT).show()
            }
        )
    }

    pendingNewTabRequest?.let { pending ->
        val requestedHost = siteHost(pending.uri)
        val sourceHost = tabs.firstOrNull { it.id == pending.sourceTabId }
            ?.let { siteHost(it.url) }
            .orEmpty()
        AlertDialog(
            onDismissRequest = {
                runCatching { pending.result.complete(null) }
                pendingNewTabRequest = null
            },
            title = { Text(tr("Open a new tab?", "Открыть новую вкладку?")) },
            text = {
                Text(
                    if (requestedHost.isNotBlank()) {
                        tr(
                            "${sourceHost.ifBlank { "This site" }} wants to open $requestedHost in a new tab.",
                            "${sourceHost.ifBlank { "Этот сайт" }} хочет открыть $requestedHost в новой вкладке."
                        )
                    } else {
                        tr(
                            "This page wants to open a new tab.",
                            "Эта страница хочет открыть новую вкладку."
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sourceTab = tabs.firstOrNull { it.id == pending.sourceTabId }
                        if (sourceTab == null) {
                            runCatching { pending.result.complete(null) }
                            pendingNewTabRequest = null
                        } else {
                            BrowserEngine.applyPreferredColorScheme(settings.theme)
                            val newTab = BrowserTab(
                                runtime = runtime,
                                initialUrl = pending.uri,
                                isPrivate = sourceTab.isPrivate,
                                initialGroup = sourceTab.groupName,
                                settings = settings,
                                desktopModeForUrl = { target -> SiteDesktopModeStore.effective(prefs, target, currentSettings.desktopMode) },
                                onDownload = onDownloadResponse,
                                onDownloadNavigation = onDownloadNavigation,
                                onNewTabRequest = handleNewTabRequest,
                                onLinkContextMenu = handleLinkContextMenu,
                                loadInitialUri = false,
                                openSession = false
                            )
                            tabs.add(newTab)
                            pendingNewTabRequest = null
                            pending.result.complete(newTab.session)
                            currentGeckoView = null
                            activeTabId = newTab.id
                            addressText = pending.uri
                            context.findActivity()?.window?.decorView?.post {
                                runCatching {
                                    newTab.bindPopupSessionAfterOpen()
                                    applyActiveState(newTab.id)
                                }
                            }
                        }
                    }
                ) {
                    Text(tr("Open", "Открыть"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        runCatching { pending.result.complete(null) }
                        pendingNewTabRequest = null
                    }
                ) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    if (showMenu) {
        val menuScrollState = rememberScrollState()
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val menuMaxHeight = (configuration.screenHeightDp * 0.84f).dp
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var menuMoreExpanded by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = menuSheetState,
            sheetGesturesEnabled = true,
            dragHandle = {
                androidx.compose.material3.BottomSheetDefaults.DragHandle()
            },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(
                topStart = IlyroVisualTokens.LargeRadius,
                topEnd = IlyroVisualTokens.LargeRadius
            )
        ) {
            IlyroSystemBarAppearance()
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .heightIn(max = menuMaxHeight)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(menuScrollState)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        QuickMenuPrimaryRow(
                            icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            title = if (isBookmarked) {
                                tr("Remove bookmark", "Удалить из закладок")
                            } else {
                                tr("Add bookmark", "Добавить в закладки")
                            },
                            enabled = !isHome,
                            selected = isBookmarked
                        ) {
                            toggleBookmark()
                            showMenu = false
                        }

                        QuickMenuPrimaryRow(
                            icon = Icons.Rounded.Search,
                            title = tr("Find in page", "Найти на странице"),
                            enabled = !isHome
                        ) {
                            showMenu = false
                            focusManager.clearFocus()
                            showTranslation = false
                            showFindInPage = true
                        }

                        QuickMenuToggleRow(
                            icon = Icons.Rounded.DesktopWindows,
                            title = tr("Desktop site", "Вид для ПК"),
                            checked = activeSiteDesktopMode,
                            enabled = !isHome && !readerModeActive
                        ) { enabled ->
                            SiteDesktopModeStore.setForUrl(prefs, effectivePageUrl, enabled)
                            siteDesktopRevision += 1
                            activeTab.applyDesktopMode(enabled, reload = true)
                        }

                        QuickMenuPrimaryRow(
                            icon = Icons.Rounded.Security,
                            title = "ILYRO Shield",
                            subtitle = if (settings.adBlockingEnabled) {
                                tr("uBlock Origin · protection on", "uBlock Origin · защита включена")
                            } else {
                                tr("uBlock Origin · protection off", "uBlock Origin · защита выключена")
                            },
                            selected = settings.adBlockingEnabled
                        ) {
                            showMenu = false
                            openProtectionPanel()
                        }

                        QuickMenuPrimaryRow(
                            icon = Icons.Rounded.MoreHoriz,
                            title = tr("More", "Больше"),
                            trailingText = if (menuMoreExpanded) "−" else "+"
                        ) {
                            menuMoreExpanded = !menuMoreExpanded
                        }

                        AnimatedVisibility(
                            visible = menuMoreExpanded,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(90))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                MenuAction(Icons.Rounded.VisibilityOff, tr("New private tab", "Новая приватная вкладка")) {
                                    showMenu = false
                                    createTab(isPrivate = true)
                                }
                                MenuAction(Icons.Rounded.Home, tr("Home", "Главная")) {
                                    showMenu = false
                                    openHome()
                                }

                                if (!isHome) {
                                    MenuTextScaleControl(
                                        scalePercent = ((settings.textScale * 100f) + 0.5f).toInt(),
                                        onDecrease = {
                                            val next = (settings.textScale - 0.05f).coerceAtLeast(0.85f)
                                            if (kotlin.math.abs(next - settings.textScale) > 0.001f) {
                                                onSettingsChange(settings.copy(textScale = next))
                                                textScaleReloadRevision += 1
                                            }
                                        },
                                        onIncrease = {
                                            val next = (settings.textScale + 0.05f).coerceAtMost(1.30f)
                                            if (kotlin.math.abs(next - settings.textScale) > 0.001f) {
                                                onSettingsChange(settings.copy(textScale = next))
                                                textScaleReloadRevision += 1
                                            }
                                        },
                                        onReset = {
                                            if (kotlin.math.abs(settings.textScale - 1.0f) > 0.001f) {
                                                onSettingsChange(settings.copy(textScale = 1.0f))
                                                textScaleReloadRevision += 1
                                            }
                                        }
                                    )

                                    MenuAction(
                                        Icons.Rounded.Translate,
                                        tr("Translate page", "Перевести страницу"),
                                        enabled = !readerModeActive && isHttpPage(effectivePageUrl)
                                    ) {
                                        showMenu = false
                                        focusManager.clearFocus()
                                        showFindInPage = false
                                        showTranslation = true
                                    }

                                    MenuAction(
                                        Icons.Rounded.Article,
                                        when {
                                            readerModeActive -> tr("Exit reader mode", "Выйти из режима чтения")
                                            activeTab.readerLoading -> tr("Preparing reader mode…", "Подготовка режима чтения…")
                                            else -> tr("Reader mode", "Режим чтения")
                                        },
                                        enabled = readerModeActive ||
                                            (activeTab.pageReaderable && !activeTab.readerLoading)
                                    ) {
                                        showMenu = false
                                        showFindInPage = false
                                        showTranslation = false
                                        if (readerModeActive) {
                                            activeTab.exitReaderMode()
                                        } else {
                                            activeTab.enterReaderMode { message ->
                                                Toast.makeText(
                                                    context,
                                                    message.ifBlank {
                                                        if (settings.language == AppLanguage.RUSSIAN) {
                                                            "Режим чтения недоступен для этой страницы"
                                                        } else {
                                                            "Reader mode isn't available for this page"
                                                        }
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }

                                    MenuAction(
                                        Icons.Rounded.AddToHomeScreen,
                                        tr("Add to Home screen", "На главный экран")
                                    ) {
                                        showMenu = false
                                        val requested = pinPageShortcut(
                                            context = context,
                                            url = effectivePageUrl,
                                            title = activeTab.title
                                        )
                                        if (!requested) {
                                            Toast.makeText(
                                                context,
                                                tr(
                                                    settings.language,
                                                    "Launcher doesn't support pinned shortcuts",
                                                    "Лаунчер не поддерживает закрепление ярлыков"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    if (mediaEnabledForPage) {
                                        val mediaLabel = if (detectedMedia.isEmpty()) {
                                            tr("Media", "Медиа")
                                        } else {
                                            tr(
                                                "Media · 1",
                                                "Медиа · 1"
                                            )
                                        }
                                        MenuAction(Icons.Rounded.VideoLibrary, mediaLabel) {
                                            showMenu = false
                                            showMedia = true
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickMenuTile(
                                icon = Icons.Rounded.History,
                                label = tr("History", "История"),
                                modifier = Modifier.weight(1f)
                            ) {
                                showMenu = false
                                showHistory = true
                            }
                            QuickMenuTile(
                                icon = Icons.Rounded.Bookmark,
                                label = tr("Bookmarks", "Закладки"),
                                modifier = Modifier.weight(1f)
                            ) {
                                showMenu = false
                                showBookmarks = true
                            }
                            QuickMenuTile(
                                icon = Icons.Rounded.Download,
                                label = tr("Downloads", "Загрузки"),
                                modifier = Modifier.weight(1f)
                            ) {
                                showMenu = false
                                refreshDownloads()
                                showDownloads = true
                            }
                            QuickMenuTile(
                                icon = Icons.Rounded.Add,
                                label = tr("New tab", "Новая"),
                                modifier = Modifier.weight(1f)
                            ) {
                                showMenu = false
                                createTab()
                            }
                        }

                        QuickMenuPrimaryRow(
                            icon = Icons.Rounded.Settings,
                            title = tr("Settings", "Настройки")
                        ) {
                            showMenu = false
                            showSettings = true
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        QuickMenuFooterAction(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            label = tr("Back", "Назад"),
                            enabled = readerModeActive || activeTab.canGoBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            showMenu = false
                            if (readerModeActive) activeTab.exitReaderMode()
                            else activeTab.session.goBack()
                        }
                        QuickMenuFooterAction(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            label = tr("Forward", "Вперёд"),
                            enabled = activeTab.canGoForward,
                            modifier = Modifier.weight(1f)
                        ) {
                            showMenu = false
                            activeTab.session.goForward()
                        }
                        QuickMenuFooterAction(
                            icon = Icons.Rounded.Share,
                            label = tr("Share", "Поделиться"),
                            enabled = !isHome,
                            modifier = Modifier.weight(1f)
                        ) {
                            showMenu = false
                            sharePage(
                                context = context,
                                url = effectivePageUrl,
                                title = activeTab.title,
                                language = settings.language
                            )
                        }
                        QuickMenuFooterAction(
                            icon = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            label = if (activeTab.isLoading) {
                                tr("Stop", "Стоп")
                            } else {
                                tr("Reload", "Обновить")
                            },
                            enabled = !isHome,
                            modifier = Modifier.weight(1f)
                        ) {
                            showMenu = false
                            if (activeTab.isLoading) activeTab.session.stop()
                            else activeTab.session.reload()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserPageLoadingLine(
    loading: Boolean,
    progress: Int,
    isPrivate: Boolean
) {
    var indicatorVisible by remember { mutableStateOf(false) }
    var targetProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(loading, progress) {
        if (loading) {
            indicatorVisible = true
            targetProgress = (progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.035f)
        } else if (indicatorVisible) {
            targetProgress = 1f
            delay(150L)
            indicatorVisible = false
            targetProgress = 0f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 170),
        label = "browser-page-loading-progress"
    )
    val lineColor = if (isPrivate) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        AnimatedVisibility(
            visible = indicatorVisible,
            enter = fadeIn(tween(70)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f)),
                    color = lineColor,
                    tonalElevation = 0.dp
                ) {}
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun requestHighFrameRateTree(view: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    view.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)

    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            requestHighFrameRateTree(view.getChildAt(index))
        }
    }
}

private fun siteHost(url: String): String {
    if (url == HOME_URL) return ""
    return runCatching {
        Uri.parse(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
}

private fun hostLabel(url: String): String {
    if (url == HOME_URL) return "ILYRO Home"

    return siteHost(url).ifBlank { "New tab" }
}

private fun normalizeAddress(input: String, searchEngine: SearchEngine): String {
    val value = input.trim()

    if (value.isEmpty()) return HOME_URL
    if (value.startsWith("https://") || value.startsWith("http://")) return value

    val looksLikeHost = !value.contains(' ') && value.contains('.')
    if (looksLikeHost) return "https://$value"

    val query = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    return searchEngine.queryUrl + query
}
