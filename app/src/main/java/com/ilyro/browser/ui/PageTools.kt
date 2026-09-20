package com.ilyro.browser.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ilyro.browser.MainActivity
import com.ilyro.browser.R
import kotlinx.coroutines.runBlocking
import org.mozilla.geckoview.GeckoSession

@Composable
internal fun FindInPageDialog(
    session: GeckoSession,
    onDismiss: () -> Unit
) {
    val finder = remember(session) { session.finder }
    val focusRequester = remember(session) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by remember(session) { mutableStateOf("") }
    var matchCurrent by remember(session) { mutableStateOf(0) }
    var matchTotal by remember(session) { mutableStateOf(0) }
    var found by remember(session) { mutableStateOf<Boolean?>(null) }
    var searchFailed by remember(session) { mutableStateOf(false) }

    val noMatchesText = tr("No matches", "Совпадений нет")
    val searchFailedText = tr("Search failed", "Ошибка поиска")

    fun updateResult(result: GeckoSession.FinderResult?) {
        searchFailed = false
        found = result?.found
        matchCurrent = result?.current ?: 0
        matchTotal = result?.total ?: 0
    }

    fun find(flags: Int, text: String? = null) {
        finder.find(text, flags).accept(
            { result -> updateResult(result) },
            {
                searchFailed = true
                found = false
                matchCurrent = 0
                matchTotal = 0
            }
        )
    }

    fun dismiss() {
        finder.clear()
        focusManager.clearFocus()
        onDismiss()
    }

    LaunchedEffect(finder) {
        finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            finder.clear()
            found = null
            matchCurrent = 0
            matchTotal = 0
            searchFailed = false
        } else {
            found = null
            searchFailed = false
            find(GeckoSession.FINDER_FIND_FORWARD, query)
        }
    }

    DisposableEffect(finder) {
        onDispose { finder.clear() }
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        IlyroSystemBarAppearance()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text(tr("Find on this page", "Найти на этой странице")) },
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (query.isNotBlank()) {
                                        find(GeckoSession.FINDER_FIND_FORWARD, null)
                                    }
                                }
                            )
                        )
                        TextButton(onClick = ::dismiss) {
                            Text("✕")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusText = when {
                            query.isBlank() -> tr("Type text to search", "Введите текст для поиска")
                            searchFailed -> searchFailedText
                            found == false -> noMatchesText
                            found == true && matchTotal > 0 -> tr(
                                "$matchCurrent of $matchTotal",
                                "$matchCurrent из $matchTotal"
                            )
                            found == true -> tr("Match found", "Совпадение найдено")
                            else -> tr("Searching…", "Поиск…")
                        }
                        Text(
                            text = statusText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            enabled = query.isNotBlank(),
                            onClick = { find(GeckoSession.FINDER_FIND_BACKWARDS, null) }
                        ) {
                            Text(tr("Previous", "Назад"))
                        }
                        TextButton(
                            enabled = query.isNotBlank(),
                            onClick = { find(GeckoSession.FINDER_FIND_FORWARD, null) }
                        ) {
                            Text(tr("Next", "Далее"))
                        }
                    }
                }
            }
        }
    }
}

internal fun sharePage(
    context: Context,
    url: String,
    title: String,
    language: AppLanguage
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    val chooser = Intent.createChooser(
        sendIntent,
        tr(language, "Share page", "Поделиться страницей")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(chooser) }
        .onFailure {
            Toast.makeText(
                context,
                tr(language, "No app available for sharing", "Нет приложения для отправки"),
                Toast.LENGTH_SHORT
            ).show()
        }
}

internal fun pinPageShortcut(context: Context, url: String, title: String): Boolean {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!shortcutManager.isRequestPinShortcutSupported) return false

    val label = title.trim().ifBlank { Uri.parse(url).host ?: "ILYRO" }.take(40)
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(url)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun buildShortcut(siteIcon: Bitmap?): ShortcutInfo {
        val icon = siteIcon?.let(Icon::createWithBitmap)
            ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
        return ShortcutInfo.Builder(context, "ilyro-page-${url.hashCode()}")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
    }

    BrowserIconCache.peek(url)?.let { cachedIcon ->
        return shortcutManager.requestPinShortcut(buildShortcut(cachedIcon), null)
    }

    // A page can be pinned before its favicon has entered the cache. Fetch it off the UI thread,
    // then request the Android shortcut on the main thread so the first shortcut already carries
    // the site's own icon instead of permanently falling back to the ILYRO app icon.
    val mainHandler = Handler(Looper.getMainLooper())
    Thread({
        val loadedIcon = runCatching {
            runBlocking { BrowserIconCache.loadOnce(url) }
        }.getOrNull()
        mainHandler.post {
            runCatching {
                shortcutManager.requestPinShortcut(buildShortcut(loadedIcon), null)
            }
        }
    }, "ilyro-shortcut-icon").start()

    return true
}
