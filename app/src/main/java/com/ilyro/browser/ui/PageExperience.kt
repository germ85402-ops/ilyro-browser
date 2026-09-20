@file:OptIn(org.mozilla.geckoview.ExperimentalGeckoViewApi::class)

package com.ilyro.browser.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.PageExtractionController
import org.mozilla.geckoview.TranslationsController

internal data class ReaderArticle(
    val url: String,
    val title: String,
    val language: String?,
    val text: String
)

internal enum class ReaderColorScheme {
    LIGHT,
    SEPIA,
    DARK
}

internal fun isHttpPage(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

private fun normalizeLanguageTag(value: String?): String? = value
    ?.trim()
    ?.substringBefore('-')
    ?.substringBefore('_')
    ?.lowercase()
    ?.takeIf { it.length in 2..3 }

@org.mozilla.geckoview.ExperimentalGeckoViewApi
internal fun loadReaderArticle(
    session: GeckoSession,
    url: String,
    title: String,
    pageLanguage: String?,
    onSuccess: (ReaderArticle) -> Unit,
    onError: (String) -> Unit
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val options = PageExtractionController.ContentParams(
        true,  // remove boilerplate with Gecko's reader extraction
        false  // keep markdown annotations; existing cleanup below handles them
    )
    session.sessionPageExtractor.getPageContent(options).accept(
        { content ->
            val cleaned = content
                ?.replace(Regex("""(?m)^#{1,6}\s+"""), "")
                ?.replace(Regex("""(?m)^>\s?"""), "")
                ?.replace(Regex("""(?m)^[-*+]\s+"""), "• ")
                ?.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
                ?.replace(Regex("""__([^_]+)__"""), "$1")
                ?.replace(Regex("""`([^`]+)`"""), "$1")
                ?.replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")
                ?.trim()
                .orEmpty()
            mainHandler.post {
                if (cleaned.isBlank()) {
                    onError("Reader mode could not extract this page")
                } else {
                    onSuccess(
                        ReaderArticle(
                            url = url,
                            title = title.ifBlank { Uri.parse(url).host.orEmpty() },
                            language = pageLanguage?.takeIf { it.isNotBlank() },
                            text = cleaned
                        )
                    )
                }
            }
        },
        { error ->
            mainHandler.post {
                onError(error?.message ?: "Reader mode could not extract this page")
            }
        }
    )
}

private fun languageName(code: String?, language: AppLanguage): String {
    val normalized = normalizeLanguageTag(code)
    return when (normalized) {
        "en" -> tr(language, "English", "Английский")
        "ru" -> tr(language, "Russian", "Русский")
        "de" -> tr(language, "German", "Немецкий")
        "fr" -> tr(language, "French", "Французский")
        "es" -> tr(language, "Spanish", "Испанский")
        "it" -> tr(language, "Italian", "Итальянский")
        "pt" -> tr(language, "Portuguese", "Португальский")
        "pl" -> tr(language, "Polish", "Польский")
        "uk" -> tr(language, "Ukrainian", "Украинский")
        "nl" -> tr(language, "Dutch", "Нидерландский")
        else -> code?.uppercase()?.takeIf { it.isNotBlank() }
            ?: tr(language, "Detecting…", "Определение…")
    }
}

@Composable
internal fun TranslationDialog(
    session: GeckoSession,
    state: TranslationsController.SessionTranslation.TranslationState?,
    pageLanguage: String?,
    translationReady: Boolean,
    pageLoading: Boolean,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val detected = state?.detectedLanguages
    // Use page metadata only for the UI fallback. Gecko Translate itself must receive
    // the exact document tag it detected (for example en-US, pt-BR), not a shortened tag.
    val detectedSourceTag = detected?.docLangTag?.takeIf { it.isNotBlank() }
    val displaySourceTag = detectedSourceTag
        ?: pageLanguage?.takeIf { it.isNotBlank() }
    val sourceCode = normalizeLanguageTag(displaySourceTag)
    val preferredTarget = if (language == AppLanguage.RUSSIAN) "ru" else "en"
    val defaultTarget = if (sourceCode == preferredTarget) {
        if (preferredTarget == "ru") "en" else "ru"
    } else {
        preferredTarget
    }
    var targetTag by remember(session, sourceCode, language) { mutableStateOf(defaultTarget) }
    var working by remember(session) { mutableStateOf(false) }
    var localError by remember(session) { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val detectedSupported = detected?.isDocLangTagSupported == true
    val translated = state?.hasVisibleChange == true
    val stateError = state?.error?.takeIf { it.isNotBlank() }
    val sourceKnown = !sourceCode.isNullOrBlank()
    val geckoSourceReady = detectedSupported && !detectedSourceTag.isNullOrBlank()
    // Starting while the document is still loading makes late DOM nodes miss the first pass.
    // Wait for both page completion and Gecko's own supported-language detection.
    val canTranslate = geckoSourceReady &&
        sourceCode != targetTag &&
        !pageLoading &&
        !working

    fun translate() {
        val from = detectedSourceTag ?: return
        val coordinator = session.sessionTranslation
        if (coordinator == null) {
            localError = tr(
                language,
                "Translation is unavailable for this page",
                "Перевод недоступен для этой страницы"
            )
            return
        }
        working = true
        localError = null
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
            .downloadModel(true)
            .build()
        coordinator.translate(from, targetTag, options).accept(
            { mainHandler.post { working = false } },
            { error ->
                mainHandler.post {
                    working = false
                    localError = error?.message ?: tr(
                        language,
                        "Translation failed",
                        "Не удалось перевести страницу"
                    )
                }
            }
        )
    }

    fun restoreOriginal() {
        val coordinator = session.sessionTranslation ?: return
        working = true
        localError = null
        coordinator.restoreOriginalPage().accept(
            { mainHandler.post { working = false } },
            { error ->
                mainHandler.post {
                    working = false
                    localError = error?.message ?: tr(
                        language,
                        "Couldn't restore the original",
                        "Не удалось вернуть оригинал"
                    )
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(language, "Translate page", "Перевести страницу")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${languageName(displaySourceTag, language)} → ${languageName(targetTag, language)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                when {
                    pageLoading -> Text(
                        text = tr(
                            language,
                            "Finish loading the page before translating it so the whole document is included.",
                            "Дождитесь полной загрузки страницы, чтобы перевод охватил весь документ."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !sourceKnown || (!geckoSourceReady && translationReady) -> Text(
                        text = tr(
                            language,
                            "Gecko is still preparing page translation. Try again in a moment.",
                            "Gecko ещё подготавливает перевод страницы. Попробуйте через мгновение."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    !geckoSourceReady -> Text(
                        text = tr(
                            language,
                            "Translation isn't available for this language pair yet.",
                            "Перевод для этой пары языков пока недоступен."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                        onClick = { targetTag = "ru" }
                    ) {
                        Text(if (targetTag == "ru") "✓ Русский" else "Русский")
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                        onClick = { targetTag = "en" }
                    ) {
                        Text(if (targetTag == "en") "✓ English" else "English")
                    }
                }
                Text(
                    text = tr(
                        language,
                        "Translation runs locally in Gecko. The required language model is downloaded automatically the first time.",
                        "Перевод выполняется локально в Gecko. Нужная языковая модель автоматически загрузится при первом использовании."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                (localError ?: stateError)?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (translated) {
                    TextButton(enabled = !working, onClick = ::restoreOriginal) {
                        Text(tr(language, "Original", "Оригинал"))
                    }
                }
                TextButton(enabled = canTranslate, onClick = ::translate) {
                    Text(
                        if (working) tr(language, "Translating…", "Перевод…")
                        else tr(language, "Translate", "Перевести")
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(language, "Close", "Закрыть"))
            }
        }
    )
}

@Composable
internal fun ReaderModeView(
    article: ReaderArticle,
    language: AppLanguage,
    darkTheme: Boolean,
    onExit: () -> Unit
) {
    var fontSize by remember(article.url) { mutableIntStateOf(19) }
    var serif by remember(article.url) { mutableStateOf(true) }
    var scheme by remember(article.url, darkTheme) {
        mutableStateOf(if (darkTheme) ReaderColorScheme.DARK else ReaderColorScheme.LIGHT)
    }

    val background = when (scheme) {
        ReaderColorScheme.LIGHT -> Color(0xFFFDFDFB)
        ReaderColorScheme.SEPIA -> Color(0xFFF4ECD8)
        ReaderColorScheme.DARK -> Color(0xFF171717)
    }
    val foreground = when (scheme) {
        ReaderColorScheme.LIGHT -> Color(0xFF202124)
        ReaderColorScheme.SEPIA -> Color(0xFF3F3528)
        ReaderColorScheme.DARK -> Color(0xFFE8E8E8)
    }
    val secondary = foreground.copy(alpha = 0.66f)
    val fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val host = remember(article.url) {
        runCatching { Uri.parse(article.url).host.orEmpty() }.getOrDefault("")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background,
        contentColor = foreground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = onExit) {
                    Text(tr(language, "Page", "Страница"), color = foreground)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = fontSize > 14,
                    onClick = { fontSize = (fontSize - 1).coerceAtLeast(14) }
                ) { Text("A−", color = foreground) }
                TextButton(
                    enabled = fontSize < 30,
                    onClick = { fontSize = (fontSize + 1).coerceAtMost(30) }
                ) { Text("A+", color = foreground) }
                TextButton(onClick = { serif = !serif }) {
                    Text(if (serif) "Serif" else "Sans", color = foreground)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { scheme = ReaderColorScheme.LIGHT }) {
                    Text(if (scheme == ReaderColorScheme.LIGHT) "● Light" else "Light", color = foreground)
                }
                TextButton(onClick = { scheme = ReaderColorScheme.SEPIA }) {
                    Text(if (scheme == ReaderColorScheme.SEPIA) "● Sepia" else "Sepia", color = foreground)
                }
                TextButton(onClick = { scheme = ReaderColorScheme.DARK }) {
                    Text(if (scheme == ReaderColorScheme.DARK) "● Dark" else "Dark", color = foreground)
                }
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (host.isNotBlank()) {
                        Text(
                            text = host,
                            color = secondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text(
                        text = article.title,
                        color = foreground,
                        fontSize = (fontSize + 8).sp,
                        lineHeight = (fontSize + 12).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                    article.language?.let { code ->
                        Text(
                            text = languageName(code, language),
                            color = secondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    HorizontalDivider(color = foreground.copy(alpha = 0.12f))
                    Text(
                        text = article.text,
                        color = foreground,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.55f).sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}
