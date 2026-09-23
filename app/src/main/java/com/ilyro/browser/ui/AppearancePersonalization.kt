package com.ilyro.browser.ui

import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun BrowserAccent.resolveAccent(darkTheme: Boolean): Color = when (this) {
    BrowserAccent.ILYRO -> if (darkTheme) Color(0xFF6687FF) else Color(0xFF246BFD)
    BrowserAccent.BLUE -> if (darkTheme) Color(0xFF6EA8FF) else Color(0xFF246BFD)
    BrowserAccent.VIOLET -> if (darkTheme) Color(0xFFB79CFF) else Color(0xFF7657E8)
    BrowserAccent.FOREST -> if (darkTheme) Color(0xFF74D7A6) else Color(0xFF23845B)
}

internal fun BrowserAccent.resolveOnAccent(darkTheme: Boolean): Color =
    readableForeground(resolveAccent(darkTheme))

internal fun privateModeAccent(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFFC4B5FD) else Color(0xFF7048D8)

internal fun privateModeOnAccent(darkTheme: Boolean): Color =
    readableForeground(privateModeAccent(darkTheme))

@Composable
internal fun privateModeAccent(): Color {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return privateModeAccent(darkTheme)
}

private fun readableForeground(background: Color): Color {
    val darkInk = Color(0xFF10151E)
    val white = Color.White
    return if (background.contrastRatio(darkInk) >= background.contrastRatio(white)) darkInk else white
}

private fun Color.contrastRatio(other: Color): Float {
    val first = luminance()
    val second = other.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}

@Composable
internal fun AppearancePersonalizationSettings(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    val context = LocalContext.current
    var pickingDarkWallpaper by remember { mutableStateOf(false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (pickingDarkWallpaper) {
                onSettingsChange(
                    settings.copy(
                        useSeparateDarkBackground = true,
                        darkHomeBackground = HomeBackground.CUSTOM,
                        darkCustomWallpaperUri = uri.toString()
                    )
                )
            } else {
                onSettingsChange(
                    settings.copy(
                        homeBackground = HomeBackground.CUSTOM,
                        customWallpaperUri = uri.toString()
                    )
                )
            }
        }
    }

    fun pickCustomWallpaper(forDarkTheme: Boolean) {
        pickingDarkWallpaper = forDarkTheme
        wallpaperPicker.launch(arrayOf("image/*"))
    }

    AppearancePreview(settings)

    PersonalizationSection(
        title = tr("Colors", "Цвета"),
        subtitle = tr(
            "Choose the theme. ILYRO automatically matches the interface accent to your wallpaper.",
            "Выберите тему. ILYRO автоматически подбирает акцент интерфейса под обои."
        )
    ) {
        SegmentedChoices(
            options = BrowserTheme.entries,
            selected = settings.theme,
            label = { themeLabelLocal(it) },
            onSelect = { onSettingsChange(settings.copy(theme = it)) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dark = MaterialTheme.colorScheme.background.luminanceSimple() < 0.5f
            val autoAccent = rememberWallpaperAccent(settings, dark)
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = autoAccent.resolveAccent(dark)
            ) {}
            Text(
                tr(
                    "Accent follows the current wallpaper automatically",
                    "Акцент автоматически следует за текущими обоями"
                ),
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    PersonalizationSection(
        title = tr("Browser toolbar", "Панель браузера"),
        subtitle = tr("Position and the buttons you want to keep visible.", "Положение и кнопки, которые должны быть всегда под рукой.")
    ) {
        SegmentedChoices(
            options = ToolbarPosition.entries,
            selected = settings.toolbarPosition,
            label = { if (it == ToolbarPosition.BOTTOM) tr("Bottom", "Снизу") else tr("Top", "Сверху") },
            onSelect = { onSettingsChange(settings.copy(toolbarPosition = it)) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        ToolbarAction.entries.filterNot { it == ToolbarAction.SHIELD }.forEach { action ->
            CompactToggleRow(
                title = toolbarActionLabel(action),
                subtitle = toolbarActionHint(action),
                checked = action in settings.toolbarActions
            ) { checked ->
                val updated = if (checked) settings.toolbarActions + action else settings.toolbarActions - action
                onSettingsChange(settings.copy(toolbarActions = updated))
            }
        }
        Text(
            tr(
                "Menu is always available. Download notifications open Downloads directly.",
                "Меню доступно всегда. Уведомление о загрузке сразу открывает экран загрузок."
            ),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    PersonalizationSection(
        title = tr("Interface density", "Плотность интерфейса"),
        subtitle = tr(
            "Compact keeps more browser controls visible without shrinking touch targets.",
            "Компактный режим показывает больше элементов, не уменьшая удобную область нажатия."
        )
    ) {
        SegmentedChoices(
            options = UiDensity.entries,
            selected = settings.uiDensity,
            label = { densityLabel(it) },
            onSelect = { onSettingsChange(settings.copy(uiDensity = it)) }
        )
    }

    PersonalizationSection(
        title = tr("New tab", "Новая вкладка"),
        subtitle = tr("Keep the start page clean or make shortcuts more prominent.", "Сделайте стартовую страницу компактной или выделите быстрые сайты.")
    ) {
        CompactToggleRow(
            title = tr("Quick access", "Быстрый доступ"),
            subtitle = tr("Show shortcut tiles on the start page", "Показывать ярлыки сайтов на стартовой странице"),
            checked = settings.showQuickAccess
        ) { onSettingsChange(settings.copy(showQuickAccess = it)) }

        if (settings.showQuickAccess) {
            CompactToggleRow(
                title = tr("Shortcut labels", "Подписи ярлыков"),
                subtitle = tr("Show site names below icons", "Показывать названия сайтов под иконками"),
                checked = settings.showShortcutLabels
            ) { onSettingsChange(settings.copy(showShortcutLabels = it)) }

            Spacer(modifier = Modifier.height(6.dp))
            Text(tr("Shortcut size", "Размер ярлыков"), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(7.dp))
            SegmentedChoices(
                options = HomeShortcutSize.entries,
                selected = settings.shortcutSize,
                label = { shortcutSizeLabel(it) },
                onSelect = { onSettingsChange(settings.copy(shortcutSize = it)) }
            )
        }
    }

    PersonalizationSection(
        title = tr("Start page background", "Фон стартовой страницы"),
        subtitle = tr(
            "Choose an ILYRO wallpaper or use your own image. Effects are applied only to the start page.",
            "Выберите обои ILYRO или своё изображение. Эффекты применяются только к стартовой странице."
        )
    ) {
        Text(tr("Main background", "Основной фон"), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        WallpaperPresetGrid(
            selected = settings.homeBackground,
            customUri = settings.customWallpaperUri,
            onSelect = { background ->
                if (background == HomeBackground.CUSTOM) {
                    if (settings.customWallpaperUri.isNullOrBlank()) {
                        pickCustomWallpaper(false)
                    } else {
                        onSettingsChange(settings.copy(homeBackground = HomeBackground.CUSTOM))
                    }
                } else {
                    onSettingsChange(settings.copy(homeBackground = background))
                }
            }
        )
        if (!settings.customWallpaperUri.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { pickCustomWallpaper(false) }) {
                    Text(tr("Change image", "Сменить изображение"))
                }
                TextButton(onClick = {
                    onSettingsChange(
                        settings.copy(
                            customWallpaperUri = null,
                            homeBackground = if (settings.homeBackground == HomeBackground.CUSTOM) {
                                HomeBackground.CLEAN
                            } else {
                                settings.homeBackground
                            }
                        )
                    )
                }) {
                    Text(tr("Remove", "Удалить"))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        CompactToggleRow(
            title = tr("Separate dark-theme background", "Отдельный фон для тёмной темы"),
            subtitle = tr(
                "Use another wallpaper whenever ILYRO is in dark mode",
                "Использовать другие обои, когда ILYRO работает в тёмной теме"
            ),
            checked = settings.useSeparateDarkBackground
        ) { onSettingsChange(settings.copy(useSeparateDarkBackground = it)) }

        if (settings.useSeparateDarkBackground) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(tr("Dark-theme background", "Фон тёмной темы"), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            WallpaperPresetGrid(
                selected = settings.darkHomeBackground,
                customUri = settings.darkCustomWallpaperUri,
                onSelect = { background ->
                    if (background == HomeBackground.CUSTOM) {
                        if (settings.darkCustomWallpaperUri.isNullOrBlank()) {
                            pickCustomWallpaper(true)
                        } else {
                            onSettingsChange(settings.copy(darkHomeBackground = HomeBackground.CUSTOM))
                        }
                    } else {
                        onSettingsChange(settings.copy(darkHomeBackground = background))
                    }
                }
            )
            if (!settings.darkCustomWallpaperUri.isNullOrBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pickCustomWallpaper(true) }) {
                        Text(tr("Change image", "Сменить изображение"))
                    }
                    TextButton(onClick = {
                        onSettingsChange(
                            settings.copy(
                                darkCustomWallpaperUri = null,
                                darkHomeBackground = if (settings.darkHomeBackground == HomeBackground.CUSTOM) {
                                    HomeBackground.NIGHT_WAVES
                                } else {
                                    settings.darkHomeBackground
                                }
                            )
                        )
                    }) {
                        Text(tr("Remove", "Удалить"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(tr("Image fit", "Масштаб изображения"), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(7.dp))
        SegmentedChoices(
            options = WallpaperFit.entries,
            selected = settings.wallpaperFit,
            label = { wallpaperFitLabel(it) },
            onSelect = { onSettingsChange(settings.copy(wallpaperFit = it)) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(tr("Dimming", "Затемнение"), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(7.dp))
        SegmentedChoices(
            options = WallpaperDim.entries,
            selected = settings.wallpaperDim,
            label = { wallpaperDimLabel(it) },
            onSelect = { onSettingsChange(settings.copy(wallpaperDim = it)) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(tr("Blur", "Размытие"), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(7.dp))
        SegmentedChoices(
            options = WallpaperBlur.entries,
            selected = settings.wallpaperBlur,
            label = { wallpaperBlurLabel(it) },
            onSelect = { onSettingsChange(settings.copy(wallpaperBlur = it)) }
        )
    }

}

@Composable
private fun AppearancePreview(settings: BrowserSettings) {
    val dark = MaterialTheme.colorScheme.background.luminanceSimple() < 0.5f
    val accent = rememberWallpaperAccent(settings, dark).resolveAccent(dark)
    val previewHeight = 72.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Live preview", "Предпросмотр"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Changes are applied immediately", "Изменения применяются сразу"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(modifier = Modifier.size(18.dp), shape = CircleShape, color = accent) {}
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (settings.toolbarPosition == ToolbarPosition.TOP) {
                PreviewToolbar(settings, accent)
                Spacer(modifier = Modifier.height(10.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(previewHeight),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ILYRO", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f), fontWeight = FontWeight.Bold)
                }
            }
            if (settings.toolbarPosition == ToolbarPosition.BOTTOM) {
                Spacer(modifier = Modifier.height(10.dp))
                PreviewToolbar(settings, accent)
            }
        }
    }
}

@Composable
private fun PreviewToolbar(settings: BrowserSettings, accent: Color) {
    val miniSize = when (settings.uiDensity) {
        UiDensity.COMPACT -> 28.dp
        UiDensity.STANDARD -> 30.dp
        UiDensity.COMFORTABLE -> 34.dp
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(if (settings.uiDensity == UiDensity.COMPACT) 6.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (settings.uiDensity == UiDensity.COMPACT) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ToolbarAction.BACK in settings.toolbarActions) PreviewMiniButton("‹", miniSize)
            if (ToolbarAction.FORWARD in settings.toolbarActions) PreviewMiniButton("›", miniSize)
            Surface(
                modifier = Modifier.weight(1f).height(if (settings.uiDensity == UiDensity.COMPACT) 34.dp else miniSize + 4.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {}
            if (ToolbarAction.TABS in settings.toolbarActions) PreviewMiniButton("3", miniSize)
            Surface(modifier = Modifier.size(miniSize), shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewMiniButton(text: String, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PersonalizationSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    var expanded by rememberSaveable { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(if (dense) 8.dp else 12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (dense) 16.dp else 18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(if (dense) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) tr("Collapse", "Свернуть") else tr("Expand", "Развернуть"),
                    modifier = Modifier.padding(start = if (dense) 8.dp else 12.dp))
            }
            if (expanded) Column(Modifier.padding(start = if (dense) 12.dp else 16.dp, end = if (dense) 12.dp else 16.dp, bottom = if (dense) 12.dp else 16.dp), content = content)
        }
    }
}

@Composable
private fun <T> SegmentedChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 7.dp)) {
        options.forEach { option ->
            val active = option == selected
            Surface(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(if (dense) 11.dp else 13.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)) else null
            ) {
                Box(modifier = Modifier.padding(horizontal = 5.dp, vertical = if (dense) 7.dp else 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun WallpaperPresetGrid(
    selected: HomeBackground,
    customUri: String?,
    onSelect: (HomeBackground) -> Unit
) {
    HomeBackground.entries.chunked(2).forEachIndexed { rowIndex, rowItems ->
        if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { background ->
                val active = selected == background
                Surface(
                    onClick = { onSelect(background) },
                    modifier = Modifier
                        .weight(1f)
                        .height(if (LocalIlyroUiDensity.current == UiDensity.COMPACT) 64.dp else 74.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        if (active) 2.dp else 1.dp,
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (background == HomeBackground.CUSTOM && !customUri.isNullOrBlank()) {
                            CustomWallpaperThumbnail(
                                uriString = customUri,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.28f)
                                            )
                                        )
                                    )
                            )
                        } else if (background == HomeBackground.CUSTOM) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(wallpaperPreviewBrush(HomeBackground.CUSTOM))
                            )
                        } else {
                            BuiltInWallpaperPreview(
                                background = background,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.34f)
                                            )
                                        )
                                    )
                            )
                        }

                        Text(
                            homeBackgroundLabel(background),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(if (LocalIlyroUiDensity.current == UiDensity.COMPACT) 8.dp else 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = wallpaperPreviewTextColor(background)
                        )
                    }
                }
            }
            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun wallpaperPreviewBrush(background: HomeBackground): Brush = when (background) {
    HomeBackground.NONE -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    HomeBackground.CLEAN -> Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    )
    HomeBackground.SOFT_GRADIENT -> Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f), MaterialTheme.colorScheme.background)
    )
    HomeBackground.MOUNTAIN_DUSK -> Brush.linearGradient(
        listOf(Color(0xFF274060), Color(0xFF6D597A), Color(0xFFE5989B))
    )
    HomeBackground.BLUE_HORIZON -> Brush.linearGradient(
        listOf(Color(0xFF0B3D91), Color(0xFF3D8BFF), Color(0xFFB8E1FF))
    )
    HomeBackground.NIGHT_WAVES -> Brush.linearGradient(
        listOf(Color(0xFF090A1A), Color(0xFF243B6B))
    )
    HomeBackground.AURORA -> Brush.linearGradient(
        listOf(Color(0xFF071A24), Color(0xFF31D6A4), Color(0xFF5A54D6))
    )
    HomeBackground.CUSTOM -> Brush.linearGradient(
        listOf(Color(0xFF42464E), Color(0xFF727984), Color(0xFF343941))
    )
}

@Composable
private fun wallpaperPreviewTextColor(background: HomeBackground): Color = Color.White

@Composable
private fun AccentChoices(selected: BrowserAccent, onSelect: (BrowserAccent) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminanceSimple() < 0.5f
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrowserAccent.entries.forEach { accent ->
            val active = selected == accent
            Surface(
                onClick = { onSelect(accent) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (active) 0.58f else 0.34f),
                border = if (active) BorderStroke(1.dp, accent.resolveAccent(dark).copy(alpha = 0.70f)) else null
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(modifier = Modifier.size(19.dp), shape = CircleShape, color = accent.resolveAccent(dark)) {}
                    Text(accentLabel(accent), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CompactToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun themeLabelLocal(theme: BrowserTheme): String = when (theme) {
    BrowserTheme.SYSTEM -> tr("System", "Система")
    BrowserTheme.LIGHT -> tr("Light", "Светлая")
    BrowserTheme.DARK -> tr("Dark", "Тёмная")
}

@Composable
private fun accentLabel(accent: BrowserAccent): String = when (accent) {
    BrowserAccent.ILYRO -> "ILYRO"
    BrowserAccent.BLUE -> tr("Blue", "Синий")
    BrowserAccent.VIOLET -> tr("Violet", "Фиолетовый")
    BrowserAccent.FOREST -> tr("Forest", "Лесной")
}

@Composable
private fun shortcutSizeLabel(size: HomeShortcutSize): String = when (size) {
    HomeShortcutSize.SMALL -> tr("Small", "Малые")
    HomeShortcutSize.STANDARD -> tr("Normal", "Обычные")
    HomeShortcutSize.LARGE -> tr("Large", "Крупные")
}

@Composable
internal fun homeBackgroundLabel(background: HomeBackground): String = when (background) {
    HomeBackground.NONE -> tr("No wallpaper", "Без обоев")
    HomeBackground.CLEAN -> tr("Space nebula", "Космическая туманность")
    HomeBackground.SOFT_GRADIENT -> tr("Mountain sunrise", "Рассвет в горах")
    HomeBackground.MOUNTAIN_DUSK -> tr("Aurora lake", "Озеро и сияние")
    HomeBackground.BLUE_HORIZON -> tr("Forest waterfall", "Лесной водопад")
    HomeBackground.NIGHT_WAVES -> tr("Moonlit valley", "Лунная долина")
    HomeBackground.AURORA -> tr("Desert sunset", "Закат в пустыне")
    HomeBackground.CUSTOM -> tr("Your image", "Своё фото")
}

@Composable
private fun wallpaperFitLabel(fit: WallpaperFit): String = when (fit) {
    WallpaperFit.FILL -> tr("Fill", "Заполнить")
    WallpaperFit.FIT -> tr("Fit", "Вместить")
    WallpaperFit.CENTER -> tr("Center", "По центру")
}

@Composable
private fun wallpaperDimLabel(dim: WallpaperDim): String = when (dim) {
    WallpaperDim.OFF -> "0%"
    WallpaperDim.LIGHT -> "20%"
    WallpaperDim.MEDIUM -> "35%"
    WallpaperDim.STRONG -> "50%"
}

@Composable
private fun wallpaperBlurLabel(blur: WallpaperBlur): String = when (blur) {
    WallpaperBlur.OFF -> tr("Off", "Выкл")
    WallpaperBlur.SOFT -> tr("Soft", "Слабое")
    WallpaperBlur.MEDIUM -> tr("Medium", "Среднее")
}

@Composable
private fun tabLayoutLabel(mode: TabLayoutMode): String = when (mode) {
    TabLayoutMode.GRID -> tr("Grid", "Сетка")
    TabLayoutMode.LIST -> tr("List", "Список")
    TabLayoutMode.COMPACT -> tr("Compact", "Компактно")
}

@Composable
private fun tabPreviewLabel(size: TabPreviewSize): String = when (size) {
    TabPreviewSize.LARGE -> tr("Large", "Большой")
    TabPreviewSize.MEDIUM -> tr("Medium", "Средний")
    TabPreviewSize.NONE -> tr("None", "Нет")
}

@Composable
private fun densityLabel(density: UiDensity): String = when (density) {
    UiDensity.COMPACT -> tr("Compact", "Компактно")
    UiDensity.STANDARD -> tr("Default", "Обычно")
    UiDensity.COMFORTABLE -> tr("Comfort", "Свободно")
}

@Composable
private fun toolbarActionLabel(action: ToolbarAction): String = when (action) {
    ToolbarAction.BACK -> tr("Back", "Назад")
    ToolbarAction.FORWARD -> tr("Forward", "Вперёд")
    ToolbarAction.SHIELD -> tr("Shield", "Защита")
    ToolbarAction.TABS -> tr("Tabs", "Вкладки")
}

@Composable
private fun toolbarActionHint(action: ToolbarAction): String = when (action) {
    ToolbarAction.BACK -> tr("Previous page", "Предыдущая страница")
    ToolbarAction.FORWARD -> tr("Next page", "Следующая страница")
    ToolbarAction.SHIELD -> tr("Protection controls", "Управление защитой сайта")
    ToolbarAction.TABS -> tr("Tab switcher", "Переключатель вкладок")
}

private fun Color.luminanceSimple(): Float = red * 0.2126f + green * 0.7152f + blue * 0.0722f
