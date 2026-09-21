package com.ilyro.browser.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilyro.browser.passwords.PasswordCredential
import com.ilyro.browser.passwords.PasswordCsvImportPreview
import com.ilyro.browser.passwords.PasswordCsvImporter
import com.ilyro.browser.passwords.PasswordManagerService
import com.ilyro.browser.passwords.PasswordVaultReadException
import java.net.URI
import kotlinx.coroutines.delay

private enum class DeviceAuthFailure {
    NOT_CONFIGURED,
    UNAVAILABLE,
    CANCELLED
}

class PasswordManagerActivity : ComponentActivity() {
    private var pendingAuthSuccess: (() -> Unit)? = null
    private var pendingAuthFailure: ((DeviceAuthFailure) -> Unit)? = null

    private val deviceAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val onSuccess = pendingAuthSuccess
        val onFailure = pendingAuthFailure
        pendingAuthSuccess = null
        pendingAuthFailure = null

        if (result.resultCode == Activity.RESULT_OK) {
            onSuccess?.invoke()
        } else {
            onFailure?.invoke(DeviceAuthFailure.CANCELLED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ilyro_browser", MODE_PRIVATE)
        val settings = BrowserSettingsStore.restore(prefs)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.theme) {
                BrowserTheme.SYSTEM -> systemDark
                BrowserTheme.LIGHT -> false
                BrowserTheme.DARK -> true
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                IlyroSystemBarAppearance()
                CompositionLocalProvider(
                    LocalIlyroLanguage provides resolveAppLanguage(settings.language)
                ) {
                    PasswordManagerScreen(
                        onClose = ::finish,
                        requestDeviceAuth = ::requestDeviceAuthentication,
                        onOpenAutofillSettings = ::openAutofillSettings
                    )
                }
            }
        }
    }

    private fun requestDeviceAuthentication(
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onFailure: (DeviceAuthFailure) -> Unit
    ) {
        if (pendingAuthSuccess != null) {
            onFailure(DeviceAuthFailure.UNAVAILABLE)
            return
        }

        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            onFailure(DeviceAuthFailure.NOT_CONFIGURED)
            return
        }

        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) {
            onFailure(DeviceAuthFailure.UNAVAILABLE)
            return
        }

        pendingAuthSuccess = onSuccess
        pendingAuthFailure = onFailure
        deviceAuthLauncher.launch(intent)
    }

    private fun openAutofillSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
            .setData(Uri.parse("package:" + packageName))
        runCatching { startActivity(request) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}

@Composable
private fun PasswordManagerScreen(
    onClose: () -> Unit,
    requestDeviceAuth: (
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onFailure: (DeviceAuthFailure) -> Unit
    ) -> Unit,
    onOpenAutofillSettings: () -> Unit
) {
    val context = LocalContext.current
    val vault = remember(context) { PasswordManagerService.vault(context) }
    var credentials by remember { mutableStateOf<List<PasswordCredential>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<PasswordCsvImportPreview?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var vaultReadError by remember { mutableStateOf(false) }
    var showVaultResetDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<PasswordCredential?>(null) }
    var editCandidate by remember { mutableStateOf<PasswordCredential?>(null) }
    var revealedGuid by remember { mutableStateOf<String?>(null) }

    val readCsvError = tr(
        "Could not read this CSV file.",
        "Не удалось прочитать этот CSV-файл."
    )
    val parseCsvError = tr(
        "Could not parse this password export.",
        "Не удалось разобрать экспорт паролей."
    )
    val importFailedError = tr(
        "Import failed.",
        "Не удалось импортировать пароли."
    )
    val passwordUpdatedMessage = tr(
        "Password updated.",
        "Пароль обновлён."
    )
    val passwordUpdateFailedMessage = tr(
        "Could not update this password.",
        "Не удалось обновить этот пароль."
    )
    val vaultReadErrorMessage = tr(
        "Saved passwords could not be opened. The encrypted vault was preserved. Try again or reset it to import a new copy.",
        "Не удалось открыть сохранённые пароли. Зашифрованное хранилище сохранено. Повторите попытку или сбросьте его, чтобы импортировать новую копию."
    )
    val vaultResetMessage = tr(
        "Password storage was reset. You can import passwords again.",
        "Хранилище паролей сброшено. Теперь можно снова импортировать пароли."
    )
    val blankPasswordMessage = tr(
        "Password cannot be empty.",
        "Пароль не может быть пустым."
    )
    val deviceLockRequiredMessage = tr(
        "Set a device screen lock before revealing or editing saved passwords.",
        "Настройте блокировку экрана устройства, чтобы просматривать или редактировать сохранённые пароли."
    )
    val deviceAuthUnavailableMessage = tr(
        "Device authentication is unavailable right now.",
        "Подтверждение устройства сейчас недоступно."
    )
    val deviceAuthCancelledMessage = tr(
        "Device authentication was cancelled.",
        "Подтверждение устройства отменено."
    )
    val revealAuthTitle = tr("Reveal saved password", "Показать сохранённый пароль")
    val revealAuthDescription = tr(
        "Confirm your device screen lock to reveal this password.",
        "Подтвердите блокировку устройства, чтобы показать этот пароль."
    )
    val editAuthTitle = tr("Edit saved password", "Изменить сохранённый пароль")
    val editAuthDescription = tr(
        "Confirm your device screen lock to edit this login.",
        "Подтвердите блокировку устройства, чтобы изменить эти данные входа."
    )

    fun authFailureMessage(failure: DeviceAuthFailure): String = when (failure) {
        DeviceAuthFailure.NOT_CONFIGURED -> deviceLockRequiredMessage
        DeviceAuthFailure.UNAVAILABLE -> deviceAuthUnavailableMessage
        DeviceAuthFailure.CANCELLED -> deviceAuthCancelledMessage
    }

    fun reload() {
        runCatching { vault.snapshot() }
            .onSuccess { restored ->
                credentials = restored
                vaultReadError = false
                if (credentials.none { it.guid == revealedGuid }) {
                    revealedGuid = null
                }
            }
            .onFailure { error ->
                credentials = emptyList()
                if (error is PasswordVaultReadException) {
                    vaultReadError = true
                    statusMessage = vaultReadErrorMessage
                } else {
                    statusMessage = vaultReadErrorMessage
                }
            }
    }

    LaunchedEffect(vault) { reload() }

    LaunchedEffect(revealedGuid) {
        if (revealedGuid != null) {
            delay(30_000L)
            revealedGuid = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText()
            } ?: error("Unable to open CSV")
        }.getOrElse {
            statusMessage = readCsvError
            return@rememberLauncherForActivityResult
        }

        val preview = runCatching { PasswordCsvImporter.preview(raw) }.getOrElse {
            statusMessage = parseCsvError
            return@rememberLauncherForActivityResult
        }
        importPreview = preview
        statusMessage = preview.error
    }

    val filtered = remember(credentials, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) credentials else credentials.filter { credential ->
            credential.origin.lowercase().contains(needle) ||
                credential.username.lowercase().contains(needle)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back", "Назад"))
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        tr("Passwords & import", "Пароли и импорт"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tr(
                            "Encrypted locally with Android Keystore",
                            "Локальное шифрование через Android Keystore"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = {
                    statusMessage = null
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("CSV", "CSV"))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text(tr("Search passwords", "Поиск паролей")) }
                )
                statusMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (vaultReadError) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { reload() }) {
                            Text(tr("Try again", "Повторить"))
                        }
                        TextButton(onClick = { showVaultResetDialog = true }) {
                            Text(tr("Reset storage", "Сбросить хранилище"))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BookmarkImportControl(
                    onStatusMessage = { message -> statusMessage = message }
                )
                TextButton(
                    onClick = onOpenAutofillSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tr(
                            "Use ILYRO for Android autofill",
                            "Использовать ILYRO для автозаполнения Android"
                        )
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (credentials.isEmpty()) {
                                tr("No saved passwords yet", "Сохранённых паролей пока нет")
                            } else {
                                tr("Nothing found", "Ничего не найдено")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.guid }) { credential ->
                        val revealed = revealedGuid == credential.guid
                        PasswordCredentialCard(
                            credential = credential,
                            revealed = revealed,
                            onRevealToggle = {
                                if (revealed) {
                                    revealedGuid = null
                                } else {
                                    statusMessage = null
                                    requestDeviceAuth(
                                        revealAuthTitle,
                                        revealAuthDescription,
                                        {
                                            revealedGuid = credential.guid
                                            statusMessage = null
                                        },
                                        { failure -> statusMessage = authFailureMessage(failure) }
                                    )
                                }
                            },
                            onEdit = {
                                statusMessage = null
                                requestDeviceAuth(
                                    editAuthTitle,
                                    editAuthDescription,
                                    {
                                        revealedGuid = null
                                        editCandidate = credential
                                    },
                                    { failure -> statusMessage = authFailureMessage(failure) }
                                )
                            },
                            onDelete = { deleteCandidate = credential }
                        )
                    }
                }
            }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text(tr("Import passwords?", "Импортировать пароли?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Ready to import: ${preview.credentials.size}",
                            "Готово к импорту: ${preview.credentials.size}"
                        )
                    )
                    if (preview.skippedRows > 0) {
                        Text(
                            tr(
                                "Skipped invalid rows: ${preview.skippedRows}",
                                "Пропущено некорректных строк: ${preview.skippedRows}"
                            )
                        )
                    }
                    if (preview.duplicateRows > 0) {
                        Text(
                            tr(
                                "Duplicates merged: ${preview.duplicateRows}",
                                "Объединено дублей: ${preview.duplicateRows}"
                            )
                        )
                    }
                    Text(
                        tr(
                            "The selected CSV is read in memory and is not copied into ILYRO storage.",
                            "Выбранный CSV читается в памяти и не копируется в хранилище ILYRO."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.canImport,
                    onClick = {
                        runCatching {
                            preview.credentials.forEach(vault::upsert)
                        }.onSuccess {
                            reload()
                            statusMessage = null
                        }.onFailure {
                            statusMessage = importFailedError
                        }
                        importPreview = null
                    }
                ) {
                    Text(tr("Import", "Импортировать"))
                }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    editCandidate?.let { credential ->
        var username by remember(credential.guid) { mutableStateOf(credential.username) }
        var password by remember(credential.guid) { mutableStateOf(credential.password) }
        var showPassword by remember(credential.guid) { mutableStateOf(false) }
        var editError by remember(credential.guid) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editCandidate = null },
            title = { Text(tr("Edit saved login", "Изменить данные входа")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        credential.origin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(tr("Username", "Имя пользователя")) }
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        label = { Text(tr("Password", "Пароль")) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPassword) {
                                        tr("Hide password", "Скрыть пароль")
                                    } else {
                                        tr("Show password", "Показать пароль")
                                    }
                                )
                            }
                        }
                    )
                    editError?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password.isEmpty()) {
                        editError = blankPasswordMessage
                        return@TextButton
                    }
                    runCatching {
                        vault.upsert(
                            credential.copy(
                                username = username,
                                password = password
                            )
                        )
                    }.onSuccess {
                        reload()
                        statusMessage = passwordUpdatedMessage
                        editCandidate = null
                    }.onFailure {
                        editError = passwordUpdateFailedMessage
                    }
                }) {
                    Text(tr("Save", "Сохранить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editCandidate = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    deleteCandidate?.let { credential ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(tr("Delete password?", "Удалить пароль?")) },
            text = {
                Text(
                    credential.username.takeIf { it.isNotBlank() }
                        ?: credential.origin
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        vault.replaceAll(vault.snapshot().filterNot { it.guid == credential.guid })
                    }.onSuccess { reload() }
                        .onFailure { error ->
                            if (error is PasswordVaultReadException) {
                                vaultReadError = true
                                statusMessage = vaultReadErrorMessage
                            }
                        }
                    deleteCandidate = null
                }) {
                    Text(tr("Delete", "Удалить"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }

    if (showVaultResetDialog) {
        AlertDialog(
            onDismissRequest = { showVaultResetDialog = false },
            title = { Text(tr("Reset password storage?", "Сбросить хранилище паролей?")) },
            text = {
                Text(
                    tr(
                        "The unreadable encrypted file will be removed from active storage. A recovery copy is kept when possible. You can then import a fresh CSV export.",
                        "Нечитаемый зашифрованный файл будет удалён из активного хранилища. По возможности резервная копия сохранится. После этого можно импортировать новый CSV-файл."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vault.clear()
                    credentials = emptyList()
                    vaultReadError = false
                    showVaultResetDialog = false
                    statusMessage = vaultResetMessage
                }) {
                    Text(tr("Reset", "Сбросить"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultResetDialog = false }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@Composable
private fun PasswordCredentialCard(
    credential: PasswordCredential,
    revealed: Boolean,
    onRevealToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val host = remember(credential.origin) {
        runCatching { URI(credential.origin).host }.getOrNull() ?: credential.origin
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    host,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    credential.username.takeIf { it.isNotBlank() }
                        ?: tr("No username", "Без имени пользователя"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (revealed) credential.password else "••••••••",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRevealToggle) {
                Icon(
                    if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) {
                        tr("Hide password", "Скрыть пароль")
                    } else {
                        tr("Reveal password", "Показать пароль")
                    }
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = tr("Edit", "Изменить")
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = tr("Delete", "Удалить"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
