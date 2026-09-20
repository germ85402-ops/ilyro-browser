package com.ilyro.browser.ui

import android.content.Intent
import android.net.Uri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Filled with the creator page URL after the Buy Me a Coffee registration is complete.
 */
private const val BUY_ME_A_COFFEE_URL = ""

private data class ClearAction(
    val title: String,
    val message: String,
    val action: () -> Unit
)

private enum class SettingsCategory {
    ACCOUNT,
    GENERAL,
    APPEARANCE,
    BROWSING,
    PRIVACY,
    DATA,
    ABOUT
}

@Composable
internal fun SettingsSheet(
    settings: BrowserSettings,
    versionName: String,
    onDismiss: () -> Unit,
    onSettingsChange: (BrowserSettings) -> Unit,
    onClearHistory: () -> Unit,
    onClearBookmarks: () -> Unit,
    onClearDownloads: () -> Unit,
    onClearSiteData: () -> Unit
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var clearAction by remember { mutableStateOf<ClearAction?>(null) }

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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
            ) {
                IlyroWallpaperBackdrop(settings)

                val wide = maxWidth >= 600.dp
                val selected = selectedName?.let { SettingsCategory.valueOf(it) }
                val category = selected ?: SettingsCategory.GENERAL
                val back: () -> Unit = {
                    if (!wide && selected != null) selectedName = null else onDismiss()
                }

                BackHandler(onBack = back)

                Column(modifier = Modifier.fillMaxSize()) {
                    ModernSettingsHeader(
                        category = if (!wide && selected != null) category else null,
                        onBack = back
                    )

                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (wide || selected == null) {
                            SettingsSidebar(
                                current = category,
                                wide = wide,
                                versionName = versionName,
                                onSelect = { selectedName = it.name },
                                modifier = if (wide) {
                                    Modifier.width(224.dp).fillMaxHeight()
                                } else {
                                    Modifier.fillMaxWidth().fillMaxHeight()
                                }
                            )
                        }

                        if (wide) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            )
                        }

                        if (wide || selected != null) {
                            val scroll = rememberScrollState()
                            LaunchedEffect(category) { scroll.scrollTo(0) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 880.dp)
                                        .fillMaxWidth()
                                        .verticalScroll(scroll)
                                        .padding(
                                            start = if (wide) 28.dp else 16.dp,
                                            end = if (wide) 28.dp else 16.dp,
                                            top = if (wide) 18.dp else 6.dp,
                                            bottom = if (wide) 28.dp else 22.dp
                                        )
                                ) {
                                    SettingsCategoryContent(
                                        category = category,
                                        settings = settings,
                                        versionName = versionName,
                                        onSettingsChange = onSettingsChange,
                                        onClearHistory = {
                                            clearAction = ClearAction(
                                                tr(settings.language, "Clear history?", "Очистить историю?"),
                                                tr(settings.language, "All saved visits will be removed.", "Вся история посещений будет удалена."),
                                                onClearHistory
                                            )
                                        },
                                        onClearBookmarks = {
                                            clearAction = ClearAction(
                                                tr(settings.language, "Clear bookmarks?", "Удалить закладки?"),
                                                tr(settings.language, "All bookmarks will be removed.", "Все закладки будут удалены."),
                                                onClearBookmarks
                                            )
                                        },
                                        onClearDownloads = {
                                            clearAction = ClearAction(
                                                tr(settings.language, "Delete downloads?", "Удалить загрузки?"),
                                                tr(settings.language, "Downloaded files and records will be removed.", "Загруженные файлы и записи о них будут удалены."),
                                                onClearDownloads
                                            )
                                        },
                                        onClearSiteData = {
                                            clearAction = ClearAction(
                                                tr(settings.language, "Clear site data?", "Очистить данные сайтов?"),
                                                tr(settings.language, "Cookies, sessions, cache and permissions will be removed.", "Cookie, сеансы, кэш и разрешения сайтов будут удалены."),
                                                onClearSiteData
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                clearAction?.let { pending ->
                    AlertDialog(
                        onDismissRequest = { clearAction = null },
                        title = { Text(pending.title) },
                        text = { Text(pending.message) },
                        confirmButton = {
                            TextButton(onClick = {
                                pending.action()
                                clearAction = null
                            }) {
                                Text(tr("Clear", "Очистить"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { clearAction = null }) {
                                Text(tr("Cancel", "Отмена"))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSettingsHeader(
    category: SettingsCategory?,
    onBack: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = if (metrics.isCompact) 6.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(metrics.headerButtonVisualSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = tr("Back", "Назад"),
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (metrics.isCompact) 10.dp else 14.dp)
        ) {
            Text(
                text = category?.title() ?: tr("Settings", "Настройки"),
                style = if (metrics.isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (category == null) {
                    tr("Make ILYRO yours", "Сделайте ILYRO своим")
                } else {
                    category.subtitle()
                },
                style = if (metrics.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSidebar(
    current: SettingsCategory,
    wide: Boolean,
    versionName: String,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val categories = listOf(
        SettingsCategory.GENERAL,
        SettingsCategory.APPEARANCE,
        SettingsCategory.BROWSING,
        SettingsCategory.PRIVACY,
        SettingsCategory.DATA,
        SettingsCategory.ACCOUNT,
        SettingsCategory.ABOUT
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 12.dp else 14.dp, vertical = if (dense) 5.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 3.dp else 5.dp)
    ) {
        categories.forEach { item ->
            val selected = wide && current == item
            Surface(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (dense) 16.dp else 18.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
                } else {
                    Color.Transparent
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (dense) 12.dp else 14.dp, vertical = if (dense) 9.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(if (dense) 32.dp else 36.dp),
                        shape = RoundedCornerShape(if (dense) 10.dp else 12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(if (dense) 18.dp else 19.dp),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (dense) 10.dp else 12.dp)
                    ) {
                        Text(
                            item.title(),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
                        )
                        if (!wide) {
                            Text(
                                item.subtitle(),
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!wide) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            "ILYRO $versionName",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        )
    }
}

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.GENERAL -> Icons.Rounded.Settings
    SettingsCategory.APPEARANCE -> Icons.Rounded.Palette
    SettingsCategory.BROWSING -> Icons.Rounded.Language
    SettingsCategory.PRIVACY -> Icons.Rounded.Lock
    SettingsCategory.DATA -> Icons.Rounded.Storage
    SettingsCategory.ACCOUNT -> Icons.Rounded.Person
    SettingsCategory.ABOUT -> Icons.Rounded.Info
}

@Composable
private fun SettingsCategory.title(): String = when (this) {
    SettingsCategory.ACCOUNT -> tr("Account", "Аккаунт")
    SettingsCategory.GENERAL -> tr("General", "Основные")
    SettingsCategory.APPEARANCE -> tr("Appearance", "Вид")
    SettingsCategory.BROWSING -> tr("Browsing", "Сайты")
    SettingsCategory.PRIVACY -> tr("Privacy", "Приватность")
    SettingsCategory.DATA -> tr("Data", "Данные")
    SettingsCategory.ABOUT -> tr("About", "О приложении")
}

@Composable
private fun SettingsCategory.subtitle(): String = when (this) {
    SettingsCategory.GENERAL -> tr("Language, search and startup", "Язык, поиск и запуск")
    SettingsCategory.APPEARANCE -> tr("Theme, websites, wallpaper and app icon", "Тема, сайты, обои и иконка")
    SettingsCategory.BROWSING -> tr("Pages and downloads", "Сайты и загрузки")
    SettingsCategory.PRIVACY -> tr("Security and private browsing", "Безопасность и приватный просмотр")
    SettingsCategory.DATA -> tr("History and stored data", "История и сохранённые данные")
    SettingsCategory.ACCOUNT -> tr("Sign-in and sync", "Вход и синхронизация")
    SettingsCategory.ABOUT -> tr("Version and browser information", "Версия и сведения о браузере")
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    settings: BrowserSettings,
    versionName: String,
    onSettingsChange: (BrowserSettings) -> Unit,
    onClearHistory: () -> Unit,
    onClearBookmarks: () -> Unit,
    onClearDownloads: () -> Unit,
    onClearSiteData: () -> Unit
) {
    SettingsIntro(
        title = when (category) {
            SettingsCategory.ACCOUNT -> tr("Account & sync", "Аккаунт и синхронизация")
            SettingsCategory.GENERAL -> tr("General", "Основные")
            SettingsCategory.APPEARANCE -> tr("Appearance", "Оформление")
            SettingsCategory.BROWSING -> tr("Browsing", "Сайты")
            SettingsCategory.PRIVACY -> tr("Privacy & security", "Приватность")
            SettingsCategory.DATA -> tr("Data", "Данные")
            SettingsCategory.ABOUT -> tr("About ILYRO", "О ILYRO")
        },
        subtitle = when (category) {
            SettingsCategory.ACCOUNT -> tr(
                "Sign in and prepare secure settings sync across your devices.",
                "Вход и подготовка безопасной синхронизации настроек между устройствами."
            )
            SettingsCategory.GENERAL -> tr(
                "Language, search engine and startup behavior.",
                "Язык, поисковик и поведение браузера при запуске."
            )
            SettingsCategory.APPEARANCE -> tr(
                "Personalize ILYRO without cluttering the interface.",
                "Настройте ILYRO под себя, не перегружая интерфейс."
            )
            SettingsCategory.BROWSING -> tr(
                "Default page behavior and downloads.",
                "Поведение страниц и загрузки."
            )
            SettingsCategory.PRIVACY -> tr(
                "Tracking protection, secure connections and private tab rules.",
                "Защита от отслеживания, безопасные соединения и приватные вкладки."
            )
            SettingsCategory.DATA -> tr(
                "History and stored browser data management.",
                "Управление историей и сохранёнными данными браузера."
            )
            SettingsCategory.ABOUT -> tr(
                "Version, browser engine and project information.",
                "Версия, движок браузера и информация о проекте."
            )
        }
    )

    when (category) {
        SettingsCategory.ACCOUNT -> AccountSettingsSection()

        SettingsCategory.GENERAL -> {
            LanguageSettingsSection(settings, onSettingsChange)

            SettingsCard(title = tr("Search engine", "Поисковик")) {
                SearchEngine.entries.forEachIndexed { index, engine ->
                    ChoiceRow(engine.displayName, settings.searchEngine == engine) {
                        onSettingsChange(settings.copy(searchEngine = engine))
                    }
                    if (index != SearchEngine.entries.lastIndex) SettingsRowDivider()
                }
            }

            SettingsCard(title = tr("Startup", "Запуск")) {
                ToggleRow(
                    tr("Restore tabs", "Восстанавливать вкладки"),
                    tr("Reopen normal tabs after starting ILYRO", "Открывать обычные вкладки после перезапуска ILYRO"),
                    settings.restoreTabs
                ) { onSettingsChange(settings.copy(restoreTabs = it)) }
            }

            DefaultBrowserSettingsCard()
        }

        SettingsCategory.APPEARANCE -> {
            AppIconSettings(settings, onSettingsChange)
            AppearancePersonalizationSettings(settings, onSettingsChange)

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCard(title = tr("Websites", "Сайты")) {
                ToggleRow(
                    tr("Dark theme for websites", "Тёмная тема сайтов"),
                    tr(
                        "Darken supported websites automatically. Powered by Dark Reader.",
                        "Автоматически затемнять поддерживаемые сайты. На базе Dark Reader."
                    ),
                    settings.darkWebsitesEnabled
                ) { enabled ->
                    onSettingsChange(settings.copy(darkWebsitesEnabled = enabled))
                }
            }
        }

        SettingsCategory.BROWSING -> {
            SettingsCard(title = tr("Page behavior", "Поведение страниц")) {
                ToggleRow(
                    tr("Desktop site mode", "Версия для компьютера"),
                    tr("Use a desktop user agent and viewport for all sites", "Использовать компьютерный режим для всех сайтов"),
                    settings.desktopMode
                ) { onSettingsChange(settings.copy(desktopMode = it)) }
                SettingsRowDivider()
                ToggleRow(
                    tr("Downloads on mobile data", "Загрузки через мобильную сеть"),
                    tr("Allow downloads on metered networks", "Разрешить загрузки через тарифицируемые сети"),
                    settings.downloadsOverMetered
                ) { onSettingsChange(settings.copy(downloadsOverMetered = it)) }
            }

        }

        SettingsCategory.PRIVACY -> {
            SettingsCard(title = tr("Security", "Безопасность")) {
                ToggleRow(
                    tr("HTTPS-only mode", "Только HTTPS"),
                    tr("Prefer secure HTTPS connections and block insecure HTTP", "Предпочитать защищённые HTTPS-соединения и блокировать HTTP"),
                    settings.httpsOnly
                ) { onSettingsChange(settings.copy(httpsOnly = it)) }
                SettingsRowDivider()
                ToggleRow(
                    tr("Isolate third-party cookies", "Изолировать сторонние cookie"),
                    tr("Partition third-party site data to reduce cross-site tracking", "Разделять сторонние данные сайтов для уменьшения отслеживания"),
                    settings.thirdPartyCookieIsolation
                ) { onSettingsChange(settings.copy(thirdPartyCookieIsolation = it)) }
                SettingsRowDivider()
                ToggleRow(
                    "Global Privacy Control",
                    tr("Tell websites not to sell or share browsing data", "Сообщать сайтам, что данные просмотра нельзя продавать или передавать"),
                    settings.globalPrivacyControl
                ) { onSettingsChange(settings.copy(globalPrivacyControl = it)) }
            }

            SettingsCard(title = tr("Private tabs", "Приватные вкладки")) {
                ToggleRow(
                    tr("Clear private tab data on close", "Очищать данные приватных вкладок при закрытии"),
                    tr("Remove isolated storage when a private tab closes", "Удалять изолированное хранилище после закрытия приватной вкладки"),
                    settings.clearPrivateDataOnExit
                ) { onSettingsChange(settings.copy(clearPrivateDataOnExit = it)) }
                SettingsRowDivider()
                InfoRow(
                    tr(
                        "Private tabs are never restored and are never added to browsing history.",
                        "Приватные вкладки не восстанавливаются и не добавляются в историю."
                    ),
                    emphasis = false
                )
            }
        }

        SettingsCategory.DATA -> {
            SettingsCard(title = tr("History", "История")) {
                ToggleRow(
                    tr("Save browsing history", "Сохранять историю"),
                    tr("Remember visited pages outside private tabs", "Запоминать посещённые страницы вне приватных вкладок"),
                    settings.historyEnabled
                ) { onSettingsChange(settings.copy(historyEnabled = it)) }
            }

            SettingsCard(title = tr("Clear browser data", "Очистка данных")) {
                ActionRow(
                    tr("Clear history", "Очистить историю"),
                    tr("Remove all saved page visits.", "Удалить всю сохранённую историю посещений."),
                    onClearHistory
                )
                SettingsRowDivider()
                ActionRow(
                    tr("Clear bookmarks", "Очистить закладки"),
                    tr("Remove all saved bookmarks.", "Удалить все сохранённые закладки."),
                    onClearBookmarks
                )
                SettingsRowDivider()
                ActionRow(
                    tr("Clear downloads", "Очистить загрузки"),
                    tr("Delete downloaded files and their records.", "Удалить загруженные файлы и записи о них."),
                    onClearDownloads
                )
                SettingsRowDivider()
                ActionRow(
                    tr("Clear site data", "Очистить данные сайтов"),
                    tr("Cookies, cache, sessions and site permissions.", "Cookie, кэш, сеансы и разрешения сайтов."),
                    onClearSiteData
                )
            }
        }

        SettingsCategory.ABOUT -> {
            val context = LocalContext.current

            SettingsCard(title = tr("Application", "Приложение")) {
                InfoRow("ILYRO")
                SettingsRowDivider()
                InfoRow(
                    tr("Version $versionName · GeckoView", "Версия $versionName · GeckoView"),
                    emphasis = false
                )
            }

            SettingsCard(title = tr("Support ILYRO", "Поддержать ILYRO")) {
                ActionRow(
                    tr("Buy Me a Coffee", "Buy Me a Coffee"),
                    if (BUY_ME_A_COFFEE_URL.isBlank()) {
                        tr(
                            "The support page will be added after registration.",
                            "Ссылка поддержки появится после регистрации."
                        )
                    } else {
                        tr(
                            "Support the browser and its future updates.",
                            "Поддержать развитие браузера и будущие обновления."
                        )
                    },
                    onClick = {
                        if (BUY_ME_A_COFFEE_URL.isNotBlank()) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(BUY_ME_A_COFFEE_URL)
                                )
                            )
                        }
                    },
                    enabled = BUY_ME_A_COFFEE_URL.isNotBlank()
                )
            }

            SettingsCard(title = tr("Developers", "Разработчики")) {
                InfoRow("Alex Agapitov")
                SettingsRowDivider()
                InfoRow("Habet Hayrapetyan", emphasis = false)
            }
        }
    }
}

@Composable
private fun SettingsIntro(title: String, subtitle: String) {
    val metrics = rememberIlyroLayoutMetrics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                end = 2.dp,
                bottom = if (metrics.isCompact) 10.dp else 16.dp
            )
    ) {
        // On phones the category title already lives in the fixed header. Repeating it below
        // created the loose/duplicated hierarchy visible in the compact layout.
        if (!metrics.isCompact) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            subtitle,
            modifier = Modifier.padding(top = if (metrics.isCompact) 0.dp else 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppIconSettings(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    SettingsCard(title = tr("App icon", "Иконка приложения")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (dense) 14.dp else 16.dp, vertical = if (dense) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 16.dp)
        ) {
            AppIcon.entries.forEach { icon ->
                val selected = settings.appIcon == icon
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = {
                            if (!selected) onSettingsChange(settings.copy(appIcon = icon))
                        },
                        modifier = Modifier.size(if (dense) 64.dp else 72.dp),
                        shape = RoundedCornerShape(if (dense) 18.dp else 20.dp),
                        color = icon.previewBackground(),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
                        ),
                        tonalElevation = 0.dp
                    ) {
                        AppBrandIcon(icon = icon, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        icon.title(),
                        modifier = Modifier.padding(top = if (dense) 5.dp else 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        Text(
            tr(
                "The home screen icon may take a few seconds to refresh.",
                "Иконка на рабочем столе может обновиться через несколько секунд."
            ),
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = if (dense) 10.dp else 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun AppIcon.previewBackground(): Color = when (this) {
    AppIcon.DEER -> Color(0xFF020B1B)
    AppIcon.CLASSIC -> Color.White
    AppIcon.MONOCHROME -> Color.Black
    AppIcon.PINK_LIGHT -> Color.White
    AppIcon.PINK_DARK -> Color.Black
}

@Composable
private fun AppIcon.title(): String = when (this) {
    AppIcon.DEER -> tr("Deer", "Олень")
    AppIcon.CLASSIC -> tr("Classic", "Классика")
    AppIcon.MONOCHROME -> tr("Mono", "Моно")
    AppIcon.PINK_LIGHT -> tr("Pink light", "Розовая светлая")
    AppIcon.PINK_DARK -> tr("Pink dark", "Розовая тёмная")
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (dense) 8.dp else 12.dp),
        shape = RoundedCornerShape(if (dense) 18.dp else 20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                modifier = Modifier.padding(start = 14.dp, top = if (dense) 10.dp else 14.dp, end = 14.dp, bottom = if (dense) 6.dp else 8.dp),
                style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = if (dense) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (dense) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.52f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.52f)
        )
    }
}

@Composable
private fun InfoRow(text: String, emphasis: Boolean = true) {
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = if (dense) 10.dp else 14.dp),
        style = if (emphasis) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
        color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    )
}
