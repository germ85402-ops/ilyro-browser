package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IlyroStringsTest {

    @Test
    fun supportedLanguagesExposeNativeLabels() {
        assertEquals("Español", appLanguageLabel(AppLanguage.SPANISH))
        assertEquals("中文", appLanguageLabel(AppLanguage.CHINESE))
        assertEquals("हिन्दी", appLanguageLabel(AppLanguage.HINDI))
        assertEquals("Bahasa Indonesia", appLanguageLabel(AppLanguage.INDONESIAN))
        assertEquals("es", appLanguageLocale(AppLanguage.SPANISH).language)
        assertEquals("zh", appLanguageLocale(AppLanguage.CHINESE).language)
        assertEquals(14, AppLanguage.entries.size)
    }

    @Test
    fun catalogTranslatesCommonUiAndFallsBackSafely() {
        assertEquals("Ajustes", ilyroTranslation(AppLanguage.SPANISH, "Settings"))
        assertEquals("设置", ilyroTranslation(AppLanguage.CHINESE, "Settings"))
        assertEquals("सेटिंग्स", ilyroTranslation(AppLanguage.HINDI, "Settings"))
        assertEquals("설정", ilyroTranslation(AppLanguage.KOREAN, "Settings"))
        assertTrue(ilyroTranslation(AppLanguage.SPANISH, "Untranslated release message") == null)
    }

    @Test
    fun systemKeepsExplicitLanguageSelections() {
        assertEquals(AppLanguage.SPANISH, resolveAppLanguage(AppLanguage.SPANISH))
        assertEquals(AppLanguage.INDONESIAN, resolveAppLanguage(AppLanguage.INDONESIAN))
    }
}
