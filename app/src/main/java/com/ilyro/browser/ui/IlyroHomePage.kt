package com.ilyro.browser.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.math.abs

/**
 * ILYRO Visual 4 start page.
 *
 * Home stays the expressive part of the browser, but its spacing and density now
 * follow the same Compact / Medium / Expanded model as the rest of the interface.
 */
@Composable
internal fun IlyroHomePage(
    settings: BrowserSettings,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    bookmarks: List<BookmarkItem>,
    quickLinks: MutableList<QuickLink>,
    isPrivate: Boolean,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onNavigate: (String) -> Unit,
    customSearchEngine: CustomSearchEngine? = null,
    customSearchEngines: List<CustomSearchEngine> = emptyList(),
    onCustomSearchEngineChange: (CustomSearchEngine?) -> Unit = {},
    onlineSearchSuggestionsEnabled: Boolean = true
) {
    val metrics = rememberIlyroLayoutMetrics()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ilyro_browser", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var omniboxFocused by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingQuickLink by remember { mutableStateOf<QuickLink?>(null) }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val activeBackground = if (darkTheme && settings.useSeparateDarkBackground) {
        settings.darkHomeBackground
    } else {
        settings.homeBackground
    }
    val wallpaperActive = activeBackground != HomeBackground.NONE
    val privateHomeTint by animateColorAsState(
        targetValue = if (isPrivate) privateModeAccent(darkTheme).copy(alpha = 0.045f) else Color.Transparent,
        animationSpec = tween(
            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
            easing = IlyroVisualTokens.MotionEnterEasing
        ),
        label = "private-home-tint"
    )

    val densityExtra = when (settings.uiDensity) {
        UiDensity.COMPACT -> (-2).dp
        UiDensity.STANDARD -> 0.dp
        UiDensity.COMFORTABLE -> 4.dp
    }
    val horizontalPadding = (metrics.horizontalPadding + densityExtra).coerceAtLeast(10.dp)
    val topSpacing = when {
        metrics.isNarrowPhone -> 14.dp
        metrics.isCompact && settings.uiDensity == UiDensity.COMPACT -> 18.dp
        metrics.isCompact -> 24.dp
        metrics.isMedium -> 30.dp
        settings.uiDensity == UiDensity.COMFORTABLE -> 42.dp
        else -> 36.dp
    }
    val animatedTopSpacing by animateDpAsState(
        targetValue = if (omniboxFocused) 10.dp else topSpacing,
        animationSpec = tween(
            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
            easing = IlyroVisualTokens.MotionEnterEasing
        ),
        label = "home-search-top-spacing"
    )

    fun persistLinks() = QuickLinkStore.save(prefs, quickLinks)

    fun moveLink(from: Int, to: Int) {
        if (from !in quickLinks.indices || to !in quickLinks.indices || from == to) return
        val item = quickLinks.removeAt(from)
        quickLinks.add(to, item)
        persistLinks()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeWallpaper(settings)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(privateHomeTint)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(animatedTopSpacing))

            AnimatedVisibility(
                visible = !omniboxFocused,
                enter = fadeIn(tween(IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs))) +
                    expandVertically(tween(IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs))),
                exit = fadeOut(tween(IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs))) +
                    shrinkVertically(tween(IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs)))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeBrandBlock(
                        isPrivate = isPrivate,
                        wallpaperActive = wallpaperActive
                    )
                    Spacer(modifier = Modifier.height(if (metrics.isNarrowPhone) 12.dp else 18.dp))
                }
            }

            IlyroHomeOmnibox(
                value = query,
                onValueChange = { query = it },
                searchEngine = searchEngine,
                history = history,
                isPrivate = isPrivate,
                onSearchEngineChange = onSearchEngineChange,
                customSearchEngine = customSearchEngine,
                customSearchEngines = customSearchEngines,
                onCustomSearchEngineChange = onCustomSearchEngineChange,
                bookmarks = bookmarks,
                quickLinks = quickLinks,
                onlineSearchSuggestionsEnabled = onlineSearchSuggestionsEnabled,
                onFocusChanged = { omniboxFocused = it },
                onNavigate = { input ->
                    if (input.isNotBlank()) {
                        query = input
                        onNavigate(input)
                    }
                },
                modifier = Modifier
                    .widthIn(max = if (settings.uiDensity == UiDensity.COMPACT) 700.dp else 760.dp)
                    .fillMaxWidth()
            )

            if (isPrivate && !omniboxFocused) {
                Spacer(modifier = Modifier.height(9.dp))
                PrivateHomeNotice()
            }

            if (settings.showQuickAccess && !omniboxFocused) {
                Spacer(
                    modifier = Modifier.height(
                        if (metrics.isNarrowPhone) 14.dp else metrics.sectionGap
                    )
                )
                QuickAccessPanel(
                    settings = settings,
                    links = quickLinks,
                    wallpaperActive = wallpaperActive,
                    editMode = editMode,
                    onEditModeChange = { editMode = it },
                    onAdd = { showAddDialog = true },
                    onOpen = onNavigate,
                    onEdit = { editingQuickLink = it },
                    onRemove = { link ->
                        quickLinks.removeAll { it.id == link.id }
                        persistLinks()
                    },
                    onMove = ::moveLink
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showAddDialog) {
        QuickLinkEditorDialog(
            title = tr("Add quick link", "Добавить быстрый сайт"),
            confirmLabel = tr("Add", "Добавить"),
            onDismiss = { showAddDialog = false },
            onSave = { label, rawUrl ->
                val url = normalizeQuickLinkUrl(rawUrl)
                if (label.isNotBlank() && url.isNotBlank() && quickLinks.size < 12) {
                    quickLinks.add(QuickLinkStore.newLink(label, url))
                    persistLinks()
                }
                showAddDialog = false
            }
        )
    }

    editingQuickLink?.let { link ->
        QuickLinkEditorDialog(
            title = tr("Edit quick link", "Изменить быстрый сайт"),
            confirmLabel = tr("Save", "Сохранить"),
            initialLabel = link.label,
            initialUrl = link.url,
            onDismiss = { editingQuickLink = null },
            onSave = { label, rawUrl ->
                val url = normalizeQuickLinkUrl(rawUrl)
                val index = quickLinks.indexOfFirst { it.id == link.id }
                if (index >= 0 && label.isNotBlank() && url.isNotBlank()) {
                    quickLinks[index] = link.copy(label = label.trim(), url = url)
                    persistLinks()
                }
                editingQuickLink = null
            }
        )
    }
}

@Composable
private fun HomeBrandBlock(isPrivate: Boolean, wallpaperActive: Boolean) {
    val metrics = rememberIlyroLayoutMetrics()
    val titleColor = when {
        wallpaperActive -> Color.White
        isPrivate -> privateModeAccent()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (wallpaperActive) {
        Color.White.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
    }
    val wallpaperShadow = Shadow(
        color = Color.Black.copy(alpha = 0.62f),
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )
    val titleStyle = if (wallpaperActive) {
        MaterialTheme.typography.headlineSmall.copy(shadow = wallpaperShadow)
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val subtitleStyle = if (wallpaperActive) {
        MaterialTheme.typography.bodySmall.copy(shadow = wallpaperShadow)
    } else {
        MaterialTheme.typography.bodySmall
    }
    val brandIconSize = when {
        metrics.isNarrowPhone -> 52.dp
        metrics.isCompact -> 58.dp
        else -> 62.dp
    }

    Column(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppBrandIcon(modifier = Modifier.size(brandIconSize))
        Text(
            text = "ILYRO",
            modifier = Modifier.padding(top = if (metrics.isNarrowPhone) 4.dp else 6.dp),
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (metrics.isCompact) 3.2.sp else 4.sp,
            color = titleColor
        )
    }
}

@Composable
private fun PrivateHomeNotice() {
    val privateAccent = privateModeAccent()
    Surface(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = privateAccent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, privateAccent.copy(alpha = 0.16f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(IlyroVisualTokens.SmallIconSize),
                tint = privateAccent
            )
            Text(
                text = tr("History is not saved", "История не сохраняется"),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickAccessPanel(
    settings: BrowserSettings,
    links: List<QuickLink>,
    wallpaperActive: Boolean,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (QuickLink) -> Unit,
    onRemove: (QuickLink) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = settings.uiDensity == UiDensity.COMPACT

    Surface(
        modifier = Modifier
            .widthIn(max = if (dense) 900.dp else 1040.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(if (dense) 22.dp else IlyroVisualTokens.LargeRadius),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (wallpaperActive) 0.84f else 0.92f
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (wallpaperActive && !metrics.isCompact) 3.dp else 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (metrics.isNarrowPhone) 9.dp else if (dense) 11.dp else 14.dp,
                    vertical = if (metrics.isNarrowPhone) 9.dp else if (dense) 10.dp else 13.dp
                )
        ) {
            val baseColumns = when {
                maxWidth < 360.dp -> 3
                maxWidth < 520.dp -> 4
                maxWidth < 700.dp -> 5
                maxWidth < 900.dp -> 6
                else -> 8
            }
            val columns = when (settings.shortcutSize) {
                HomeShortcutSize.SMALL -> (baseColumns + 1).coerceAtMost(9)
                HomeShortcutSize.STANDARD -> baseColumns
                HomeShortcutSize.LARGE -> (baseColumns - 1).coerceAtLeast(if (maxWidth < 360.dp) 3 else 3)
            }
            val itemCount = links.size
            val tileHeight = when (settings.shortcutSize) {
                HomeShortcutSize.SMALL -> if (metrics.isNarrowPhone) 62 else if (dense) 66 else 72
                HomeShortcutSize.STANDARD -> when {
                    metrics.isNarrowPhone -> 72
                    dense && columns >= 6 -> 78
                    dense -> 82
                    columns >= 6 -> 86
                    else -> 92
                }
                HomeShortcutSize.LARGE -> if (metrics.isNarrowPhone) 86 else if (dense) 96 else 104
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Quick access", "Быстрый доступ"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (links.size < 12) {
                        if (metrics.isNarrowPhone) {
                            IconButton(onClick = onAdd) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = tr("Add", "Добавить"),
                                    modifier = Modifier.size(IlyroVisualTokens.IconSize)
                                )
                            }
                        } else {
                            TextButton(onClick = onAdd) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(IlyroVisualTokens.SmallIconSize)
                                )
                                Text(
                                    tr("Add", "Добавить"),
                                    modifier = Modifier.padding(start = 5.dp)
                                )
                            }
                        }
                    }

                    if (editMode) {
                        TextButton(onClick = { onEditModeChange(false) }) {
                            Text(tr("Done", "Готово"))
                        }
                    } else {
                        IconButton(onClick = { onEditModeChange(true) }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = tr("Edit quick access", "Изменить быстрый доступ"),
                                modifier = Modifier.size(IlyroVisualTokens.IconSize)
                            )
                        }
                    }
                }

                if (itemCount > 0) {
                    val gap = if (metrics.isNarrowPhone) 6 else 8
                    val rows = (itemCount + columns - 1) / columns
                    val gridHeight = (
                        rows * tileHeight +
                            (rows - 1).coerceAtLeast(0) * gap
                        ).dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                        horizontalArrangement = Arrangement.spacedBy(gap.dp),
                        verticalArrangement = Arrangement.spacedBy(gap.dp),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(links, key = { _, link -> link.id }) { index, link ->
                            QuickSiteCard(
                                site = link,
                                index = index,
                                count = links.size,
                                columns = columns,
                                editMode = editMode,
                                shortcutSize = settings.shortcutSize,
                                showLabel = settings.showShortcutLabels,
                                narrow = metrics.isNarrowPhone,
                                gridGapDp = gap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(tileHeight.dp),
                                onClick = { onOpen(link.url) },
                                onEdit = { onEdit(link) },
                                onRemove = { onRemove(link) },
                                onMove = { target -> onMove(index, target) }
                            )
                        }

                    }
                }

                if (editMode && !metrics.isNarrowPhone) {
                    Text(
                        text = tr(
                            "Long-press and drag a shortcut to reorder it.",
                            "Зажмите ярлык и перетащите его, чтобы изменить порядок."
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSiteCard(
    site: QuickLink,
    index: Int,
    count: Int,
    columns: Int,
    editMode: Boolean,
    shortcutSize: HomeShortcutSize,
    showLabel: Boolean,
    narrow: Boolean,
    gridGapDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    var dragX by remember(site.id) { mutableStateOf(0f) }
    var dragY by remember(site.id) { mutableStateOf(0f) }
    var dragging by remember(site.id) { mutableStateOf(false) }
    var cardWidth by remember(site.id) { mutableStateOf(1) }
    var cardHeight by remember(site.id) { mutableStateOf(1) }
    val currentIndex by rememberUpdatedState(index)
    val currentOnMove by rememberUpdatedState(onMove)

    val iconSize = when (shortcutSize) {
        HomeShortcutSize.SMALL -> if (narrow) 36.dp else 40.dp
        HomeShortcutSize.STANDARD -> if (narrow) 42.dp else 46.dp
        HomeShortcutSize.LARGE -> if (narrow) 48.dp else 52.dp
    }

    val dragModifier = if (editMode) {
        Modifier
            .onSizeChanged {
                cardWidth = it.width.coerceAtLeast(1)
                cardHeight = it.height.coerceAtLeast(1)
            }
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer {
                translationX = dragX
                translationY = dragY
                val scale = if (dragging) 1.045f else 1f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(site.id, count, columns) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDragCancel = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y

                        val width = cardWidth.toFloat().coerceAtLeast(1f)
                        val height = cardHeight.toFloat().coerceAtLeast(1f)
                        val horizontalScore = abs(dragX) / width
                        val verticalScore = abs(dragY) / height
                        val current = currentIndex

                        if (verticalScore >= horizontalScore && abs(dragY) > height * 0.32f) {
                            val direction = if (dragY > 0f) 1 else -1
                            val target = (current + direction * columns).coerceIn(0, count - 1)
                            if (target != current) {
                                currentOnMove(target)
                                dragY -= direction * (height + gridGapDp.dp.toPx())
                            }
                        } else if (abs(dragX) > width * 0.32f) {
                            val direction = if (dragX > 0f) 1 else -1
                            val target = current + direction
                            val sameRow = target in 0 until count && target / columns == current / columns
                            if (sameRow) {
                                currentOnMove(target)
                                dragX -= direction * (width + gridGapDp.dp.toPx())
                            }
                        }
                    }
                )
            }
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        enabled = !editMode,
        modifier = modifier.then(dragModifier),
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = if (dragging) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        } else {
            Color.Transparent
        },
        tonalElevation = 0.dp,
        shadowElevation = if (dragging) 5.dp else 0.dp
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = if (narrow) 5.dp else 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(iconSize),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
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
                        Box(contentAlignment = Alignment.Center) {
                            QuickSiteIcon(site)
                        }
                    }

                    if (editMode) {
                        Surface(
                            onClick = onEdit,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(if (narrow) 22.dp else 24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            ),
                            tonalElevation = 0.dp,
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = tr("Edit quick link", "Изменить быстрый сайт"),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                if (showLabel) {
                    Spacer(modifier = Modifier.height(if (narrow) 4.dp else 6.dp))
                    Text(
                        text = site.label,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (editMode) {
                Surface(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp)
                        .size(if (narrow) 30.dp else 34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = IlyroVisualTokens.SubtleBorderAlpha
                        )
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = tr("Remove quick link", "Удалить быстрый сайт"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddQuickLinkCard(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(
                tr("Add", "Добавить"),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuickLinkEditorDialog(
    title: String,
    confirmLabel: String,
    initialLabel: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var label by remember(initialLabel, initialUrl) { mutableStateOf(initialLabel) }
    var url by remember(initialLabel, initialUrl) { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                    label = { Text(tr("Name", "Название")) }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.padding(top = 10.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                    label = { Text(tr("Website", "Сайт")) },
                    placeholder = { Text("example.com") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, url) },
                enabled = label.isNotBlank() && url.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "Отмена"))
            }
        }
    )
}

@Composable
private fun QuickSiteIcon(site: QuickLink) {
    var bitmap by remember(site.url) { mutableStateOf(QuickSiteIconCache.peek(site.url)) }

    LaunchedEffect(site.url) {
        if (bitmap == null) bitmap = QuickSiteIconCache.loadOnce(site.url)
    }

    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = site.label,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = site.label.take(2).uppercase(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private object QuickSiteIconCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()

    @Synchronized
    fun peek(url: String): Bitmap? = bitmaps[url]
        ?: BrowserIconCache.peek(url)?.also { bitmaps[url] = it }

    suspend fun loadOnce(url: String): Bitmap? {
        val job = synchronized(this) {
            bitmaps[url]?.let { return it }
            BrowserIconCache.peek(url)?.let {
                bitmaps[url] = it
                return it
            }
            inFlight[url]?.let { return@synchronized it }
            scope.async {
                val loaded = BrowserIconCache.loadOnce(url)
                synchronized(this@QuickSiteIconCache) {
                    if (loaded != null) bitmaps[url] = loaded
                    inFlight.remove(url)
                }
                loaded
            }.also { inFlight[url] = it }
        }
        return job.await()
    }
}

private fun normalizeQuickLinkUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    return if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
        value
    } else {
        "https://$value"
    }
}
