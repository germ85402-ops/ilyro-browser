package com.ilyro.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyro.browser.account.GoogleAccountManager
import com.ilyro.browser.account.GoogleAccountProfile
import com.ilyro.browser.account.GoogleSignInResult
import com.ilyro.browser.sync.BrowserAutoSyncScheduler
import com.ilyro.browser.sync.BrowserSettingsSyncManager
import com.ilyro.browser.sync.DriveAuthorizationResult
import com.ilyro.browser.sync.GoogleDriveAuthorizationManager
import com.ilyro.browser.sync.GoogleDriveSyncProvider
import com.ilyro.browser.sync.SyncResult
import kotlinx.coroutines.launch

import java.text.DateFormat
import java.util.Date

private const val BROWSER_PREFS_NAME = "ilyro_browser"

private enum class SettingsSyncAction {
    BACKUP,
    RESTORE,
    ENABLE_AUTO,
    DELETE_REMOTE
}

/**
 * Google identity and Drive sync UI. Authentication and Drive authorization intentionally remain
 * separate: signing in never grants Drive access until the user explicitly starts synchronization.
 */
@Composable
internal fun AccountSettingsSection() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val browserPrefs = remember(appContext) {
        appContext.getSharedPreferences(BROWSER_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val accountManager = remember(appContext) {
        GoogleAccountManager(appContext)
    }
    val driveAuthorizationManager = remember(appContext) {
        GoogleDriveAuthorizationManager(appContext)
    }
    val syncManager = remember(appContext) {
        BrowserSettingsSyncManager(appContext)
    }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(accountManager.currentProfile()) }
    var busy by remember { mutableStateOf(false) }
    var pendingSyncAction by remember { mutableStateOf<SettingsSyncAction?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var autoSyncEnabled by remember {
        mutableStateOf(BrowserAutoSyncScheduler.isEnabled(appContext))
    }
    var lastSyncAtEpochMs by remember {
        mutableStateOf(syncManager.lastSyncAtEpochMs())
    }
    var showDeleteRemoteConfirmation by remember { mutableStateOf(false) }

    // Resolve localized strings while in composition, not from callbacks/coroutines.
    val signInUnavailableMessage = tr(
        "Google sign-in is unavailable in this screen.",
        "Вход через Google недоступен на этом экране."
    )
    val signInSuccessMessage = tr("Signed in successfully.", "Вход выполнен.")
    val oauthNotConfiguredMessage = tr(
        "Google OAuth is not configured for this build yet.",
        "Google OAuth пока не настроен для этой сборки."
    )
    val signInCancelledMessage = tr("Sign-in cancelled.", "Вход отменён.")
    val noGoogleAccountMessage = tr(
        "No Google account is available on this device.",
        "На устройстве не найден доступный аккаунт Google."
    )
    val unsupportedCredentialMessage = tr(
        "Google returned an unsupported credential.",
        "Google вернул неподдерживаемые данные авторизации."
    )
    val signInFailedMessage = tr(
        "Google sign-in failed.",
        "Не удалось выполнить вход через Google."
    )
    val signedOutMessage = tr("Signed out.", "Вы вышли из аккаунта.")
    val driveAuthorizationFailedMessage = tr(
        "Google Drive permission could not be granted.",
        "Не удалось получить разрешение Google Drive."
    )
    val driveAuthorizationCancelledMessage = tr(
        "Google Drive permission was not granted.",
        "Разрешение Google Drive не было предоставлено."
    )
    val backupSuccessMessage = tr(
        "Browser data saved to Google Drive.",
        "Данные браузера сохранены в Google Drive."
    )
    val restoreSuccessMessage = tr(
        "Browser data restored from Google Drive. ILYRO will reload to apply it.",
        "Данные браузера восстановлены из Google Drive. ILYRO перезапустит экран, чтобы применить их."
    )
    val noBackupMessage = tr(
        "No ILYRO browser backup was found in Google Drive.",
        "В Google Drive не найдена резервная копия ILYRO."
    )
    val syncFailedMessage = tr(
        "Browser data sync failed.",
        "Не удалось синхронизировать данные браузера."
    )

    val automaticSyncEnabledMessage = tr(
        "Automatic sync enabled.",
        "Автосинхронизация включена."
    )
    val automaticSyncDisabledMessage = tr(
        "Automatic sync disabled.",
        "Автосинхронизация выключена."
    )
    val automaticSyncUnavailableMessage = tr(
        "Automatic sync could not be enabled on this device.",
        "Не удалось включить автосинхронизацию на этом устройстве."
    )
    val localChangesPreservedMessage = tr(
        "Local settings and tabs were preserved because they changed on this device.",
        "Локальные настройки и вкладки сохранены: на этом устройстве были изменения."
    )
    val deleteRemoteTitle = tr(
        "Delete cloud backup?",
        "Удалить облачную копию?"
    )
    val deleteRemoteMessage = tr(
        "This removes the ILYRO backup from Google Drive. Data stored on this device will not be deleted.",
        "Это удалит резервную копию ILYRO из Google Drive. Данные на этом устройстве удалены не будут."
    )
    val deleteRemoteConfirm = tr("Delete backup", "Удалить копию")
    val deleteRemoteCancel = tr("Cancel", "Отмена")
    val deleteRemoteSuccessMessage = tr(
        "Cloud backup deleted. Automatic sync was disabled.",
        "Облачная копия удалена. Автосинхронизация выключена."
    )
    val automaticSyncError = BrowserAutoSyncScheduler.lastError(appContext)

    fun automaticSyncFailureMessage(): String =
        BrowserAutoSyncScheduler.lastError(appContext)
            ?.takeIf { it.isNotBlank() }
            ?.let { "$automaticSyncUnavailableMessage\n$it" }
            ?: automaticSyncUnavailableMessage

    suspend fun performSync(action: SettingsSyncAction, accessToken: String): Boolean {
        val provider = GoogleDriveSyncProvider(accessToken)
        return when (action) {
            SettingsSyncAction.BACKUP -> {
                val currentSettings = BrowserSettingsStore.restore(browserPrefs)
                when (val result = syncManager.backup(currentSettings, provider)) {
                    is SyncResult.Success -> {
                        lastSyncAtEpochMs = syncManager.lastSyncAtEpochMs()
                        statusMessage = backupSuccessMessage
                        true
                    }

                    is SyncResult.Failure -> {
                        statusMessage = "$syncFailedMessage${result.message?.let { "\n$it" }.orEmpty()}"
                        false
                    }

                    SyncResult.NotAuthorized -> {
                        statusMessage = driveAuthorizationFailedMessage
                        false
                    }
                }
            }

            SettingsSyncAction.RESTORE -> {
                when (val result = syncManager.restore(provider)) {
                    is SyncResult.Success -> {
                        val restored = result.value
                        if (restored == null) {
                            statusMessage = noBackupMessage
                            false
                        } else {
                            BrowserSettingsStore.save(browserPrefs, restored)
                            lastSyncAtEpochMs = syncManager.lastSyncAtEpochMs()
                            statusMessage = restoreSuccessMessage
                            context.findActivity()?.recreate()
                            true
                        }
                    }

                    is SyncResult.Failure -> {
                        statusMessage = "$syncFailedMessage${result.message?.let { "\n$it" }.orEmpty()}"
                        false
                    }

                    SyncResult.NotAuthorized -> {
                        statusMessage = driveAuthorizationFailedMessage
                        false
                    }
                }
            }

            SettingsSyncAction.ENABLE_AUTO -> {
                val currentSettings = BrowserSettingsStore.restore(browserPrefs)
                when (val result = syncManager.sync(currentSettings, provider)) {
                    is SyncResult.Success -> {
                        if (BrowserAutoSyncScheduler.setEnabled(appContext, true)) {
                            autoSyncEnabled = true
                            lastSyncAtEpochMs = syncManager.lastSyncAtEpochMs()
                            statusMessage = if (result.value.preservedLocalChanges) {
                                "$automaticSyncEnabledMessage\n$localChangesPreservedMessage"
                            } else {
                                automaticSyncEnabledMessage
                            }
                            if (result.value.appliedRemoteSettings || result.value.appliedRemoteTabs) {
                                context.findActivity()?.recreate()
                            }
                            true
                        } else {
                            statusMessage = automaticSyncFailureMessage()
                            false
                        }
                    }

                    is SyncResult.Failure -> {
                        statusMessage = "$syncFailedMessage${result.message?.let { "\n$it" }.orEmpty()}"
                        false
                    }

                    SyncResult.NotAuthorized -> {
                        statusMessage = driveAuthorizationFailedMessage
                        false
                    }
                }
            }

            SettingsSyncAction.DELETE_REMOTE -> {
                BrowserAutoSyncScheduler.setEnabled(appContext, false)
                autoSyncEnabled = false
                when (val result = provider.deleteRemoteData()) {
                    is SyncResult.Success -> {
                        statusMessage = deleteRemoteSuccessMessage
                        true
                    }

                    is SyncResult.Failure -> {
                        statusMessage = "$syncFailedMessage${result.message?.let { "\n$it" }.orEmpty()}"
                        false
                    }

                    SyncResult.NotAuthorized -> {
                        statusMessage = driveAuthorizationFailedMessage
                        false
                    }
                }
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val action = pendingSyncAction
        pendingSyncAction = null

        if (activityResult.resultCode != Activity.RESULT_OK || action == null) {
            busy = false
            statusMessage = driveAuthorizationCancelledMessage
            return@rememberLauncherForActivityResult
        }

        when (val result = driveAuthorizationManager.resolve(activityResult.data)) {
            is DriveAuthorizationResult.Authorized -> {
                scope.launch {
                    try {
                        performSync(action, result.accessToken)
                    } finally {
                        busy = false
                    }
                }
            }
            is DriveAuthorizationResult.ResolutionRequired -> {
                busy = false
                statusMessage = driveAuthorizationFailedMessage
            }
            is DriveAuthorizationResult.Failure -> {
                busy = false
                statusMessage = result.message?.takeIf { it.isNotBlank() }
                    ?: driveAuthorizationFailedMessage
            }
        }
    }

    fun requestSync(action: SettingsSyncAction) {
        val currentProfile = profile ?: return
        busy = true
        statusMessage = null

        scope.launch {
            when (val result = driveAuthorizationManager.authorize(currentProfile.email)) {
                is DriveAuthorizationResult.Authorized -> {
                    try {
                        performSync(action, result.accessToken)
                    } finally {
                        busy = false
                    }
                }
                is DriveAuthorizationResult.ResolutionRequired -> {
                    pendingSyncAction = action
                    val request = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    authorizationLauncher.launch(request)
                }
                is DriveAuthorizationResult.Failure -> {
                    busy = false
                    statusMessage = result.message?.takeIf { it.isNotBlank() }
                        ?: driveAuthorizationFailedMessage
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                tr("Account & sync", "Аккаунт и синхронизация"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val currentProfile = profile
            if (currentProfile == null) {
                SignedOutAccountContent(
                    configured = accountManager.isConfigured(),
                    busy = busy,
                    onSignIn = {
                        val activity = context.findActivity()
                        if (activity == null) {
                            statusMessage = signInUnavailableMessage
                        } else {
                            busy = true
                            statusMessage = null
                            scope.launch {
                                try {
                                    when (val result = accountManager.signIn(activity)) {
                                        is GoogleSignInResult.Success -> {
                                            profile = result.profile
                                            statusMessage = signInSuccessMessage
                                        }
                                        GoogleSignInResult.NotConfigured -> {
                                            statusMessage = oauthNotConfiguredMessage
                                        }
                                        GoogleSignInResult.Cancelled -> {
                                            statusMessage = signInCancelledMessage
                                        }
                                        GoogleSignInResult.NoGoogleAccount -> {
                                            statusMessage = noGoogleAccountMessage
                                        }
                                        GoogleSignInResult.UnsupportedCredential,
                                        GoogleSignInResult.InvalidCredential -> {
                                            statusMessage = unsupportedCredentialMessage
                                        }
                                        is GoogleSignInResult.Failure -> {
                                            statusMessage = result.message ?: signInFailedMessage
                                        }
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
            } else {
                SignedInAccountContent(
                    profile = currentProfile,
                    busy = busy,
                    onBackup = { requestSync(SettingsSyncAction.BACKUP) },
                    onRestore = { requestSync(SettingsSyncAction.RESTORE) },
                    onDeleteRemote = { showDeleteRemoteConfirmation = true },
                    autoSyncEnabled = autoSyncEnabled,
                    lastSyncAtEpochMs = lastSyncAtEpochMs,
                    automaticSyncError = automaticSyncError,
                    onAutoSyncChanged = { enabled ->
                        if (enabled) {
                            requestSync(SettingsSyncAction.ENABLE_AUTO)
                        } else {
                            autoSyncEnabled = false
                            busy = true
                            scope.launch {
                                try {
                                    statusMessage =
                                        if (BrowserAutoSyncScheduler.setEnabled(appContext, false)) {
                                            automaticSyncDisabledMessage
                                        } else {
                                            automaticSyncFailureMessage()
                                        }
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onSignOut = {
                        autoSyncEnabled = false
                        busy = true
                        statusMessage = null
                        scope.launch {
                            try {
                                BrowserAutoSyncScheduler.setEnabled(appContext, false)
                                accountManager.signOut()
                            } finally {
                                pendingSyncAction = null
                                profile = null
                                statusMessage = signedOutMessage
                                busy = false
                            }
                        }
                    }
                )
            }

            statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                tr(
                    "Automatic sync merges history, bookmarks, and quick links. Newer settings and open tabs are applied unless both devices changed them; then local changes are preserved. Passwords, cookies, private tabs, downloads, and custom wallpaper files stay on this device.",
                    "Автосинхронизация объединяет историю, закладки и быстрые ссылки. Новые настройки и открытые вкладки применяются, если оба устройства не меняли их; иначе сохраняются локальные изменения. Пароли, cookie, приватные вкладки, загрузки и файлы пользовательских обоев остаются на этом устройстве."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, PasswordManagerActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Passwords & import", "Пароли и импорт"))
            }
        }
    }

    if (showDeleteRemoteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteRemoteConfirmation = false },
            title = { Text(deleteRemoteTitle) },
            text = { Text(deleteRemoteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteRemoteConfirmation = false
                        requestSync(SettingsSyncAction.DELETE_REMOTE)
                    }
                ) {
                    Text(deleteRemoteConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRemoteConfirmation = false }) {
                    Text(deleteRemoteCancel)
                }
            }
        )
    }
}

@Composable
private fun SignedOutAccountContent(
    configured: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit
) {
    Text(
        tr(
            "Sign in with Google to sync your browser data across devices.",
            "Войдите через Google, чтобы синхронизировать данные браузера между устройствами."
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = onSignIn,
        enabled = configured && !busy,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = {
                fadeIn(animationSpec = tween(160)) togetherWith
                    fadeOut(animationSpec = tween(100))
            },
            label = "google-sign-in-button"
        ) { signingIn ->
            if (signingIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(tr("Signing in…", "Выполняется…"))
                }
            } else {
                Text(tr("Sign in with Google", "Войти через Google"))
            }
        }
    }

    if (!configured) {
        Text(
            tr(
                "OAuth client ID still needs to be added before sign-in can be enabled.",
                "Перед включением входа нужно добавить OAuth Client ID."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignedInAccountContent(
    profile: GoogleAccountProfile,
    busy: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDeleteRemote: () -> Unit,
    autoSyncEnabled: Boolean,
    lastSyncAtEpochMs: Long,
    automaticSyncError: String?,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val lastSyncLabel = if (lastSyncAtEpochMs > 0L) {
        tr(
            "Last sync: ${formatSyncTime(lastSyncAtEpochMs)}",
            "Последняя синхронизация: ${formatSyncTime(lastSyncAtEpochMs)}"
        )
    } else {
        tr("Last sync: never", "Последняя синхронизация: никогда")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.displayName?.takeIf { it.isNotBlank() }
                    ?: tr("Google account", "Аккаунт Google"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (profile.email.isNotBlank()) {
                Text(
                    profile.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(
            onClick = onSignOut,
            enabled = !busy
        ) {
            Text(tr("Sign out", "Выйти"))
        }
    }

    Text(
        tr(
            "Browser data sync",
            "Синхронизация данных браузера"
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onBackup,
            enabled = !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(tr("Save", "Сохранить"))
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(tr("Restore", "Восстановить"))
        }
    }

    OutlinedButton(
        onClick = onDeleteRemote,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(tr("Delete cloud backup", "Удалить облачную копию"))
    }

    if (busy) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    tr("Working…", "Выполняется…"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tr("Automatic sync", "Автоматическая синхронизация"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                tr("Every 12 hours when a network is available.", "Каждые 12 часов при наличии сети."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = autoSyncEnabled,
            enabled = !busy,
            onCheckedChange = onAutoSyncChanged
        )
        Text(
            text = lastSyncLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    automaticSyncError?.takeIf { it.isNotBlank() }?.let { error ->
        Text(
            text = tr("Last automatic sync error: $error", "Ошибка автосинхронизации: $error"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

}

private fun formatSyncTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
