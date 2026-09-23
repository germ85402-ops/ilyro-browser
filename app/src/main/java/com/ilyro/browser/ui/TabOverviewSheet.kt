package com.ilyro.browser.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

internal data class TabOverviewItem(
    val id: String,
    val title: String,
    val host: String,
    val isHome: Boolean,
    val isPrivate: Boolean,
    val selected: Boolean,
    val isPinned: Boolean,
    val groupName: String?,
    val preview: Bitmap? = null
)

private enum class TabOverviewSection { NORMAL, PRIVATE, GROUPS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TabOverviewSheet(
    settings: BrowserSettings,
    tabs: List<TabOverviewItem>,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    onDismissNotice: () -> Unit = {},
    onTogglePinned: (String) -> Unit,
    onUpdateGroup: (String, String?) -> Unit,
    tabCloseNotice: BrowserTopNotice? = null,
    onUndoClose: () -> Unit = {},
    restoredTabId: String? = null,
    restoreGeneration: Long = 0L
) {
    val metrics = rememberIlyroLayoutMetrics()
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(TabOverviewSection.NORMAL) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var editingGroupFor by remember { mutableStateOf<TabOverviewItem?>(null) }
    var groupDraft by remember { mutableStateOf("") }
    var actionTabId by remember { mutableStateOf<String?>(null) }
    var openingTabId by remember { mutableStateOf<String?>(null) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestSheetDismiss(after: () -> Unit = onDismiss) {
        if (!sheetVisible || pendingExitAction != null) return
        pendingExitAction = after
        sheetVisible = false
    }

    LaunchedEffect(Unit) {
        sheetVisible = true
    }

    LaunchedEffect(sheetVisible, pendingExitAction) {
        if (!sheetVisible) {
            val action = pendingExitAction ?: return@LaunchedEffect
            delay(IlyroVisualTokens.motionDelay(IlyroVisualTokens.MotionScreenMs))
            if (!sheetVisible && pendingExitAction != null) {
                action()
            }
        }
    }

    val groups = tabs
        .mapNotNull { it.groupName?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .sorted()
    val queryValue = query.trim()

    val sectionTabs = when (section) {
        TabOverviewSection.NORMAL -> tabs.filterNot { it.isPrivate }
        TabOverviewSection.PRIVATE -> tabs.filter { it.isPrivate }
        TabOverviewSection.GROUPS -> tabs.filter { !it.groupName.isNullOrBlank() }
    }

    val visibleTabs = sectionTabs
        .filter { tab ->
            (section != TabOverviewSection.GROUPS || selectedGroup == null || tab.groupName == selectedGroup) &&
                (queryValue.isBlank() ||
                    tab.title.contains(queryValue, ignoreCase = true) ||
                    tab.host.contains(queryValue, ignoreCase = true) ||
                    tab.groupName.orEmpty().contains(queryValue, ignoreCase = true))
        }
        .sortedWith(
            compareByDescending<TabOverviewItem> { it.isPinned }
                .thenBy { it.groupName.orEmpty().lowercase() }
                .thenByDescending { it.selected }
        )

    val baseColumns = when (metrics.windowClass) {
        IlyroWindowClass.COMPACT -> if (metrics.isNarrowPhone) 1 else 2
        IlyroWindowClass.MEDIUM -> 3
        IlyroWindowClass.EXPANDED -> if (metrics.widthDp >= 1200) 5 else 4
    }
    val columns = when (settings.tabLayout) {
        TabLayoutMode.GRID -> baseColumns
        TabLayoutMode.LIST -> 1
        TabLayoutMode.COMPACT -> when (metrics.windowClass) {
            IlyroWindowClass.COMPACT -> if (metrics.isNarrowPhone) 1 else 2
            IlyroWindowClass.MEDIUM -> 4
            IlyroWindowClass.EXPANDED -> if (metrics.widthDp >= 1200) 6 else 5
        }
    }
    val cardHeight = when {
        settings.tabPreviewSize == TabPreviewSize.NONE -> {
            if (settings.uiDensity == UiDensity.COMPACT) 84.dp else 94.dp
        }
        columns == 1 && metrics.isCompact -> {
            if (settings.tabPreviewSize == TabPreviewSize.MEDIUM) 184.dp else 210.dp
        }
        settings.tabLayout == TabLayoutMode.COMPACT && settings.tabPreviewSize == TabPreviewSize.MEDIUM -> 160.dp
        settings.tabLayout == TabLayoutMode.COMPACT -> 188.dp
        settings.tabPreviewSize == TabPreviewSize.MEDIUM -> if (columns == 1) 194.dp else 206.dp
        columns == 1 -> 242.dp
        else -> 228.dp
    }
    val gridGap = when (settings.uiDensity) {
        UiDensity.COMPACT -> 8.dp
        UiDensity.STANDARD -> metrics.itemGap
        UiDensity.COMFORTABLE -> 14.dp
    }

    LaunchedEffect(openingTabId) {
        val id = openingTabId ?: return@LaunchedEffect
        delay(IlyroVisualTokens.motionDelay(IlyroVisualTokens.MotionFastMs))
        requestSheetDismiss { onSelect(id) }
    }

    Dialog(
        onDismissRequest = { requestSheetDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        IlyroSystemBarAppearance()
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                        easing = IlyroVisualTokens.MotionEnterEasing
                    )
                ) + fadeIn(
                    tween(
                        durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                        easing = IlyroVisualTokens.MotionEnterEasing
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                        easing = IlyroVisualTokens.MotionExitEasing
                    )
                ) + fadeOut(
                    tween(
                        durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                        easing = IlyroVisualTokens.MotionExitEasing
                    )
                ),
                modifier = Modifier.fillMaxSize()
            ) {
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (settings.uiDensity == UiDensity.COMPACT) { if (metrics.isCompact) 78.dp else 84.dp } else { if (metrics.isCompact) 88.dp else 94.dp })
                    ) {
                        IlyroScreenHeader(
                            title = tr("Tabs", "Вкладки"),
                            subtitle = tr("${tabs.size} open", "Открыто: ${tabs.size}"),
                            onBack = { requestSheetDismiss() },
                            actions = {
                                if (tabs.isNotEmpty()) {
                                    TextButton(onClick = { showCloseAllDialog = true }) {
                                        Text(
                                            if (metrics.isNarrowPhone) {
                                                tr("Close", "Закрыть")
                                            } else {
                                                tr("Close all", "Закрыть все")
                                            }
                                        )
                                    }
                                }
                            }
                        )

                        if (tabs.size >= 5 || query.isNotBlank()) {
                            IlyroSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = tr("Search tabs", "Поиск по вкладкам")
                            )
                        }

                        if (section == TabOverviewSection.GROUPS && groups.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                            ) {
                                FilterChip(
                                    selected = selectedGroup == null,
                                    onClick = { selectedGroup = null },
                                    label = { Text(tr("All groups", "Все группы")) }
                                )
                                groups.forEach { group ->
                                    FilterChip(
                                        selected = selectedGroup == group,
                                        onClick = {
                                            selectedGroup = if (selectedGroup == group) null else group
                                        },
                                        label = { Text(group) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(metrics.sectionGap))

                        if (visibleTabs.isEmpty()) {
                            EmptyTabsState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                section = section,
                                queryActive = queryValue.isNotBlank(),
                                groupsEmpty = groups.isEmpty()
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(gridGap),
                                verticalArrangement = Arrangement.spacedBy(gridGap),
                                contentPadding = PaddingValues(bottom = 10.dp)
                            ) {
                                items(visibleTabs, key = { it.id }) { tab ->
                                    Box(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(
                                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                                                easing = IlyroVisualTokens.MotionEnterEasing
                                            ),
                                            placementSpec = if (IlyroVisualTokens.systemMotionEnabled()) {
                                                spring(dampingRatio = 0.90f, stiffness = 420f)
                                            } else {
                                                snap()
                                            },
                                            fadeOutSpec = tween(
                                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                                                easing = IlyroVisualTokens.MotionExitEasing
                                            )
                                        )
                                    ) {
                                        val restoreKey = if (tab.id == restoredTabId) restoreGeneration else 0L
                                        key(tab.id, restoreKey) {
                                            ModernTabCard(
                                                tab = tab,
                                                cardHeight = cardHeight,
                                                previewSize = settings.tabPreviewSize,
                                                actionsVisible = actionTabId == tab.id,
                                                opening = openingTabId == tab.id,
                                                dimmedForOpening = openingTabId != null && openingTabId != tab.id,
                                                compact = metrics.isCompact,
                                                onSelect = {
                                                    if (openingTabId == null) {
                                                        actionTabId = null
                                                        openingTabId = tab.id
                                                    }
                                                },
                                                onClose = {
                                                    if (actionTabId == tab.id) actionTabId = null
                                                    onClose(tab.id)
                                                },
                                                onLongPress = {
                                                    if (openingTabId == null) {
                                                        actionTabId = if (actionTabId == tab.id) null else tab.id
                                                    }
                                                },
                                                onTogglePinned = {
                                                    onTogglePinned(tab.id)
                                                    actionTabId = null
                                                },
                                                onEditGroup = {
                                                    groupDraft = tab.groupName.orEmpty()
                                                    editingGroupFor = tab
                                                    actionTabId = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                tabCloseNotice?.let { notice ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        BrowserTopNoticeCard(
                            notice = notice,
                            onClick = {},
                            onActionClick = onUndoClose,
                            onDismiss = onDismissNotice,
                            allowSwipeUp = true,
                            allowSwipeDown = true
                        )
                    }
                }

                TabBottomControls(
                    section = section,
                    regularCount = tabs.count { !it.isPrivate },
                    privateCount = tabs.count { it.isPrivate },
                    groupCount = groups.size,
                    compact = metrics.isCompact,
                    narrow = metrics.isSmallPhone,
                    horizontalPadding = metrics.horizontalPadding,
                    onSectionChange = { next ->
                        section = next
                        selectedGroup = null
                        actionTabId = null
                    },
                    onNewTab = {
                        requestSheetDismiss {
                            if (section == TabOverviewSection.PRIVATE) {
                                onNewPrivateTab()
                            } else {
                                onNewTab()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
            }
        }
    }

    editingGroupFor?.let { tab ->
        AlertDialog(
            onDismissRequest = { editingGroupFor = null },
            title = { Text(tr("Tab group", "Группа вкладки")) },
            text = {
                OutlinedTextField(
                    value = groupDraft,
                    onValueChange = { groupDraft = it.take(48) },
                    singleLine = true,
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                    label = { Text(tr("Group name", "Название группы")) },
                    placeholder = { Text(tr("For example: Video", "Например: Видео")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateGroup(tab.id, groupDraft.trim().ifBlank { null })
                    editingGroupFor = null
                }) {
                    Text(tr("Save", "Сохранить"))
                }
            },
            dismissButton = {
                Row {
                    if (!tab.groupName.isNullOrBlank()) {
                        TextButton(onClick = {
                            onUpdateGroup(tab.id, null)
                            editingGroupFor = null
                        }) {
                            Text(tr("Remove", "Убрать"))
                        }
                    }
                    TextButton(onClick = { editingGroupFor = null }) {
                        Text(tr("Cancel", "Отмена"))
                    }
                }
            }
        )
    }

    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text(tr("Close all tabs?", "Закрыть все вкладки?")) },
            text = {
                Text(
                    tr(
                        "All open tabs, including private tabs, will be closed.",
                        "Будут закрыты все открытые вкладки, включая приватные."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAllDialog = false
                    onCloseAll()
                }) {
                    Text(tr("Close all", "Закрыть все"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllDialog = false }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@Composable
private fun TabBottomControls(
    section: TabOverviewSection,
    regularCount: Int,
    privateCount: Int,
    groupCount: Int,
    compact: Boolean,
    narrow: Boolean,
    horizontalPadding: Dp,
    onSectionChange: (TabOverviewSection) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Row(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = if (dense) 6.dp else if (compact) 10.dp else 12.dp)
            .widthIn(max = if (dense) 680.dp else 720.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (dense) 7.dp else 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(if (dense) 54.dp else if (compact) 62.dp else 66.dp),
            shape = RoundedCornerShape(if (dense) 20.dp else 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (dense) 4.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TabSectionButton(
                    selected = section == TabOverviewSection.NORMAL,
                    label = tr("Regular", "Обычные"),
                    count = regularCount,
                    icon = Icons.Rounded.Language,
                    narrow = narrow,
                    modifier = Modifier.weight(1f),
                    onClick = { onSectionChange(TabOverviewSection.NORMAL) }
                )
                TabSectionButton(
                    selected = section == TabOverviewSection.PRIVATE,
                    label = tr("Private", "Приватные"),
                    count = privateCount,
                    icon = Icons.Rounded.VisibilityOff,
                    privateAccent = true,
                    narrow = narrow,
                    modifier = Modifier.weight(1f),
                    onClick = { onSectionChange(TabOverviewSection.PRIVATE) }
                )
                TabSectionButton(
                    selected = section == TabOverviewSection.GROUPS,
                    label = tr("Groups", "Группы"),
                    count = groupCount,
                    icon = Icons.Rounded.CreateNewFolder,
                    narrow = narrow,
                    modifier = Modifier.weight(1f),
                    onClick = { onSectionChange(TabOverviewSection.GROUPS) }
                )
            }
        }

        NewTabFab(
            isPrivate = section == TabOverviewSection.PRIVATE,
            compact = compact,
            onClick = onNewTab
        )
    }
}

@Composable
private fun TabSectionButton(
    selected: Boolean,
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    privateAccent: Boolean = false,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val darkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (privateAccent) {
        if (darkScheme) Color(0xFFC4B5FD) else Color(0xFF7048D8)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(if (dense) 17.dp else 19.dp),
        color = if (selected) accent.copy(alpha = 0.13f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.26f)) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (narrow) 4.dp else if (dense) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (narrow || dense) 17.dp else 19.dp),
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    modifier = Modifier.size(if (narrow) 17.dp else 19.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {}
            }

            if (!narrow) {
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (count > 0) {
                Surface(
                    modifier = Modifier.padding(start = if (narrow) 5.dp else 6.dp),
                    shape = CircleShape,
                    color = if (selected) accent.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabsState(
    modifier: Modifier,
    section: TabOverviewSection,
    queryActive: Boolean,
    groupsEmpty: Boolean
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (section) {
                    TabOverviewSection.PRIVATE -> Icons.Rounded.VisibilityOff
                    TabOverviewSection.GROUPS -> Icons.Rounded.CreateNewFolder
                    TabOverviewSection.NORMAL -> Icons.Rounded.Language
                },
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                when {
                    queryActive -> tr("No matching tabs", "Подходящих вкладок нет")
                    section == TabOverviewSection.PRIVATE -> tr("No private tabs", "Нет приватных вкладок")
                    section == TabOverviewSection.GROUPS -> tr("No tab groups", "Нет групп вкладок")
                    else -> tr("No regular tabs", "Нет обычных вкладок")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (section == TabOverviewSection.GROUPS && groupsEmpty && !queryActive) {
                Text(
                    tr(
                        "Hold a tab and choose Group to create one.",
                        "Зажмите вкладку и выберите «Группа», чтобы создать её."
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun NewTabFab(
    isPrivate: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val darkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val privateAccent = if (darkScheme) Color(0xFFC4B5FD) else Color(0xFF7048D8)

    Surface(
        onClick = onClick,
        modifier = modifier.size(if (dense) 50.dp else if (compact) 56.dp else 60.dp),
        shape = RoundedCornerShape(if (dense) 18.dp else if (compact) 20.dp else 22.dp),
        color = if (isPrivate) privateAccent else MaterialTheme.colorScheme.primary,
        shadowElevation = if (dense) 4.dp else if (compact) 5.dp else 7.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = if (isPrivate) {
                    tr("New private tab", "Новая приватная вкладка")
                } else {
                    tr("New tab", "Новая вкладка")
                },
                tint = if (isPrivate) {
                    Color.Black.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier.size(if (dense) 24.dp else if (compact) 28.dp else 30.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ModernTabCard(
    tab: TabOverviewItem,
    cardHeight: Dp,
    previewSize: TabPreviewSize,
    actionsVisible: Boolean,
    opening: Boolean,
    dimmedForOpening: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePinned: () -> Unit,
    onEditGroup: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.30f })
    val hapticFeedback = LocalHapticFeedback.current
    var removing by remember(tab.id) { mutableStateOf(false) }
    val darkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val privateAccent = if (darkScheme) Color(0xFFC4B5FD) else Color(0xFF7048D8)
    val privateCardColor = if (darkScheme) Color(0xFF1B1722) else Color(0xFFF9F7FD)
    val privatePreviewColor = if (darkScheme) Color(0xFF241D31) else Color(0xFFFBFAFE)
    val regularCardColor = if (darkScheme) {
        MaterialTheme.colorScheme.surface.copy(
            alpha = if (compact) 0.88f else IlyroVisualTokens.StrongSurfaceAlpha
        )
    } else {
        Color(0xFFFFFFFF)
    }
    val regularPreviewColor = when {
        darkScheme -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        tab.isHome -> Color(0xFFFCFDFE)
        else -> Color(0xFFF5F7FA)
    }

    val scale by animateFloatAsState(
        targetValue = when {
            opening -> 1.035f
            dimmedForOpening -> 0.99f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
            easing = IlyroVisualTokens.MotionEnterEasing
        ),
        label = "tab-open-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (dimmedForOpening) 0.30f else 1f,
        animationSpec = tween(
            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionMicroMs),
            easing = IlyroVisualTokens.MotionEnterEasing
        ),
        label = "tab-open-alpha"
    )

    val privateTabDescription = tr("Private tab", "Приватная вкладка")
    val pinnedDescription = tr("Pinned", "Закреплена")
    val groupDescription = tr("Group", "Группа")
    val selectedDescription = tr("Selected", "Выбрана")
    val notSelectedDescription = tr("Not selected", "Не выбрана")

    LaunchedEffect(removing) {
        if (removing) {
            delay(IlyroVisualTokens.motionDelay(IlyroVisualTokens.MotionScreenMs))
            onClose()
        }
    }

    AnimatedVisibility(
        visible = !removing,
        enter = fadeIn(
            tween(
                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                easing = IlyroVisualTokens.MotionEnterEasing
            )
        ) + expandVertically(
            tween(
                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                easing = IlyroVisualTokens.MotionEnterEasing
            )
        ),
        exit = fadeOut(
            tween(
                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                easing = IlyroVisualTokens.MotionExitEasing
            )
        ) + shrinkVertically(
            tween(
                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                easing = IlyroVisualTokens.MotionExitEasing
            )
        )
    ) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            onDismiss = { value ->
                if (value != SwipeToDismissBoxValue.Settled && !removing) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    removing = true
                }
            },
            backgroundContent = { Box(Modifier.fillMaxSize()) }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                            easing = IlyroVisualTokens.MotionEnterEasing
                        )
                    )
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        selected = tab.selected
                        contentDescription = buildString {
                            append(tab.title)
                            if (tab.host.isNotBlank()) append(". ${tab.host}")
                            if (tab.isPrivate) append(". $privateTabDescription")
                            if (tab.isPinned) append(". $pinnedDescription")
                            tab.groupName?.takeIf { it.isNotBlank() }?.let {
                                append(". $groupDescription: $it")
                            }
                        }
                        stateDescription = if (tab.selected) {
                            selectedDescription
                        } else {
                            notSelectedDescription
                        }
                    }
                    .combinedClickable(
                        onClick = { if (actionsVisible) onLongPress() else onSelect() },
                        onLongClick = onLongPress
                    ),
                shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                color = if (tab.isPrivate) privateCardColor else regularCardColor,
                border = when {
                    tab.selected && tab.isPrivate -> BorderStroke(
                        2.dp,
                        privateAccent.copy(alpha = 0.72f)
                    )
                    tab.selected -> BorderStroke(
                        if (darkScheme) 2.dp else 1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = if (darkScheme) 0.74f else 0.68f)
                    )
                    tab.isPrivate -> BorderStroke(
                        1.dp,
                        privateAccent.copy(alpha = IlyroVisualTokens.SelectedBorderAlpha)
                    )
                    else -> BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = IlyroVisualTokens.SubtleBorderAlpha
                        )
                    )
                },
                tonalElevation = 0.dp,
                shadowElevation = if (opening) 6.dp else if (tab.selected) 2.dp else 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 9.dp, end = 4.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tab.isPrivate) {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = privateAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.size(5.dp))
                                }
                                Text(
                                    tab.title,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (compact) {
                                        MaterialTheme.typography.bodyMedium
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            val suffix = tab.groupName
                                ?.takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty()
                            Text(
                                tab.host + suffix,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tab.isPrivate) {
                                    privateAccent.copy(alpha = 0.84f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Surface(
                            onClick = { removing = true },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .size(if (compact) 34.dp else 38.dp),
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = tr("Close tab", "Закрыть вкладку"),
                                    modifier = Modifier.size(if (compact) 17.dp else 18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = actionsVisible,
                        enter = fadeIn(
                            tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                                easing = IlyroVisualTokens.MotionEnterEasing
                            )
                        ) + expandVertically(
                            tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                                easing = IlyroVisualTokens.MotionEnterEasing
                            )
                        ),
                        exit = fadeOut(
                            tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionMicroMs),
                                easing = IlyroVisualTokens.MotionExitEasing
                            )
                        ) + shrinkVertically(
                            tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                                easing = IlyroVisualTokens.MotionExitEasing
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 9.dp, end = 9.dp, bottom = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            TabContextAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.PushPin,
                                label = if (tab.isPinned) {
                                    tr("Unpin", "Открепить")
                                } else {
                                    tr("Pin", "Закрепить")
                                },
                                active = tab.isPinned,
                                onClick = onTogglePinned
                            )
                            TabContextAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.CreateNewFolder,
                                label = if (tab.groupName.isNullOrBlank()) {
                                    tr("Group", "Группа")
                                } else {
                                    tr("Edit group", "Изменить")
                                },
                                active = !tab.groupName.isNullOrBlank(),
                                onClick = onEditGroup
                            )
                        }
                    }

                    if (previewSize != TabPreviewSize.NONE) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                            shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                            color = if (tab.isPrivate) privatePreviewColor else regularPreviewColor,
                            border = if (!darkScheme) {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
                                )
                            } else {
                                null
                            },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    tab.preview != null && !tab.isHome -> ComposeImage(
                                        bitmap = tab.preview.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        filterQuality = FilterQuality.Medium
                                    )

                                    tab.isHome -> Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AppBrandIcon(modifier = Modifier.size(if (compact) 44.dp else 48.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "ILYRO",
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = if (tab.isPrivate) {
                                                privateAccent
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }

                                    else -> Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            tab.host,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth(0.58f)
                                                .height(6.dp),
                                            shape = RoundedCornerShape(99.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                        ) {}
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth(0.38f)
                                                .height(6.dp),
                                            shape = RoundedCornerShape(99.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
                                        ) {}
                                    }
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
private fun TabContextAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
