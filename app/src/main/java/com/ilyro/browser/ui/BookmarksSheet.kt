package com.ilyro.browser.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class BookmarkSort {
    NEWEST,
    OLDEST,
    NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    settings: BrowserSettings,
    bookmarks: List<BookmarkItem>,
    onDismiss: () -> Unit,
    onOpen: (BookmarkItem) -> Unit,
    onRemove: (BookmarkItem) -> Unit,
    onEdit: (BookmarkItem, String, String?) -> Unit,
    onClearAll: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    var query by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedSort by remember { mutableStateOf(BookmarkSort.NEWEST) }
    var editingBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    var actionBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editFolder by remember { mutableStateOf("") }
    var confirmClearAll by remember { mutableStateOf(false) }

    val folderOptions = bookmarks
        .mapNotNull { it.folder?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sorted()
    val value = query.trim()
    val visibleBookmarks = when (selectedSort) {
        BookmarkSort.NEWEST -> bookmarks
            .filter { selectedFolder == null || it.folder == selectedFolder }
            .filter { value.isEmpty() || it.title.contains(value, true) || it.url.contains(value, true) }
            .sortedByDescending { it.createdAt }
        BookmarkSort.OLDEST -> bookmarks
            .filter { selectedFolder == null || it.folder == selectedFolder }
            .filter { value.isEmpty() || it.title.contains(value, true) || it.url.contains(value, true) }
            .sortedBy { it.createdAt }
        BookmarkSort.NAME -> bookmarks
            .filter { selectedFolder == null || it.folder == selectedFolder }
            .filter { value.isEmpty() || it.title.contains(value, true) || it.url.contains(value, true) }
            .sortedBy { it.title.lowercase() }
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
                            title = tr("Bookmarks", "Закладки"),
                            subtitle = tr(
                                bookmarks.size.toString() + " saved",
                                "Сохранено: " + bookmarks.size
                            ),
                            onBack = onDismiss,
                            actions = {
                                if (bookmarks.isNotEmpty()) {
                                    if (metrics.isNarrowPhone) {
                                        IconButton(onClick = { confirmClearAll = true }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = tr("Clear", "Очистить"),
                                                modifier = Modifier.size(IlyroVisualTokens.IconSize)
                                            )
                                        }
                                    } else {
                                        TextButton(onClick = { confirmClearAll = true }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                                            )
                                            Text(
                                                tr("Clear", "Очистить"),
                                                modifier = Modifier.padding(start = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        if (bookmarks.isNotEmpty()) {
                            IlyroSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = tr("Search bookmarks", "Поиск по закладкам")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (folderOptions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                            ) {
                                FilterChip(
                                    selected = selectedFolder == null,
                                    onClick = { selectedFolder = null },
                                    label = { Text(tr("All folders", "Все папки")) }
                                )
                                folderOptions.forEach { folder ->
                                    FilterChip(
                                        selected = selectedFolder == folder,
                                        onClick = { selectedFolder = folder },
                                        label = {
                                            Text(
                                                folder,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                        }

                        if (bookmarks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                            ) {
                                BookmarkSort.entries.forEach { sort ->
                                    FilterChip(
                                        selected = selectedSort == sort,
                                        onClick = { selectedSort = sort },
                                        label = {
                                            Text(
                                                when (sort) {
                                                    BookmarkSort.NEWEST -> tr("Newest", "Сначала новые")
                                                    BookmarkSort.OLDEST -> tr("Oldest", "Сначала старые")
                                                    BookmarkSort.NAME -> tr("A–Z", "А–Я")
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(metrics.sectionGap))
                        }

                        when {
                            bookmarks.isEmpty() -> EmptyBookmarksState()
                            visibleBookmarks.isEmpty() -> EmptyBookmarksSearchState()
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(metrics.itemGap)
                                ) {
                                    items(visibleBookmarks, key = { it.url }) { bookmark ->
                                        BookmarkCard(
                                            bookmark = bookmark,
                                            compact = metrics.isCompact,
                                            narrow = metrics.isNarrowPhone,
                                            onOpen = { onOpen(bookmark) },
                                            onEdit = {
                                                editingBookmark = bookmark
                                                editTitle = bookmark.title
                                                editFolder = bookmark.folder.orEmpty()
                                            },
                                            onRemove = { onRemove(bookmark) },
                                            onMore = { actionBookmark = bookmark }
                                        )
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

    actionBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { actionBookmark = null },
            title = {
                Text(
                    bookmark.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actionBookmark = null
                            editingBookmark = bookmark
                            editTitle = bookmark.title
                            editFolder = bookmark.folder.orEmpty()
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                        )
                        Text(
                            tr("Edit", "Изменить"),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actionBookmark = null
                            onRemove(bookmark)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                        )
                        Text(
                            tr("Remove", "Удалить"),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionBookmark = null }) {
                    Text(tr("Close", "Закрыть"))
                }
            }
        )
    }

    editingBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(tr("Edit bookmark", "Изменить закладку")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                        label = { Text(tr("Title", "Название")) }
                    )
                    OutlinedTextField(
                        value = editFolder,
                        onValueChange = { editFolder = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                        label = { Text(tr("Folder", "Папка")) },
                        placeholder = { Text(tr("Optional", "Необязательно")) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = editTitle.trim().ifBlank { bookmark.url }
                        val folder = editFolder.trim().takeIf { it.isNotBlank() }
                        onEdit(bookmark, title, folder)
                        editingBookmark = null
                    }
                ) {
                    Text(tr("Save", "Сохранить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(tr("Clear bookmarks?", "Удалить все закладки?")) },
            text = {
                Text(
                    tr(
                        "All saved bookmarks will be removed.",
                        "Все сохранённые закладки будут удалены."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onClearAll()
                }) {
                    Text(tr("Clear all", "Удалить все"))
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

@Composable
private fun BookmarkCard(
    bookmark: BookmarkItem,
    compact: Boolean,
    narrow: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val tight = dense || compact
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
        Row(
            modifier = Modifier.padding(
                start = if (narrow || dense) 9.dp else 12.dp,
                top = if (dense) 7.dp else 10.dp,
                end = 4.dp,
                bottom = if (dense) 7.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        Icons.Rounded.Bookmark,
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
                    bookmark.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    bookmark.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!narrow) {
                    bookmark.folder?.takeIf { it.isNotBlank() }?.let { folder ->
                        Text(
                            tr("Folder: " + folder, "Папка: " + folder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (narrow) {
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = tr("Bookmark actions", "Действия с закладкой"),
                        modifier = Modifier.size(IlyroVisualTokens.IconSize)
                    )
                }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = tr("Edit bookmark", "Изменить закладку"),
                        modifier = Modifier.size(IlyroVisualTokens.IconSize)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = tr("Remove bookmark", "Удалить закладку"),
                        modifier = Modifier.size(IlyroVisualTokens.IconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBookmarksState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
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
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                tr("No bookmarks yet", "Закладок пока нет"),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                tr("Save a page from the ILYRO menu.", "Сохраните страницу через меню ILYRO."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyBookmarksSearchState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.LargeRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            tr("No matching bookmarks", "Подходящих закладок нет"),
            modifier = Modifier.padding(22.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
