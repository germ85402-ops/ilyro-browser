package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class HistorySection(
    val date: LocalDate,
    val entries: List<HistoryItem>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun HistorySheet(
    settings: BrowserSettings,
    history: List<HistoryItem>,
    onDismiss: () -> Unit,
    onOpen: (HistoryItem) -> Unit,
    onRemove: (HistoryItem) -> Unit,
    onClearAll: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    var query by remember { mutableStateOf("") }
    var clearMenuExpanded by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val value = query.trim()
    val filteredHistory = remember(history, value) {
        if (value.isEmpty()) {
            history
        } else {
            history.filter { entry ->
                val host = historyHost(entry.url)
                entry.title.contains(value, ignoreCase = true) ||
                    entry.url.contains(value, ignoreCase = true) ||
                    host.contains(value, ignoreCase = true)
            }
        }
    }
    val sections = remember(filteredHistory) { groupHistoryByDay(filteredHistory) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        IlyroSystemBarAppearance()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                IlyroWallpaperBackdrop(settings)

                IlyroAdaptiveContent {
                    Column(modifier = Modifier.fillMaxSize()) {
                        IlyroScreenHeader(
                            title = tr("History", "История"),
                            subtitle = if (value.isEmpty()) {
                                tr("${history.size} visits", "Посещений: ${history.size}")
                            } else {
                                tr(
                                    "${filteredHistory.size} of ${history.size} results",
                                    "Найдено ${filteredHistory.size} из ${history.size}"
                                )
                            },
                            onBack = onDismiss,
                            actions = {
                                if (history.isNotEmpty()) {
                                    Box {
                                        if (metrics.isNarrowPhone) {
                                            IconButton(onClick = { clearMenuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = tr("Clear", "Очистить"),
                                                    modifier = Modifier.size(IlyroVisualTokens.IconSize)
                                                )
                                            }
                                        } else {
                                            TextButton(onClick = { clearMenuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                                                )
                                                Text(
                                                    text = tr("Clear", "Очистить"),
                                                    modifier = Modifier.padding(start = 6.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = clearMenuExpanded,
                                            onDismissRequest = { clearMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(tr("Last hour", "Последний час")) },
                                                onClick = {
                                                    clearMenuExpanded = false
                                                    val cutoff = System.currentTimeMillis() - 60L * 60L * 1000L
                                                    history.toList()
                                                        .filter { it.visitedAt >= cutoff }
                                                        .forEach(onRemove)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("Today", "Сегодня")) },
                                                onClick = {
                                                    clearMenuExpanded = false
                                                    val today = LocalDate.now()
                                                    history.toList()
                                                        .filter { historyDate(it.visitedAt) == today }
                                                        .forEach(onRemove)
                                                }
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                                },
                                                text = { Text(tr("All history", "Вся история")) },
                                                onClick = {
                                                    clearMenuExpanded = false
                                                    confirmClearAll = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        if (history.isNotEmpty()) {
                            IlyroSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = tr(
                                    "Search pages, sites or addresses",
                                    "Поиск страниц, сайтов или адресов"
                                )
                            )
                            Spacer(modifier = Modifier.height(metrics.sectionGap))
                        }

                        when {
                            history.isEmpty() -> {
                                EmptyHistoryMessage(
                                    title = tr("No history yet", "Истории пока нет"),
                                    subtitle = tr(
                                        "Pages you visit in regular tabs will appear here.",
                                        "Страницы из обычных вкладок будут появляться здесь."
                                    )
                                )
                            }

                            filteredHistory.isEmpty() -> {
                                EmptyHistoryMessage(
                                    title = tr("Nothing found", "Ничего не найдено"),
                                    subtitle = tr(
                                        "Try a page title, website or part of an address.",
                                        "Попробуйте название страницы, сайта или часть адреса."
                                    )
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(metrics.itemGap)
                                ) {
                                    sections.forEach { section ->
                                        stickyHeader(key = "header-${section.date}") {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = androidx.compose.ui.graphics.Color.Transparent
                                            ) {
                                                Text(
                                                    text = historySectionLabel(section.date),
                                                    modifier = Modifier.padding(top = 9.dp, bottom = 6.dp),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        items(
                                            items = section.entries,
                                            key = { entry -> "${entry.visitedAt}|${entry.url}" }
                                        ) { entry ->
                                            HistoryEntryCard(
                                                entry = entry,
                                                compact = metrics.isCompact,
                                                narrow = metrics.isNarrowPhone,
                                                onOpen = { onOpen(entry) },
                                                onRemove = { onRemove(entry) }
                                            )
                                        }
                                    }
                                    item { Spacer(modifier = Modifier.height(22.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(tr("Clear history?", "Очистить историю?")) },
            text = {
                Text(
                    tr(
                        "All saved browsing history will be removed. This cannot be undone.",
                        "Вся сохранённая история посещений будет удалена. Это действие нельзя отменить."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        onClearAll()
                    }
                ) {
                    Text(tr("Clear all", "Очистить всё"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryCard(
    entry: HistoryItem,
    compact: Boolean,
    narrow: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    var actionsExpanded by remember(entry.url, entry.visitedAt) { mutableStateOf(false) }
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val tight = dense || compact
    val clipboard = LocalClipboardManager.current
    val host = remember(entry.url) { historyHost(entry.url) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { actionsExpanded = true }
                ),
            shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
            color = MaterialTheme.colorScheme.surface.copy(
                alpha = if (tight) 0.84f else IlyroVisualTokens.StrongSurfaceAlpha
            ),
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
                modifier = Modifier.padding(
                    start = if (narrow || dense) 9.dp else 12.dp,
                    top = if (dense) 7.dp else 10.dp,
                    end = 4.dp,
                    bottom = if (dense) 7.dp else 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryFavicon(
                    url = entry.url,
                    modifier = Modifier.size(if (narrow || dense) 36.dp else 42.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (narrow || dense) 9.dp else 12.dp)
                ) {
                    Text(
                        text = entry.title.ifBlank { host.ifBlank { entry.url } },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = host.ifBlank { entry.url },
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  ·  ${relativeVisitTime(entry.visitedAt)}",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                    if (!narrow && entry.url != host && host.isNotBlank()) {
                        Text(
                            text = entry.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                        )
                    }
                }

                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = tr("History item actions", "Действия с записью"),
                        modifier = Modifier.size(IlyroVisualTokens.IconSize)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { actionsExpanded = false },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                text = { Text(tr("Open", "Открыть")) },
                onClick = {
                    actionsExpanded = false
                    onOpen()
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                text = { Text(tr("Copy link", "Копировать ссылку")) },
                onClick = {
                    actionsExpanded = false
                    clipboard.setText(AnnotatedString(entry.url))
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                text = { Text(tr("Remove from history", "Удалить из истории")) },
                onClick = {
                    actionsExpanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun HistoryFavicon(
    url: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) { mutableStateOf(HistoryFaviconCache.peek(url)) }

    LaunchedEffect(url) {
        if (bitmap == null) bitmap = HistoryFaviconCache.loadOnce(url)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = IlyroVisualTokens.SubtleSurfaceAlpha
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(27.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryMessage(
    title: String,
    subtitle: String
) {
    val metrics = rememberIlyroLayoutMetrics()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.LargeRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (metrics.isCompact) 20.dp else 28.dp,
                vertical = if (metrics.isCompact) 24.dp else 30.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(11.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun groupHistoryByDay(history: List<HistoryItem>): List<HistorySection> {
    return history
        .sortedByDescending { it.visitedAt }
        .groupBy { historyDate(it.visitedAt) }
        .map { (date, entries) -> HistorySection(date, entries) }
        .sortedByDescending { it.date }
}

@Composable
private fun historySectionLabel(date: LocalDate): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    return when (date) {
        today -> tr("Today", "Сегодня")
        today.minusDays(1) -> tr("Yesterday", "Вчера")
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }
}

private fun historyDate(timestamp: Long): LocalDate {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun historyHost(url: String): String {
    return runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }
        .getOrDefault("")
}

private fun relativeVisitTime(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

private object HistoryFaviconCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val attempted = mutableSetOf<String>()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

    @Synchronized
    fun peek(url: String): Bitmap? = bitmaps[url]

    suspend fun loadOnce(url: String): Bitmap? {
        val job = synchronized(this) {
            bitmaps[url]?.let { return it }
            inFlight[url]?.let { return@synchronized it }
            if (url in attempted) return null

            attempted.add(url)
            scope.async {
                val loaded = fetchHistoryFavicon(url)
                synchronized(this@HistoryFaviconCache) {
                    if (loaded != null) bitmaps[url] = loaded
                    inFlight.remove(url)
                }
                loaded
            }.also { inFlight[url] = it }
        }
        return job.await()
    }
}

private suspend fun fetchHistoryFavicon(siteUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val connection = (
            URL("https://www.google.com/s2/favicons?sz=128&domain_url=$encoded")
                .openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = 3000
            readTimeout = 3000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ILYRO Android")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
