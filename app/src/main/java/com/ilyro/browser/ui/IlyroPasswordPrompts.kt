package com.ilyro.browser.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class IlyroLoginChoice(
    val username: String,
    val host: String
)

internal fun showIlyroPasswordSavePrompt(
    owner: ComponentActivity,
    host: String,
    username: String,
    updating: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
): Dialog {
    val dialog = createIlyroPromptDialog(owner)
    dialog.setContentView(
        ComposeView(owner).apply {
            setContent {
                IlyroPromptTheme(owner) {
                    PasswordSaveSheet(
                        host = host,
                        username = username,
                        updating = updating,
                        onAccept = {
                            onAccept()
                            dialog.dismiss()
                        },
                        onDismiss = {
                            onDismiss()
                            dialog.dismiss()
                        }
                    )
                }
            }
        }
    )
    dialog.setOnCancelListener { onDismiss() }
    dialog.show()
    applyIlyroPromptWindow(dialog)
    return dialog
}

internal fun showIlyroLoginSelectPrompt(
    owner: ComponentActivity,
    choices: List<IlyroLoginChoice>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
): Dialog {
    val dialog = createIlyroPromptDialog(owner)
    dialog.setContentView(
        ComposeView(owner).apply {
            setContent {
                IlyroPromptTheme(owner) {
                    LoginSelectSheet(
                        choices = choices,
                        onSelect = { index ->
                            onSelect(index)
                            dialog.dismiss()
                        },
                        onDismiss = {
                            onDismiss()
                            dialog.dismiss()
                        }
                    )
                }
            }
        }
    )
    dialog.setOnCancelListener { onDismiss() }
    dialog.show()
    applyIlyroPromptWindow(dialog)
    return dialog
}

private fun createIlyroPromptDialog(owner: ComponentActivity): Dialog =
    ComponentDialog(owner).apply {
        setCanceledOnTouchOutside(true)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

private fun applyIlyroPromptWindow(dialog: Dialog) {
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.30f }
    }
}

@Composable
private fun IlyroPromptTheme(
    owner: ComponentActivity,
    content: @Composable () -> Unit
) {
    val prefs = owner.getSharedPreferences("ilyro_browser", Context.MODE_PRIVATE)
    val settings = BrowserSettingsStore.restore(prefs)
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        BrowserTheme.SYSTEM -> systemDark
        BrowserTheme.LIGHT -> false
        BrowserTheme.DARK -> true
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalIlyroLanguage provides resolveAppLanguage(settings.language),
            content = content
        )
    }
}

@Composable
private fun PasswordSaveSheet(
    host: String,
    username: String,
    updating: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    PromptSurface {
        PromptHeader(
            title = if (updating) tr("Update password?", "Обновить пароль?") else tr("Save password?", "Сохранить пароль?"),
            subtitle = tr(
                "ILYRO keeps it encrypted on this device.",
                "ILYRO сохранит его в зашифрованном виде на устройстве."
            )
        )

        CredentialPreview(host = host, username = username)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(tr("Not now", "Не сейчас"))
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (updating) tr("Update", "Обновить") else tr("Save", "Сохранить"))
            }
        }
    }
}

@Composable
private fun LoginSelectSheet(
    choices: List<IlyroLoginChoice>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    PromptSurface {
        PromptHeader(
            title = tr("Choose an account", "Выберите аккаунт"),
            subtitle = tr(
                "Your password stays hidden while ILYRO fills it securely.",
                "Пароль останется скрытым, пока ILYRO безопасно подставляет его."
            )
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            LazyColumn {
                itemsIndexed(choices) { index, choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                choice.username,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                choice.host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (index != choices.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(tr("Cancel", "Отмена"))
        }
    }
}

@Composable
private fun PromptSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun PromptHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CredentialPreview(host: String, username: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    username,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "••••••••",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
