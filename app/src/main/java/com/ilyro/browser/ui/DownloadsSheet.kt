package com.ilyro.browser.ui

import android.app.DownloadManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class DownloadCategory {
    ALL,
    APK,
    VIDEO,
    MUSIC,
    PHOTO,
    FILES
}

private enum class DownloadSort {
    NEWEST,
    NAME,
    SIZE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsSheet(
    settings: BrowserSettings,
    downloads: List<DownloadUiItem>,
    onDismiss: () -> Unit,
    onOpen: (DownloadUiItem) -> Unit,
    onRetry: (DownloadUiItem) -> Unit,
    onPause: (DownloadUiItem) -> Unit,
    onResume: (DownloadUiItem) -> Unit,
    onCancel: (DownloadUiItem) -> Unit,
    onRemove: (DownloadUiItem) -> Unit,
    onRename: (DownloadUiItem, String) -> Unit,
    onShare: (DownloadUiItem) -> Unit,
    onMove: (DownloadUiItem) -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    var selectedCategory by remember { mutableStateOf(DownloadCategory.ALL) }
    var selectedSort by remember { mutableStateOf(DownloadSort.NEWEST) }
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DownloadUiItem?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val value = query.trim()
    val visibleDownloads = downloads
        .filter { item ->
            (selectedCategory == DownloadCategory.ALL || categoryFor(item.record) == selectedCategory) &&
                (value.isEmpty() ||
                    item.record.fileName.contains(value, ignoreCase = true) ||
                    item.record.sourceUrl.contains(value, ignoreCase = true))
        }
        .let { filtered ->
            when (selectedSort) {
                DownloadSort.NEWEST -> filtered.sortedByDescending { it.record.createdAt }
                DownloadSort.NAME -> filtered.sortedBy { it.record.fileName.lowercase() }
                DownloadSort.SIZE -> filtered.sortedByDescending {
                    it.totalBytes.takeIf { size -> size > 0L } ?: -1L
                }
            }
        }

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
                            title = tr("Downloads", "Загрузки"),
                            subtitle = tr(
                                visibleDownloads.size.toString() + " shown",
                                "Показано: " + visibleDownloads.size
                            ),
                            onBack = onDismiss
                        )

                        IlyroSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = tr("Search downloads", "Поиск по загрузкам")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                        ) {
                            DownloadCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(categoryLabel(category)) },
                                    leadingIcon = if (category == DownloadCategory.ALL) {
                                        null
                                    } else {
                                        {
                                            Icon(
                                                imageVector = categoryIcon(category),
                                                contentDescription = null,
                                                modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                        ) {
                            DownloadSort.entries.forEach { sort ->
                                FilterChip(
                                    selected = selectedSort == sort,
                                    onClick = { selectedSort = sort },
                                    label = {
                                        Text(
                                            when (sort) {
                                                DownloadSort.NEWEST -> tr("Newest", "Сначала новые")
                                                DownloadSort.NAME -> tr("Name", "По имени")
                                                DownloadSort.SIZE -> tr("Size", "По размеру")
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.padding(top = metrics.itemGap))

                        when {
                            downloads.isEmpty() -> {
                                EmptyDownloadsState(
                                    title = tr("No downloads yet", "Загрузок пока нет"),
                                    subtitle = tr(
                                        "Downloaded files will appear here.",
                                        "Загруженные файлы появятся здесь."
                                    )
                                )
                            }

                            visibleDownloads.isEmpty() -> {
                                EmptyDownloadsState(
                                    title = tr("Nothing here yet", "Здесь пока пусто"),
                                    subtitle = tr(
                                        "No downloaded file matches this filter.",
                                        "Подходящих загрузок нет."
                                    )
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(metrics.itemGap)
                                ) {
                                    items(visibleDownloads, key = { it.record.id }) { item ->
                                        DownloadCard(
                                            item = item,
                                            compact = metrics.isCompact,
                                            narrow = metrics.isNarrowPhone,
                                            onOpen = { onOpen(item) },
                                            onRetry = { onRetry(item) },
                                            onPause = { onPause(item) },
                                            onResume = { onResume(item) },
                                            onCancel = { onCancel(item) },
                                            onRemove = { onRemove(item) },
                                            onRename = {
                                                renameTarget = item
                                                renameValue = item.record.fileName
                                            },
                                            onShare = { onShare(item) },
                                            onMove = { onMove(item) }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.padding(top = 14.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(tr("Rename download", "Переименовать загрузку")) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("File name", "Имя файла")) },
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(item, renameValue)
                        renameTarget = null
                    },
                    enabled = renameValue.trim().isNotEmpty()
                ) {
                    Text(tr("Save", "Сохранить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@Composable
private fun EmptyDownloadsState(title: String, subtitle: String) {
    val metrics = rememberIlyroLayoutMetrics()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 28.dp),
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
                        Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadUiItem,
    compact: Boolean,
    narrow: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val tight = dense || compact
    val canOpen = item.status == DownloadManager.STATUS_SUCCESSFUL
    val failed = item.status == DownloadManager.STATUS_FAILED
    val active = item.status == DownloadManager.STATUS_RUNNING ||
        item.status == DownloadManager.STATUS_PENDING ||
        item.status == DownloadManager.STATUS_PAUSED
    val ilyroManaged = item.record.localUri != null
    val userPaused = ilyroManaged && item.status == DownloadManager.STATUS_PAUSED
    val canPause = ilyroManaged &&
        (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING)
    var showActions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onOpen),
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (tight) 0.84f else IlyroVisualTokens.StrongSurfaceAlpha
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (narrow || dense) 9.dp else 12.dp,
                vertical = if (dense) 7.dp else 10.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(if (narrow || dense) 36.dp else 40.dp),
                    shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = IlyroVisualTokens.SubtleSurfaceAlpha
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (failed) Icons.Rounded.Refresh else fileTypeIcon(item.record),
                            contentDescription = null,
                            modifier = Modifier.size(IlyroVisualTokens.IconSize)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (narrow || dense) 9.dp else 12.dp)
                ) {
                    Text(
                        item.record.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        downloadStatus(item),
                        maxLines = if (narrow) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    failed -> {
                        TextButton(onClick = onRetry) {
                            Text(tr("Retry", "Повторить"))
                        }
                    }
                    canPause -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                Icons.Rounded.Pause,
                                contentDescription = tr("Pause download", "Поставить на паузу"),
                                modifier = Modifier.size(IlyroVisualTokens.LargeIconSize)
                            )
                        }
                    }
                    userPaused -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = tr("Resume download", "Продолжить загрузку"),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (!narrow && active) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = tr("Stop download", "Остановить загрузку"),
                            modifier = Modifier.size(IlyroVisualTokens.LargeIconSize)
                        )
                    }
                } else if (!narrow && !active && !failed) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = tr("Remove", "Удалить"),
                            modifier = Modifier.size(IlyroVisualTokens.IconSize)
                        )
                    }
                }

                IconButton(onClick = { showActions = true }) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = tr("Download actions", "Действия загрузки"),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            if (active) {
                Spacer(modifier = Modifier.padding(top = if (dense) 6.dp else 8.dp))
                item.progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showActions) {
        Dialog(
            onDismissRequest = { showActions = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            IlyroSystemBarAppearance()
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = fileTypeIcon(item.record),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = item.record.fileName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = downloadStatus(item),
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showActions = false }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = tr("Close", "Закрыть")
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            if (canOpen) {
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Download,
                                    label = tr("Open", "Открыть")
                                ) {
                                    showActions = false
                                    onOpen()
                                }
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Share,
                                    label = tr("Share", "Поделиться")
                                ) {
                                    showActions = false
                                    onShare()
                                }
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Edit,
                                    label = tr("Rename", "Переименовать")
                                ) {
                                    showActions = false
                                    onRename()
                                }
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Bookmark,
                                    label = tr("Move to another folder", "Переместить в другую папку")
                                ) {
                                    showActions = false
                                    onMove()
                                }
                            }

                            if (canPause) {
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Pause,
                                    label = tr("Pause", "Пауза")
                                ) {
                                    showActions = false
                                    onPause()
                                }
                            } else if (userPaused) {
                                DownloadDialogAction(
                                    icon = Icons.Rounded.PlayArrow,
                                    label = tr("Resume", "Продолжить")
                                ) {
                                    showActions = false
                                    onResume()
                                }
                            }

                            if (active) {
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Stop,
                                    label = tr("Stop download", "Остановить загрузку"),
                                    destructive = true
                                ) {
                                    showActions = false
                                    onCancel()
                                }
                            } else {
                                DownloadDialogAction(
                                    icon = Icons.Rounded.Delete,
                                    label = tr("Remove", "Удалить"),
                                    destructive = true
                                ) {
                                    showActions = false
                                    onRemove()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadDialogAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                color = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(IlyroVisualTokens.SmallIconSize),
                        tint = contentColor
                    )
                }
            }
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

private fun categoryFor(record: DownloadRecord): DownloadCategory {
    val mime = record.mimeType.orEmpty().lowercase()
    val extension = record.fileName.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/vnd.android.package-archive" || extension in APK_EXTENSIONS -> DownloadCategory.APK
        mime.startsWith("video/") || extension in VIDEO_EXTENSIONS -> DownloadCategory.VIDEO
        mime.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> DownloadCategory.MUSIC
        mime.startsWith("image/") || extension in IMAGE_EXTENSIONS -> DownloadCategory.PHOTO
        else -> DownloadCategory.FILES
    }
}

@Composable
private fun categoryLabel(category: DownloadCategory): String = when (category) {
    DownloadCategory.ALL -> tr("All", "Все")
    DownloadCategory.APK -> "APK"
    DownloadCategory.VIDEO -> tr("Video", "Видео")
    DownloadCategory.MUSIC -> tr("Music", "Музыка")
    DownloadCategory.PHOTO -> tr("Photos", "Фото")
    DownloadCategory.FILES -> tr("Files", "Файлы")
}

private fun categoryIcon(category: DownloadCategory): ImageVector = when (category) {
    DownloadCategory.ALL -> Icons.Rounded.Download
    DownloadCategory.APK -> Icons.Rounded.Android
    DownloadCategory.VIDEO -> Icons.Rounded.Movie
    DownloadCategory.MUSIC -> Icons.Rounded.MusicNote
    DownloadCategory.PHOTO -> Icons.Rounded.Image
    DownloadCategory.FILES -> Icons.Rounded.Description
}

private fun fileTypeIcon(record: DownloadRecord): ImageVector {
    val extension = record.fileName.substringAfterLast('.', "").lowercase()
    return when (categoryFor(record)) {
        DownloadCategory.APK -> Icons.Rounded.Android
        DownloadCategory.VIDEO -> Icons.Rounded.Movie
        DownloadCategory.MUSIC -> Icons.Rounded.MusicNote
        DownloadCategory.PHOTO -> Icons.Rounded.Image
        DownloadCategory.FILES -> if (extension in ARCHIVE_EXTENSIONS) {
            Icons.Rounded.Archive
        } else {
            Icons.Rounded.Description
        }
        DownloadCategory.ALL -> Icons.Rounded.Download
    }
}

@Composable
private fun downloadStatus(item: DownloadUiItem): String {
    val speed = item.speedBytesPerSecond.takeIf { it > 0L }?.let {
        " · " + formatSpeed(it)
    }.orEmpty()
    val ilyroManaged = item.record.localUri != null
    return when (item.status) {
        DownloadManager.STATUS_PENDING -> tr("Waiting", "Ожидание")
        DownloadManager.STATUS_RUNNING -> item.progress?.let {
            (it * 100).toInt().toString() + "% · " +
                formatBytes(item.downloadedBytes) + " / " +
                formatBytes(item.totalBytes) + speed
        } ?: tr("Downloading", "Загрузка") + " · " +
            formatBytes(item.downloadedBytes) + speed
        DownloadManager.STATUS_PAUSED -> if (ilyroManaged) {
            item.progress?.let {
                tr(
                    "Paused · " + (it * 100).toInt() + "% · tap ▶ to resume",
                    "На паузе · " + (it * 100).toInt() + "% · нажмите ▶ для продолжения"
                )
            } ?: tr("Paused · tap ▶ to resume", "На паузе · нажмите ▶ для продолжения")
        } else {
            tr(
                "Paused by Android · it will resume automatically",
                "Приостановлено Android · продолжится автоматически"
            )
        }
        DownloadManager.STATUS_SUCCESSFUL -> if (item.totalBytes > 0L) {
            tr(
                "Downloaded · " + formatBytes(item.totalBytes),
                "Загружено · " + formatBytes(item.totalBytes)
            )
        } else {
            tr("Downloaded", "Загружено")
        }
        else -> tr("Download failed · tap Retry", "Ошибка загрузки · нажмите «Повторить»")
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    val bps = bytesPerSecond.coerceAtLeast(0L)
    if (bps < 1024L) return bps.toString() + " B/s"
    val kb = bps / 1024.0
    if (kb < 1024.0) return "%.1f KB/s".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB/s".format(mb)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

private val APK_EXTENSIONS = setOf("apk", "xapk", "apks", "aab")
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp",
    "ts", "m2ts", "mpeg", "mpg", "m3u8"
)
private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "ogg", "oga", "flac", "wav", "opus", "wma"
)
private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic",
    "heif", "avif", "svg"
)
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "tgz", "gz", "bz2", "xz")
