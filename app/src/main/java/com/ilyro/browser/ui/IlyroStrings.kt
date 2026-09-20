package com.ilyro.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

internal val LocalIlyroLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

internal fun resolveAppLanguage(language: AppLanguage): AppLanguage = when (language) {
    AppLanguage.SYSTEM -> if (Locale.getDefault().language.equals("ru", ignoreCase = true)) {
        AppLanguage.RUSSIAN
    } else {
        AppLanguage.ENGLISH
    }
    else -> language
}

@Composable
internal fun tr(en: String, ru: String): String =
    if (resolveAppLanguage(LocalIlyroLanguage.current) == AppLanguage.RUSSIAN) ru else en

internal fun tr(language: AppLanguage, en: String, ru: String): String =
    if (resolveAppLanguage(language) == AppLanguage.RUSSIAN) ru else en

internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> tr(language, "System", "Системный")
    AppLanguage.ENGLISH -> "English"
    AppLanguage.RUSSIAN -> "Русский"
}

internal fun themeLabel(theme: BrowserTheme, language: AppLanguage): String = when (theme) {
    BrowserTheme.SYSTEM -> tr(language, "System", "Системная")
    BrowserTheme.LIGHT -> tr(language, "Light", "Светлая")
    BrowserTheme.DARK -> tr(language, "Dark", "Тёмная")
}
