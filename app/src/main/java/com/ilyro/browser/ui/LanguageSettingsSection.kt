package com.ilyro.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class SiteLanguageOption(
    val tag: String,
    val nativeName: String,
    val displayName: String
)

@Composable
internal fun LanguageSettingsSection(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var showBrowserLanguagePicker by remember { mutableStateOf(false) }
    val resolvedUiLanguage = resolveAppLanguage(settings.language)
    val displayLocale = remember(resolvedUiLanguage) { appLanguageLocale(settings.language) }
    val availableLanguages = remember(displayLocale) { buildSiteLanguageOptions(displayLocale) }
    val preferredLanguages = settings.preferredSiteLanguages.ifEmpty { defaultPreferredSiteLanguages() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                tr("Languages", "Языки"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            LanguageSummaryRow(
                selectedLanguage = settings.language,
                onClick = { showBrowserLanguagePicker = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                tr("Website languages", "Языки сайтов"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                tr(
                    "Sites receive these languages in this order. This does not require ILYRO itself to be translated into them.",
                    "Сайты получают эти языки в указанном порядке. Перевод интерфейса ILYRO на них не требуется."
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            preferredLanguages.forEachIndexed { index, tag ->
                PreferredLanguageRow(
                    index = index,
                    tag = tag,
                    displayLocale = displayLocale,
                    canMoveUp = index > 0,
                    canMoveDown = index < preferredLanguages.lastIndex,
                    canRemove = preferredLanguages.size > 1,
                    onMoveUp = {
                        val updated = preferredLanguages.toMutableList()
                        val value = updated.removeAt(index)
                        updated.add(index - 1, value)
                        onSettingsChange(settings.copy(preferredSiteLanguages = updated))
                    },
                    onMoveDown = {
                        val updated = preferredLanguages.toMutableList()
                        val value = updated.removeAt(index)
                        updated.add(index + 1, value)
                        onSettingsChange(settings.copy(preferredSiteLanguages = updated))
                    },
                    onRemove = {
                        if (preferredLanguages.size > 1) {
                            onSettingsChange(
                                settings.copy(
                                    preferredSiteLanguages = preferredLanguages.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    }
                                )
                            )
                        }
                    }
                )
            }

            TextButton(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                onClick = { showPicker = true }
            ) {
                Text(tr("+ Add language", "+ Добавить язык"))
            }
        }
    }

    if (showPicker) {
        SiteLanguagePickerDialog(
            options = availableLanguages,
            selectedTags = preferredLanguages,
            onSelectedTagsChange = { updated ->
                onSettingsChange(settings.copy(preferredSiteLanguages = updated))
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showBrowserLanguagePicker) {
        BrowserLanguagePickerDialog(
            selectedLanguage = settings.language,
            onSelectedLanguage = { language ->
                onSettingsChange(settings.copy(language = language))
                showBrowserLanguagePicker = false
            },
            onDismiss = { showBrowserLanguagePicker = false }
        )
    }
}

@Composable
private fun LanguageSummaryRow(
    selectedLanguage: AppLanguage,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr("Browser language", "Язык браузера"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                appLanguageLabel(selectedLanguage),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = tr("Choose language", "Выбрать язык"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LanguageChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BrowserLanguagePickerDialog(
    selectedLanguage: AppLanguage,
    onSelectedLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Browser language", "Язык браузера")) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
            ) {
                items(AppLanguage.entries, key = { it.name }) { language ->
                    LanguageChoiceRow(
                        title = appLanguageLabel(language),
                        subtitle = if (language == AppLanguage.SYSTEM) {
                            tr(
                                "Uses the supported language from Android; falls back to English.",
                                "Использует поддерживаемый язык Android; если перевода нет — English."
                            )
                        } else null,
                        selected = selectedLanguage == language,
                        onClick = { onSelectedLanguage(language) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Done", "Готово"))
            }
        }
    )
}

@Composable
private fun PreferredLanguageRow(
    index: Int,
    tag: String,
    displayLocale: Locale,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    val nativeName = remember(tag) { languageName(locale, locale) }
    val displayName = remember(tag, displayLocale) { languageName(locale, displayLocale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            modifier = Modifier.padding(end = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                nativeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (displayName.equals(nativeName, ignoreCase = true)) tag.uppercase(Locale.ROOT)
                else "$displayName · ${tag.uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageActionButton(
                    enabled = canMoveUp,
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = tr("Move up", "Выше"),
                    onClick = onMoveUp
                )
                LanguageActionButton(
                    enabled = canMoveDown,
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = tr("Move down", "Ниже"),
                    onClick = onMoveDown
                )
                LanguageActionButton(
                    enabled = canRemove,
                    icon = Icons.Rounded.Close,
                    contentDescription = tr("Remove", "Удалить"),
                    onClick = onRemove
                )
            }
        }
    }
}

@Composable
private fun LanguageActionButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(11.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(19.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                    }
                )
            }
        }
    }
}

@Composable
private fun SiteLanguagePickerDialog(
    options: List<SiteLanguageOption>,
    selectedTags: List<String>,
    onSelectedTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = remember(options, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            options
        } else {
            options.filter { option ->
                option.nativeName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    option.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    option.tag.contains(normalizedQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add website languages", "Добавить языки сайтов")) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("Search languages", "Поиск языков")) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.tag }) { option ->
                        val selected = option.tag in selectedTags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = when {
                                        selected && selectedTags.size > 1 -> selectedTags - option.tag
                                        selected -> selectedTags
                                        else -> selectedTags + option.tag
                                    }
                                    onSelectedTagsChange(updated)
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                enabled = !selected || selectedTags.size > 1,
                                onCheckedChange = {
                                    val updated = when {
                                        selected && selectedTags.size > 1 -> selectedTags - option.tag
                                        selected -> selectedTags
                                        else -> selectedTags + option.tag
                                    }
                                    onSelectedTagsChange(updated)
                                }
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(option.nativeName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (option.nativeName.equals(option.displayName, ignoreCase = true)) {
                                        option.tag.uppercase(Locale.ROOT)
                                    } else {
                                        "${option.displayName} · ${option.tag.uppercase(Locale.ROOT)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Done", "Готово"))
            }
        }
    )
}

private fun buildSiteLanguageOptions(displayLocale: Locale): List<SiteLanguageOption> =
    Locale.getAvailableLocales()
        .asSequence()
        .map { it.language.lowercase(Locale.ROOT) }
        .filter { it.matches(Regex("[a-z]{2,3}")) }
        .distinct()
        .map { tag ->
            val locale = Locale.forLanguageTag(tag)
            SiteLanguageOption(
                tag = tag,
                nativeName = languageName(locale, locale),
                displayName = languageName(locale, displayLocale)
            )
        }
        .filter { it.nativeName.isNotBlank() && it.displayName.isNotBlank() }
        .sortedBy { it.displayName.lowercase(displayLocale) }
        .toList()

private fun languageName(locale: Locale, displayLocale: Locale): String {
    val raw = locale.getDisplayLanguage(displayLocale).trim()
    if (raw.isBlank()) return locale.language.uppercase(Locale.ROOT)
    return raw.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
    }
}
