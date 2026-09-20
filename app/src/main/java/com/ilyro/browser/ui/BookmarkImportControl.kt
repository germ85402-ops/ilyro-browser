package com.ilyro.browser.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyro.browser.passwords.PasswordCsvExporter
import com.ilyro.browser.passwords.PasswordCsvImportPreview
import com.ilyro.browser.passwords.PasswordCsvImporter
import com.ilyro.browser.passwords.PasswordManagerService

private const val PASSWORD_EXPORT_AUTH_WINDOW_MS = 2 * 60 * 1000L

@Composable
internal fun BookmarkImportControl(
    onStatusMessage: (String?) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("ilyro_browser", Context.MODE_PRIVATE)
    }
    val vault = remember(context) { PasswordManagerService.vault(context) }
    var bookmarkPreview by remember { mutableStateOf<BookmarkHtmlImportPreview?>(null) }
    var passwordPreview by remember { mutableStateOf<PasswordCsvImportPreview?>(null) }
    var showExportWarning by remember { mutableStateOf(false) }
    var exportAuthorizedUntil by remember { mutableStateOf(0L) }

    val readFailedMessage = tr(
        "Could not read this bookmark HTML file.",
        "Не удалось прочитать HTML-файл закладок."
    )
    val noBookmarksMessage = tr(
        "No supported browser bookmarks were found in this file.",
        "В этом файле не найдено поддерживаемых закладок браузера."
    )
    val importFailedMessage = tr(
        "Could not import bookmarks.",
        "Не удалось импортировать закладки."
    )
    val importedBookmarksPrefix = tr(
        "Imported new bookmarks:",
        "Импортировано новых закладок:"
    )
    val noBookmarksToExportMessage = tr(
        "There are no bookmarks to export.",
        "Нет закладок для экспорта."
    )
    val bookmarkExportFailedMessage = tr(
        "Could not export bookmarks.",
        "Не удалось экспортировать закладки."
    )
    val bookmarkExportSuccessMessage = tr(
        "Bookmarks exported to HTML.",
        "Закладки экспортированы в HTML."
    )
    val passwordReadFailedMessage = tr(
        "Could not read this CSV file.",
        "Не удалось прочитать этот CSV-файл."
    )
    val passwordParseFailedMessage = tr(
        "Could not parse this password export.",
        "Не удалось разобрать экспорт паролей."
    )
    val passwordImportFailedMessage = tr(
        "Could not import passwords.",
        "Не удалось импортировать пароли."
    )
    val passwordImportSuccessPrefix = tr(
        "Imported passwords:",
        "Импортировано паролей:"
    )
    val noPasswordsMessage = tr(
        "There are no saved passwords to export.",
        "Нет сохранённых паролей для экспорта."
    )
    val deviceLockRequiredMessage = tr(
        "Set a device screen lock before exporting passwords.",
        "Настройте блокировку экрана устройства перед экспортом паролей."
    )
    val exportAuthUnavailableMessage = tr(
        "Device authentication is unavailable right now.",
        "Подтверждение устройства сейчас недоступно."
    )
    val exportAuthCancelledMessage = tr(
        "Password export was cancelled.",
        "Экспорт паролей отменён."
    )
    val exportAuthExpiredMessage = tr(
        "Export authorization expired. Try again.",
        "Срок подтверждения экспорта истёк. Повторите попытку."
    )
    val exportFailedMessage = tr(
        "Could not export passwords.",
        "Не удалось экспортировать пароли."
    )
    val exportSuccessMessage = tr(
        "Passwords exported. Keep the CSV file private.",
        "Пароли экспортированы. Храните CSV-файл в безопасном месте."
    )
    val exportAuthTitle = tr("Export saved passwords", "Экспорт сохранённых паролей")
    val exportAuthDescription = tr(
        "Confirm your device screen lock before creating a plaintext password file.",
        "Подтвердите блокировку устройства перед созданием файла с паролями в открытом виде."
    )

    val bookmarkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText()
            } ?: error("Unable to open bookmark HTML")
        }.getOrElse {
            onStatusMessage(readFailedMessage)
            return@rememberLauncherForActivityResult
        }

        val parsed = runCatching { BookmarkHtmlImporter.preview(raw) }.getOrElse {
            onStatusMessage(noBookmarksMessage)
            return@rememberLauncherForActivityResult
        }
        if (!parsed.canImport) {
            onStatusMessage(noBookmarksMessage)
            return@rememberLauncherForActivityResult
        }
        onStatusMessage(null)
        bookmarkPreview = parsed
    }

    val passwordImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText()
            } ?: error("Unable to open password CSV")
        }.getOrElse {
            onStatusMessage(passwordReadFailedMessage)
            return@rememberLauncherForActivityResult
        }

        val parsed = runCatching { PasswordCsvImporter.preview(raw) }.getOrElse {
            onStatusMessage(passwordParseFailedMessage)
            return@rememberLauncherForActivityResult
        }
        if (!parsed.canImport) {
            onStatusMessage(parsed.error ?: passwordParseFailedMessage)
            return@rememberLauncherForActivityResult
        }
        onStatusMessage(null)
        passwordPreview = parsed
    }

    val bookmarkExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bookmarks = BookmarkStore.restore(prefs)
            if (bookmarks.isEmpty()) error("No bookmarks")
            val html = BookmarkHtmlExporter.encode(bookmarks)
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(html) }
                ?: error("Unable to create bookmark export")
        }.onSuccess {
            onStatusMessage(bookmarkExportSuccessMessage)
        }.onFailure {
            onStatusMessage(bookmarkExportFailedMessage)
        }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) {
            exportAuthorizedUntil = 0L
            return@rememberLauncherForActivityResult
        }
        val authorized = SystemClock.elapsedRealtime() <= exportAuthorizedUntil
        exportAuthorizedUntil = 0L
        if (!authorized) {
            onStatusMessage(exportAuthExpiredMessage)
            return@rememberLauncherForActivityResult
        }

        runCatching {
            val csv = PasswordCsvExporter.encode(vault.snapshot())
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(csv) }
                ?: error("Unable to create password export")
        }.onSuccess {
            onStatusMessage(exportSuccessMessage)
        }.onFailure {
            onStatusMessage(exportFailedMessage)
        }
    }

    val exportAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            exportAuthorizedUntil = SystemClock.elapsedRealtime() + PASSWORD_EXPORT_AUTH_WINDOW_MS
            exportDocumentLauncher.launch("ilyro-passwords.csv")
        } else {
            exportAuthorizedUntil = 0L
            onStatusMessage(exportAuthCancelledMessage)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            tr("Data transfer", "Перенос данных"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            tr(
                "Move passwords and bookmarks between ILYRO and other browsers.",
                "Переносите пароли и закладки между ILYRO и другими браузерами."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TransferCard(
            icon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(21.dp)) },
            title = tr("Passwords", "Пароли"),
            subtitle = tr(
                "Import Chromium/Firefox CSV or export an authenticated copy.",
                "Импорт CSV из Chromium/Firefox или защищённый экспорт."
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onStatusMessage(null)
                        passwordImportLauncher.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "text/plain")
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Import", "Импорт"))
                }
                OutlinedButton(
                    onClick = {
                        val hasPasswords = runCatching { vault.snapshot().isNotEmpty() }.getOrDefault(false)
                        if (!hasPasswords) {
                            onStatusMessage(noPasswordsMessage)
                        } else {
                            onStatusMessage(null)
                            showExportWarning = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Export", "Экспорт"))
                }
            }
        }

        TransferCard(
            icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(21.dp)) },
            title = tr("Bookmarks", "Закладки"),
            subtitle = tr(
                "Standard HTML works with Chrome, Firefox, Edge and Brave.",
                "Стандартный HTML совместим с Chrome, Firefox, Edge и Brave."
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onStatusMessage(null)
                        bookmarkLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Import", "Импорт"))
                }
                OutlinedButton(
                    onClick = {
                        val bookmarks = BookmarkStore.restore(prefs)
                        if (bookmarks.isEmpty()) {
                            onStatusMessage(noBookmarksToExportMessage)
                        } else {
                            onStatusMessage(null)
                            bookmarkExportLauncher.launch("ilyro-bookmarks.html")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Export", "Экспорт"))
                }
            }
        }
    }

    passwordPreview?.let { importPreview ->
        AlertDialog(
            onDismissRequest = { passwordPreview = null },
            title = { Text(tr("Import passwords?", "Импортировать пароли?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Ready to import: ${importPreview.credentials.size}",
                            "Готово к импорту: ${importPreview.credentials.size}"
                        )
                    )
                    if (importPreview.skippedRows > 0) {
                        Text(
                            tr(
                                "Skipped invalid rows: ${importPreview.skippedRows}",
                                "Пропущено некорректных строк: ${importPreview.skippedRows}"
                            )
                        )
                    }
                    if (importPreview.duplicateRows > 0) {
                        Text(
                            tr(
                                "Duplicates merged: ${importPreview.duplicateRows}",
                                "Объединено дублей: ${importPreview.duplicateRows}"
                            )
                        )
                    }
                    Text(
                        tr(
                            "The CSV is read in memory and is not copied into ILYRO storage.",
                            "CSV читается в памяти и не копируется в хранилище ILYRO."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        importPreview.credentials.forEach(vault::upsert)
                    }.onSuccess {
                        onStatusMessage("$passwordImportSuccessPrefix ${importPreview.credentials.size}")
                    }.onFailure {
                        onStatusMessage(passwordImportFailedMessage)
                    }
                    passwordPreview = null
                }) {
                    Text(tr("Import", "Импортировать"))
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordPreview = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    bookmarkPreview?.let { importPreview ->
        AlertDialog(
            onDismissRequest = { bookmarkPreview = null },
            title = { Text(tr("Import bookmarks?", "Импортировать закладки?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Ready to import: ${importPreview.bookmarks.size}",
                            "Готово к импорту: ${importPreview.bookmarks.size}"
                        )
                    )
                    if (importPreview.skippedRows > 0) {
                        Text(
                            tr(
                                "Skipped unsupported entries: ${importPreview.skippedRows}",
                                "Пропущено неподдерживаемых записей: ${importPreview.skippedRows}"
                            )
                        )
                    }
                    if (importPreview.duplicateRows > 0) {
                        Text(
                            tr(
                                "Duplicates in file: ${importPreview.duplicateRows}",
                                "Дубликатов в файле: ${importPreview.duplicateRows}"
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        BookmarkStore.mergeExternalImport(prefs, importPreview.bookmarks)
                    }.onSuccess { added ->
                        onStatusMessage("$importedBookmarksPrefix $added")
                    }.onFailure {
                        onStatusMessage(importFailedMessage)
                    }
                    bookmarkPreview = null
                }) {
                    Text(tr("Import", "Импортировать"))
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkPreview = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(tr("Export passwords?", "Экспортировать пароли?")) },
            text = {
                Text(
                    tr(
                        "The exported CSV contains every password in plaintext. Anyone who gets this file can read them. ILYRO will require your device screen lock before creating it.",
                        "Экспортированный CSV содержит все пароли в открытом виде. Любой, кто получит этот файл, сможет их прочитать. Перед созданием файла ILYRO потребует подтвердить блокировку устройства."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    val keyguard = context.getSystemService(KeyguardManager::class.java)
                    if (keyguard?.isDeviceSecure != true) {
                        onStatusMessage(deviceLockRequiredMessage)
                        return@TextButton
                    }

                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent(
                        exportAuthTitle,
                        exportAuthDescription
                    )
                    if (intent == null) {
                        onStatusMessage(exportAuthUnavailableMessage)
                    } else {
                        exportAuthLauncher.launch(intent)
                    }
                }) {
                    Text(tr("Continue", "Продолжить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@Composable
private fun TransferCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(Modifier.padding(9.dp)) { icon() }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}
