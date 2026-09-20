package com.ilyro.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

internal val LocalIlyroLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

internal fun resolveAppLanguage(language: AppLanguage): AppLanguage = when (language) {
    AppLanguage.SYSTEM -> when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "ru" -> AppLanguage.RUSSIAN
        "es" -> AppLanguage.SPANISH
        "zh" -> AppLanguage.CHINESE
        "hi" -> AppLanguage.HINDI
        "pt" -> AppLanguage.PORTUGUESE
        "ar" -> AppLanguage.ARABIC
        "fr" -> AppLanguage.FRENCH
        "de" -> AppLanguage.GERMAN
        "ja" -> AppLanguage.JAPANESE
        "ko" -> AppLanguage.KOREAN
        "tr" -> AppLanguage.TURKISH
        "it" -> AppLanguage.ITALIAN
        "id" -> AppLanguage.INDONESIAN
        else -> AppLanguage.ENGLISH
    }
    else -> language
}

@Composable
internal fun tr(en: String, ru: String): String =
    translate(resolveAppLanguage(LocalIlyroLanguage.current), en, ru)

internal fun tr(language: AppLanguage, en: String, ru: String): String =
    translate(resolveAppLanguage(language), en, ru)

private fun translate(language: AppLanguage, en: String, ru: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    else -> ilyroTranslation(language, en) ?: en
}

internal fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> tr(language, "System", "Системный")
    else -> language.displayName
}

internal fun appLanguageLocale(language: AppLanguage): Locale = when (resolveAppLanguage(language)) {
    AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru")
    AppLanguage.SPANISH -> Locale.forLanguageTag("es")
    AppLanguage.CHINESE -> Locale.forLanguageTag("zh-CN")
    AppLanguage.HINDI -> Locale.forLanguageTag("hi")
    AppLanguage.PORTUGUESE -> Locale.forLanguageTag("pt-BR")
    AppLanguage.ARABIC -> Locale.forLanguageTag("ar")
    AppLanguage.FRENCH -> Locale.forLanguageTag("fr")
    AppLanguage.GERMAN -> Locale.forLanguageTag("de")
    AppLanguage.JAPANESE -> Locale.forLanguageTag("ja")
    AppLanguage.KOREAN -> Locale.forLanguageTag("ko")
    AppLanguage.TURKISH -> Locale.forLanguageTag("tr")
    AppLanguage.ITALIAN -> Locale.forLanguageTag("it")
    AppLanguage.INDONESIAN -> Locale.forLanguageTag("id")
    else -> Locale.ENGLISH
}

internal fun themeLabel(theme: BrowserTheme, language: AppLanguage): String = when (theme) {
    BrowserTheme.SYSTEM -> tr(language, "System", "Системная")
    BrowserTheme.LIGHT -> tr(language, "Light", "Светлая")
    BrowserTheme.DARK -> tr(language, "Dark", "Тёмная")
}
