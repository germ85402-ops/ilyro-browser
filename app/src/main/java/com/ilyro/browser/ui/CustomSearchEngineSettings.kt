package com.ilyro.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import java.util.UUID

@Composable
internal fun SearchEngineSettingsSection(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    var editorVisible by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftTemplate by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun closeEditor() {
        editorVisible = false
        editingId = null
        validationError = null
    }

    fun openEditor(engine: CustomSearchEngine?) {
        editingId = engine?.id
        draftName = engine?.displayName.orEmpty()
        draftTemplate = engine?.queryUrlTemplate.orEmpty()
        validationError = null
        editorVisible = true
    }

    fun saveEditor() {
        if (editingId == null && settings.customSearchEngines.size >= MAX_CUSTOM_SEARCH_ENGINES) {
            validationError = tr(
                settings.language,
                "You can add up to $MAX_CUSTOM_SEARCH_ENGINES custom search engines.",
                "Можно добавить не более $MAX_CUSTOM_SEARCH_ENGINES пользовательских поисковиков."
            )
            return
        }

        val candidate = CustomSearchEngine(
            id = editingId ?: UUID.randomUUID().toString(),
            displayName = draftName,
            queryUrlTemplate = draftTemplate
        )
        val normalized = normalizeCustomSearchEngine(candidate)
        if (normalized == null) {
            validationError = tr(
                settings.language,
                "Enter a name and an http(s) URL containing %s.",
                "Введите название и URL http(s) с обозначением %s."
            )
            return
        }

        val updated = if (editingId == null) {
            settings.customSearchEngines + normalized
        } else {
            settings.customSearchEngines.map { engine ->
                if (engine.id == editingId) normalized else engine
            }
        }
        onSettingsChange(
            settings.copy(
                customSearchEngines = updated,
                customSearchEngineId = if (editingId == null) normalized.id else settings.customSearchEngineId
            )
        )
        closeEditor()
    }

    SettingsCard(title = tr("Search engine", "Поисковик")) {
        SearchEngine.entries.forEachIndexed { index, engine ->
            ChoiceRow(
                title = engine.displayName,
                selected = settings.customSearchEngineId == null && settings.searchEngine == engine,
                onClick = {
                    onSettingsChange(
                        settings.copy(
                            searchEngine = engine,
                            customSearchEngineId = null
                        )
                    )
                }
            )
            if (index != SearchEngine.entries.lastIndex || settings.customSearchEngines.isNotEmpty()) {
                SettingsRowDivider()
            }
        }

        settings.customSearchEngines.forEachIndexed { index, engine ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.customSearchEngineId == engine.id,
                    onClick = {
                        onSettingsChange(settings.copy(customSearchEngineId = engine.id))
                    }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        engine.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        engine.queryUrlTemplate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { openEditor(engine) }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = tr("Edit", "Изменить")
                    )
                }
                IconButton(
                    onClick = {
                        val nextId = settings.customSearchEngineId
                            ?.takeIf { it != engine.id }
                        onSettingsChange(
                            settings.copy(
                                customSearchEngines = settings.customSearchEngines
                                    .filterNot { it.id == engine.id },
                                customSearchEngineId = nextId
                            )
                        )
                    }
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = tr("Delete", "Удалить")
                    )
                }
            }
            if (index != settings.customSearchEngines.lastIndex) {
                SettingsRowDivider()
            }
        }

        if (settings.customSearchEngines.isEmpty()) {
            Text(
                tr(
                    "Add a search engine with an URL template. Use %s for the query.",
                    "Добавьте поисковик с URL-шаблоном. Используйте %s для запроса."
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = { openEditor(null) },
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(tr("Add search engine", "Добавить поисковик"))
        }

        SettingsRowDivider()
        ToggleRow(
            title = tr(settings.language, "Search suggestions", "Подсказки поисковика"),
            subtitle = tr(
                settings.language,
                "Send text to the selected built-in search provider while typing. Private tabs never send it.",
                "Текст отправляется выбранному встроенному поисковику при вводе. В приватных вкладках не отправляется."
            ),
            checked = settings.onlineSearchSuggestionsEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(onlineSearchSuggestionsEnabled = enabled))
            }
        )
    }

    if (editorVisible) {
        AlertDialog(
            onDismissRequest = ::closeEditor,
            title = {
                Text(
                    if (editingId == null) {
                        tr("Add search engine", "Добавить поисковик")
                    } else {
                        tr("Edit search engine", "Изменить поисковик")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(tr("Name", "Название")) }
                    )
                    OutlinedTextField(
                        value = draftTemplate,
                        onValueChange = { draftTemplate = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("URL") },
                        placeholder = { Text("https://example.com/search?q=%s") }
                    )
                    Text(
                        tr(
                            "Use %s where the search query should be inserted.",
                            "Укажите %s там, где должен подставляться поисковый запрос."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    validationError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = ::saveEditor) {
                    Text(tr("Save", "Сохранить"))
                }
            },
            dismissButton = {
                TextButton(onClick = ::closeEditor) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}
