package com.ilyro.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shows what a WebExtension asks for before ILYRO grants it.
 *
 * Without this host the requests recorded by [ProtectionBridge] were never answered, so an
 * install that needed permissions stayed pending forever and ILYRO had to silently approve
 * queued installs instead.
 */
@Composable
internal fun ExtensionPermissionPromptHost() {
    val request = ProtectionBridge.pendingInstallPermissionRequest ?: return

    val title = when (request.kind) {
        ExtensionPermissionPromptKind.INSTALL ->
            tr("Add ${request.extensionName}?", "Добавить ${request.extensionName}?")
        ExtensionPermissionPromptKind.OPTIONAL ->
            tr(
                "${request.extensionName} needs more access",
                "${request.extensionName} запрашивает больше доступа"
            )
        ExtensionPermissionPromptKind.UPDATE ->
            tr(
                "${request.extensionName} needs new permissions",
                "${request.extensionName} запрашивает новые разрешения"
            )
    }

    AlertDialog(
        onDismissRequest = {
            ProtectionBridge.respondToInstallPermissionPrompt(request.requestId, false)
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr(
                        "This extension is asking for the following access:",
                        "Расширение запрашивает следующий доступ:"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PermissionGroup(
                    label = tr("Permissions", "Разрешения"),
                    values = request.permissions
                )
                PermissionGroup(
                    label = tr("Sites", "Сайты"),
                    values = request.origins
                )
                PermissionGroup(
                    label = tr("Data collection", "Сбор данных"),
                    values = request.dataCollectionPermissions
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ProtectionBridge.respondToInstallPermissionPrompt(request.requestId, true)
            }) {
                Text(tr("Allow", "Разрешить"))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                ProtectionBridge.respondToInstallPermissionPrompt(request.requestId, false)
            }) {
                Text(tr("Cancel", "Отмена"))
            }
        }
    )
}

@Composable
private fun PermissionGroup(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        values.take(12).forEach { value ->
            Text(
                text = "• $value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (values.size > 12) {
            Text(
                text = tr("and ${values.size - 12} more", "и ещё ${values.size - 12}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
