package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import java.net.HttpURLConnection
import java.net.URL

private fun toolbarSiteHost(url: String): String {
    if (url == HOME_URL) return ""
    return runCatching {
        Uri.parse(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
}

private fun toolbarHostLabel(url: String): String {
    if (url == HOME_URL) return "ILYRO Home"
    return toolbarSiteHost(url).ifBlank { "New tab" }
}

private object TabletFaviconCache {
    private val cache = android.util.LruCache<String, Bitmap>(96)

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }
}

private fun faviconOrigin(pageUrl: String): String {
    val parsed = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return ""
    val host = parsed.host?.trim()?.lowercase().orEmpty()
    val scheme = parsed.scheme?.lowercase()
    if (host.isBlank() || (scheme != "http" && scheme != "https")) return ""
    return "$scheme://$host"
}

private fun decodeFavicon(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > 96) sample *= 2

    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}

private suspend fun loadTabletFavicon(pageUrl: String): Bitmap? {
    val origin = faviconOrigin(pageUrl)
    if (origin.isBlank()) return null
    TabletFaviconCache.get(origin)?.let { return it }

    return withContext(Dispatchers.IO) {
        val connection = runCatching {
            (URL("$origin/favicon.ico").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                instanceFollowRedirects = true
                useCaches = true
                setRequestProperty("User-Agent", "Mozilla/5.0 ILYRO")
                setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                )
            }
        }.getOrNull() ?: return@withContext null

        try {
            val code = connection.responseCode
            if (code !in 200..299) return@withContext null

            val declaredSize = connection.contentLengthLong
            if (declaredSize > 768 * 1024) return@withContext null

            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > 768 * 1024) return@withContext null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }

            decodeFavicon(bytes)?.also { bitmap ->
                TabletFaviconCache.put(origin, bitmap)
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
private fun TabletSiteFavicon(
    pageUrl: String,
    accent: Color
) {
    val origin = remember(pageUrl) { faviconOrigin(pageUrl) }
    var favicon by remember(origin) { mutableStateOf(TabletFaviconCache.get(origin)) }

    LaunchedEffect(origin) {
        if (origin.isNotBlank() && favicon == null) {
            favicon = loadTabletFavicon(pageUrl)
        }
    }

    val bitmap = favicon
    if (bitmap != null) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            )
        }
    } else {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.10f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = toolbarSiteHost(pageUrl)
                        .firstOrNull()
                        ?.uppercaseChar()
                        ?.toString()
                        ?: "•",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun TabletTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onSelect: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNewTab: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val listState = rememberLazyListState()
    val stripHeight = if (dense) { if (metrics.isMedium) 46.dp else 48.dp } else { if (metrics.isMedium) 50.dp else 52.dp }
    val tabMinWidth = if (dense) { if (metrics.isMedium) 126.dp else 138.dp } else { if (metrics.isMedium) 138.dp else 150.dp }
    val tabMaxWidth = if (dense) { if (metrics.isMedium) 188.dp else 204.dp } else { if (metrics.isMedium) 205.dp else 225.dp }

    LaunchedEffect(activeTabId, tabs.size) {
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            delay(48L)
            listState.animateScrollToItem(activeIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = IlyroVisualTokens.SubtleBorderAlpha
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (dense) 7.dp else 9.dp, vertical = if (dense) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = tabs,
                    key = { it.id }
                ) { tab ->
                    val selected = tab.id == activeTabId
                    val accent = if (tab.isPrivate) {
                        Color(0xFF8B5CF6)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val title = if (tab.url == HOME_URL) {
                        tr("New tab", "Новая вкладка")
                    } else {
                        tab.title.ifBlank { toolbarHostLabel(tab.url) }
                    }

                    Surface(
                        onClick = { onSelect(tab) },
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = tabMinWidth, max = tabMaxWidth),
                        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                        color = if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
                        } else {
                            Color.Transparent
                        },
                        border = if (selected) {
                            BorderStroke(
                                1.dp,
                                accent.copy(alpha = IlyroVisualTokens.SelectedBorderAlpha)
                            )
                        } else {
                            null
                        },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = if (dense) 7.dp else 9.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            when {
                                tab.isPrivate -> {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = accent
                                    )
                                }
                                tab.url == HOME_URL -> {
                                    Icon(
                                        Icons.Rounded.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (selected) {
                                            accent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                else -> {
                                    TabletSiteFavicon(
                                        pageUrl = tab.url,
                                        accent = accent
                                    )
                                }
                            }

                            Text(
                                text = title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            Surface(
                                onClick = { onClose(tab) },
                                modifier = Modifier.size(if (dense) 28.dp else 30.dp),
                                shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                                color = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = tr("Close tab", "Закрыть вкладку"),
                                        modifier = Modifier.size(17.dp),
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                onClick = onNewTab,
                modifier = Modifier
                    .padding(start = if (dense) 5.dp else 7.dp)
                    .size(if (dense) 36.dp else 40.dp),
                shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = IlyroVisualTokens.SubtleBorderAlpha
                    )
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = tr("New tab", "Новая вкладка"),
                        modifier = Modifier.size(IlyroVisualTokens.LargeIconSize),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun BrowserBottomBar(
    address: String,
    onAddressChange: (String) -> Unit,
    onAddressFocusChanged: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    tabCount: Int,
    isPrivate: Boolean,
    darkTheme: Boolean,
    mediaCount: Int,
    onMedia: () -> Unit,
    homeMode: Boolean,
    position: ToolbarPosition,
    toolbarActions: Set<ToolbarAction>,
    uiDensity: UiDensity
) {
    val metrics = rememberIlyroLayoutMetrics()
    val compact = metrics.isCompact
    val veryCompact = metrics.isNarrowPhone
    val baseButtonSize = when {
        veryCompact -> 38
        compact -> 40
        else -> 44
    }
    val buttonSize = when (uiDensity) {
        UiDensity.COMPACT -> (baseButtonSize - 2).coerceAtLeast(36)
        UiDensity.STANDARD -> baseButtonSize
        UiDensity.COMFORTABLE -> baseButtonSize + 2
    }
    val barPadding = when (uiDensity) {
        UiDensity.COMPACT -> if (compact) 5.dp else 8.dp
        UiDensity.STANDARD -> if (compact) 7.dp else 10.dp
        UiDensity.COMFORTABLE -> if (compact) 9.dp else 12.dp
    }
    val gap = when (uiDensity) {
        UiDensity.COMPACT -> 2.dp
        UiDensity.STANDARD -> if (compact) 3.dp else 5.dp
        UiDensity.COMFORTABLE -> if (compact) 4.dp else 7.dp
    }
    val addressHeight = when (uiDensity) {
        UiDensity.COMPACT -> if (compact) 48.dp else 50.dp
        UiDensity.STANDARD -> if (compact) 48.dp else 50.dp
        UiDensity.COMFORTABLE -> if (compact) 52.dp else 54.dp
    }
    var fieldValue by remember { mutableStateOf(TextFieldValue(address)) }
    var fieldFocused by remember { mutableStateOf(false) }
    val privateBarTarget = if (darkTheme) Color(0xFF17131F) else Color(0xFFF8F5FC)
    val barColor by animateColorAsState(
        targetValue = if (isPrivate) privateBarTarget else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = IlyroVisualTokens.MotionStandardMs),
        label = "browser-mode-bar"
    )
    val density = LocalDensity.current
    val imeBottomPx = if (
        position == ToolbarPosition.BOTTOM &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    ) {
        WindowInsets.ime.getBottom(density)
    } else {
        0
    }
    val imeOffset = with(density) { imeBottomPx.toDp() }
    val showForward = ToolbarAction.FORWARD in toolbarActions &&
        (!veryCompact || canGoForward)

    LaunchedEffect(address) {
        if (fieldValue.text != address) {
            fieldValue = TextFieldValue(address)
        }
    }

    LaunchedEffect(fieldFocused) {
        if (fieldFocused) {
            delay(32L)
            fieldValue = fieldValue.copy(
                selection = TextRange(0, fieldValue.text.length)
            )
        }
    }

    Surface(
        modifier = Modifier.offset(y = -imeOffset),
        color = barColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = IlyroVisualTokens.SubtleBorderAlpha
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (position == ToolbarPosition.BOTTOM) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (position == ToolbarPosition.BOTTOM) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = barPadding, vertical = if (compact) 6.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            if (ToolbarAction.BACK in toolbarActions) {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    enabled = canGoBack,
                    onClick = onBack,
                    contentDescription = tr("Back", "Назад"),
                    sizeDp = buttonSize
                )
            }
            if (showForward) {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = canGoForward,
                    onClick = onForward,
                    contentDescription = tr("Forward", "Вперёд"),
                    sizeDp = buttonSize
                )
            }

            if (homeMode) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                IlyroAddressOmnibox(
                    value = fieldValue,
                    onValueChange = { updated ->
                        fieldValue = updated
                        onAddressChange(updated.text)
                    },
                    onFocusChanged = { focusedNow ->
                        if (focusedNow && !fieldFocused) {
                            fieldValue = fieldValue.copy(
                                selection = TextRange(0, fieldValue.text.length)
                            )
                        }
                        fieldFocused = focusedNow
                        onAddressFocusChanged(focusedNow)
                    },
                    onNavigate = onNavigate,
                    searchEngine = searchEngine,
                    history = history,
                    isPrivate = isPrivate,
                    compact = compact,
                    fieldHeight = addressHeight,
                    suggestionsAbove = position == ToolbarPosition.BOTTOM,
                    trailingIcon = if (mediaCount > 0) {
                        { MediaFoundButton(count = mediaCount, onClick = onMedia) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (ToolbarAction.TABS in toolbarActions) {
                TabCountButton(
                    count = tabCount,
                    isPrivate = isPrivate,
                    darkTheme = darkTheme,
                    onClick = onTabs,
                    sizeDp = buttonSize
                )
            }
            ChromeIconButton(
                icon = Icons.Rounded.MoreHoriz,
                enabled = true,
                onClick = onMenu,
                contentDescription = tr("More options", "Дополнительные действия"),
                sizeDp = buttonSize
            )
        }
    }
}

@Composable
internal fun BrowserTopNoticeCard(
    notice: BrowserTopNotice,
    onClick: () -> Unit,
    onActionClick: () -> Unit = onClick,
    onDismiss: () -> Unit = {},
    allowSwipeUp: Boolean = true,
    allowSwipeDown: Boolean = true
) {
    val metrics = rememberIlyroLayoutMetrics()
    var dragOffsetX by remember(notice.id) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(notice.id) { mutableFloatStateOf(0f) }
    var dismissing by remember(notice.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 64.dp.toPx() }
    val maxDismissOffset = with(density) { 240.dp.toPx() }

    // Reuse the active browser palette so notices follow light/dark mode,
    // wallpaper-derived accents, and the selected browser style.
    val themeColors = MaterialTheme.colorScheme
    val snackbarColor = themeColors.surfaceVariant.copy(alpha = 0.96f)
    val snackbarTextColor = themeColors.onSurface
    val snackbarSecondaryColor = themeColors.onSurfaceVariant.copy(alpha = 0.78f)
    val snackbarActionColor = themeColors.primary

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(if (metrics.isNarrowPhone) 0.90f else 0.52f)
            .widthIn(min = 180.dp, max = 420.dp)
            .graphicsLayer {
                translationX = dragOffsetX
                translationY = dragOffsetY
                alpha = 1f - (
                    (abs(dragOffsetX) + abs(dragOffsetY)) /
                        (maxDismissOffset * 1.5f)
                    ).coerceIn(0f, 0.38f)
            }
            .pointerInput(notice.id, allowSwipeUp, allowSwipeDown) {
                detectDragGestures(
                    onDragEnd = {
                        if (dismissing) return@detectDragGestures
                        val currentX = dragOffsetX
                        val currentY = dragOffsetY
                        val horizontalDismiss = abs(currentX) >= dismissThreshold
                        val verticalDismiss =
                            (allowSwipeUp && currentY <= -dismissThreshold) ||
                                (allowSwipeDown && currentY >= dismissThreshold)

                        if (!horizontalDismiss && !verticalDismiss) {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            return@detectDragGestures
                        }

                        dismissing = true
                        if (horizontalDismiss && abs(currentX) >= abs(currentY)) {
                            dragOffsetX = if (currentX >= 0f) maxDismissOffset else -maxDismissOffset
                        } else {
                            dragOffsetY = if (currentY < 0f) -maxDismissOffset else maxDismissOffset
                        }
                        onDismiss()
                    },
                    onDragCancel = {
                        if (!dismissing) {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        }
                    }
                ) { _, dragAmount ->
                    val minY = if (allowSwipeUp) -maxDismissOffset else 0f
                    val maxY = if (allowSwipeDown) maxDismissOffset else 0f
                    dragOffsetX = (dragOffsetX + dragAmount.x)
                        .coerceIn(-maxDismissOffset, maxDismissOffset)
                    dragOffsetY = (dragOffsetY + dragAmount.y)
                        .coerceIn(minY, maxY)
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = snackbarColor,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = snackbarTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = snackbarSecondaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            notice.actionLabel?.let { label ->
                Surface(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    contentColor = snackbarActionColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = snackbarActionColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCountButton(
    count: Int,
    isPrivate: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
    sizeDp: Int
) {
    val privateAccent = if (darkTheme) Color(0xFFC4B5FD) else Color(0xFF7048D8)
    val targetBackground = if (isPrivate) {
        privateAccent.copy(alpha = if (darkTheme) 0.14f else 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = IlyroVisualTokens.MotionStandardMs),
        label = "tab-count-background"
    )
    val content by animateColorAsState(
        targetValue = if (isPrivate) privateAccent else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = IlyroVisualTokens.MotionStandardMs),
        label = "tab-count-content"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(sizeDp.dp),
        shape = RoundedCornerShape(if (sizeDp < 40) 12.dp else 14.dp),
        color = background,
        border = BorderStroke(
            1.dp,
            if (isPrivate) {
                privateAccent.copy(alpha = IlyroVisualTokens.SelectedBorderAlpha)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = IlyroVisualTokens.SubtleBorderAlpha
                )
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isPrivate) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = tr("Private tabs", "Приватные вкладки"),
                        modifier = Modifier.size(if (sizeDp < 40) 13.dp else 14.dp),
                        tint = content
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = content
                    )
                }
            } else {
                Text(
                    text = count.toString(),
                    fontSize = if (sizeDp < 40) 15.sp else 17.sp,
                    maxLines = 1,
                    color = content,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChromeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    sizeDp: Int = 42
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(sizeDp.dp),
        shape = RoundedCornerShape(if (sizeDp < 40) 12.dp else 14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (sizeDp < 40) 17.sp else 20.sp,
                maxLines = 1,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                },
                fontWeight = if (label.any { it.isDigit() }) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                }
            )
        }
    }
}

@Composable
private fun ChromeIconButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    sizeDp: Int = 42
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(sizeDp.dp),
        shape = RoundedCornerShape(if (sizeDp < 40) 12.dp else 14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(if (sizeDp < 40) 20.dp else 22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                }
            )
        }
    }
}

@Composable
private fun MediaFoundButton(
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(end = 3.dp)
            .height(30.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (count > 1) 8.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = tr("Media found", "Видео найдено"),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (count > 1) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
