package com.ilyro.browser.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WallpaperQuickPickerSheet(
    settings: BrowserSettings,
    darkTheme: Boolean,
    onSettingsChange: (BrowserSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val selectedBackground = if (darkTheme) settings.darkHomeBackground else settings.homeBackground
    val selectedCustomUri = if (darkTheme) settings.darkCustomWallpaperUri else settings.customWallpaperUri
    var pickingForDarkTheme by remember(darkTheme) { mutableStateOf(darkTheme) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (pickingForDarkTheme) {
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
                        useSeparateDarkBackground = true,
                        homeBackground = HomeBackground.CUSTOM,
                        customWallpaperUri = uri.toString()
                    )
                )
            }
            onDismiss()
        }
    }

    fun chooseBackground(background: HomeBackground) {
        if (background == HomeBackground.CUSTOM && selectedCustomUri.isNullOrBlank()) {
            pickingForDarkTheme = darkTheme
            imagePicker.launch(arrayOf("image/*"))
            return
        }
        onSettingsChange(
            if (darkTheme) {
                settings.copy(useSeparateDarkBackground = true, darkHomeBackground = background)
            } else {
                settings.copy(useSeparateDarkBackground = true, homeBackground = background)
            }
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .stabilizeBottomSheetFling()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                tr("Wallpaper", "Обои"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (darkTheme) tr("Dark theme", "Тёмная тема") else tr("Light theme", "Светлая тема"),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WallpaperPresetGrid(
                selected = selectedBackground,
                customUri = selectedCustomUri,
                options = wallpaperPresetOptions(darkTheme, selectedBackground),
                onSelect = ::chooseBackground
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    pickingForDarkTheme = darkTheme
                    imagePicker.launch(arrayOf("image/*"))
                }) {
                    Text(tr("Choose photo", "Выбрать фото"))
                }
                if (selectedBackground == HomeBackground.CUSTOM && !selectedCustomUri.isNullOrBlank()) {
                    TextButton(onClick = {
                        onSettingsChange(
                            if (darkTheme) {
                                settings.copy(
                                    darkCustomWallpaperUri = null,
                                    darkHomeBackground = DARK_WALLPAPER_PRESETS.first(),
                                    useSeparateDarkBackground = true
                                )
                            } else {
                                settings.copy(
                                    customWallpaperUri = null,
                                    homeBackground = LIGHT_WALLPAPER_PRESETS.first(),
                                    useSeparateDarkBackground = true
                                )
                            }
                        )
                        onDismiss()
                    }) {
                        Text(tr("Remove", "Удалить"))
                    }
                }
            }
        }
    }
}
