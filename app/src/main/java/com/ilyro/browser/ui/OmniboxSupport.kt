package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class OmniboxSuggestionKind { HISTORY, SEARCH }

private data class OmniboxSuggestion(
    val kind: OmniboxSuggestionKind,
    val value: String,
    val title: String,
    val subtitle: String
)

/**
 * Activity-level touch routing for the focused home omnibox.
 *
 * Compose's AndroidComposeView owns touch dispatch, so attaching an Android OnTouchListener to
 * LocalView is not reliable for taps that are handled inside Compose. MainActivity forwards the
 * initial window ACTION_DOWN here instead. We keep the two interactive rectangles separate so a
 * tap on the empty home area dismisses search, while the field and suggestion list remain
 * fully interactive.
 */
internal object HomeOmniboxTouchCoordinator {
    private var owner: Any? = null
    private var active = false
    private var fieldBounds = Rect.Zero
    private var suggestionBounds = Rect.Zero
    private var dismissAction: (() -> Unit)? = null

    fun bind(ownerToken: Any, onDismiss: () -> Unit) {
        owner = ownerToken
        dismissAction = onDismiss
    }

    fun unbind(ownerToken: Any) {
        if (owner !== ownerToken) return
        owner = null
        active = false
        fieldBounds = Rect.Zero
        suggestionBounds = Rect.Zero
        dismissAction = null
    }

    fun setActive(ownerToken: Any, value: Boolean) {
        if (owner !== ownerToken) return
        active = value
        if (!value) suggestionBounds = Rect.Zero
    }

    fun setFieldBounds(ownerToken: Any, bounds: Rect) {
        if (owner === ownerToken) fieldBounds = bounds
    }

    fun setSuggestionBounds(ownerToken: Any, bounds: Rect) {
        if (owner === ownerToken) suggestionBounds = bounds
    }

    fun onWindowTouchDown(x: Float, y: Float) {
        if (!active || owner == null) return
        val point = Offset(x, y)
        val insideField = fieldBounds != Rect.Zero && fieldBounds.contains(point)
        val insideSuggestions = suggestionBounds != Rect.Zero && suggestionBounds.contains(point)
        if (!insideField && !insideSuggestions) {
            dismissAction?.invoke()
        }
    }
}

internal object AddressOmniboxTouchCoordinator {
    private var owner: Any? = null
    private var active = false
    private var fieldBounds = Rect.Zero
    private var dismissAction: (() -> Unit)? = null

    fun bind(ownerToken: Any, onDismiss: () -> Unit) {
        owner = ownerToken
        dismissAction = onDismiss
    }

    fun unbind(ownerToken: Any) {
        if (owner !== ownerToken) return
        owner = null
        active = false
        fieldBounds = Rect.Zero
        dismissAction = null
    }

    fun setActive(ownerToken: Any, value: Boolean) {
        if (owner !== ownerToken) return
        active = value
    }

    fun setFieldBounds(ownerToken: Any, bounds: Rect) {
        if (owner === ownerToken) fieldBounds = bounds
    }

    fun onWindowTouchDown(x: Float, y: Float) {
        if (!active || owner == null) return
        val point = Offset(x, y)
        val insideField = fieldBounds != Rect.Zero && fieldBounds.contains(point)
        if (!insideField) dismissAction?.invoke()
    }
}

@Composable
internal fun IlyroHomeOmnibox(
    value: String,
    onValueChange: (String) -> Unit,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    isPrivate: Boolean,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    customSearchEngine: CustomSearchEngine? = null,
    customSearchEngines: List<CustomSearchEngine> = emptyList(),
    onCustomSearchEngineChange: (CustomSearchEngine?) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val ownerToken = remember { Any() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(ownerToken, focusManager, keyboardController) {
        HomeOmniboxTouchCoordinator.bind(ownerToken) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        onDispose {
            HomeOmniboxTouchCoordinator.unbind(ownerToken)
        }
    }

    Surface(
        modifier = modifier
            .zIndex(if (focused) 20f else 0f),
        shape = RoundedCornerShape(IlyroVisualTokens.PillRadius),
        color = if (focused) MaterialTheme.colorScheme.background else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(IlyroVisualTokens.PillRadius))
                    .onGloballyPositioned {
                        HomeOmniboxTouchCoordinator.setFieldBounds(ownerToken, it.boundsInWindow())
                    }
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        HomeOmniboxTouchCoordinator.setActive(ownerToken, state.isFocused)
                        if (!state.isFocused) {
                            HomeOmniboxTouchCoordinator.setSuggestionBounds(ownerToken, Rect.Zero)
                        }
                    },
                singleLine = true,
                placeholder = {
                    Text(
                        tr(
                            "Search with ${customSearchEngine?.displayName ?: searchEngine.displayName} or enter address",
                            "Поиск через ${customSearchEngine?.displayName ?: searchEngine.displayName} или адрес"
                        )
                    )
                },
                leadingIcon = {
                    SearchEngineSelector(
                        engine = searchEngine,
                        customEngine = customSearchEngine,
                        customEngines = customSearchEngines,
                        onEngineSelected = onSearchEngineChange,
                        onCustomEngineSelected = onCustomSearchEngineChange
                    )
                },
                shape = RoundedCornerShape(IlyroVisualTokens.PillRadius),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (value.isNotBlank()) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onNavigate(value)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = if (isPrivate) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
                )
            )

            if (focused) {
                OmniboxSuggestionsMenu(
                    expanded = true,
                    query = value,
                    searchEngine = searchEngine,
                    history = history,
                    allowRemote = !isPrivate && customSearchEngine == null,
                    placeAbove = false,
                    onDismiss = { },
                    onSelect = { selected ->
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onNavigate(selected)
                    },
                    onBoundsChanged = {
                        HomeOmniboxTouchCoordinator.setSuggestionBounds(ownerToken, it)
                    }
                )
            }
        }
    }
}

@Composable
internal fun IlyroAddressOmnibox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    isPrivate: Boolean,
    compact: Boolean,
    fieldHeight: Dp,
    suggestionsAbove: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    customSearchEngine: CustomSearchEngine? = null
) {
    var focused by remember { mutableStateOf(false) }
    val ownerToken = remember { Any() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val secure = value.text.startsWith("https://", ignoreCase = true)

    val dismissEditing: () -> Unit = {
        focused = false
        onFocusChanged(false)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    DisposableEffect(ownerToken, focusManager, keyboardController, onFocusChanged) {
        AddressOmniboxTouchCoordinator.bind(ownerToken, dismissEditing)
        onDispose {
            AddressOmniboxTouchCoordinator.unbind(ownerToken)
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .clip(RoundedCornerShape(IlyroVisualTokens.PillRadius))
                .onGloballyPositioned {
                    AddressOmniboxTouchCoordinator.setFieldBounds(ownerToken, it.boundsInWindow())
                }
                .onFocusChanged { state ->
                    focused = state.isFocused
                    AddressOmniboxTouchCoordinator.setActive(ownerToken, state.isFocused)
                    onFocusChanged(state.isFocused)
                },
            singleLine = true,
            textStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            placeholder = {
                Text(
                    if (compact) tr("Search or URL", "Поиск или URL")
                    else tr("Search or enter address", "Поиск или адрес"),
                    maxLines = 1,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            },
            leadingIcon = {
                Surface(
                    modifier = Modifier.size(if (compact) 30.dp else 32.dp),
                    shape = RoundedCornerShape(50),
                    color = when {
                        isPrivate -> Color(0xFF8B5CF6).copy(alpha = 0.12f)
                        secure -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (secure) Icons.Rounded.Lock else Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 17.dp else 18.dp),
                            tint = when {
                                isPrivate -> Color(0xFFC4B5FD)
                                secure -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(IlyroVisualTokens.PillRadius),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    val target = value.text
                    dismissEditing()
                    if (target.isNotBlank()) onNavigate(target)
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                focusedBorderColor = if (isPrivate) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
            )
        )

        OmniboxSuggestionsMenu(
            expanded = focused,
            query = value.text,
            searchEngine = searchEngine,
            history = history,
            allowRemote = !isPrivate && customSearchEngine == null,
            placeAbove = suggestionsAbove,
            onDismiss = dismissEditing,
            onSelect = { selected ->
                dismissEditing()
                onNavigate(selected)
            }
        )
    }
}

@Composable
private fun SearchEngineSelector(
    engine: SearchEngine,
    customEngine: CustomSearchEngine?,
    customEngines: List<CustomSearchEngine>,
    onEngineSelected: (SearchEngine) -> Unit,
    onCustomEngineSelected: (CustomSearchEngine?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        if (customEngine != null) {
            CustomSearchEngineBadge(
                engine = customEngine,
                modifier = Modifier
                    .size(28.dp)
                    .pointerInput(engine, customEngine.id) {
                        detectTapGestures(
                            onTap = { expanded = true },
                            onLongPress = { expanded = true }
                        )
                    }
            )
        } else {
            SearchEngineBadge(
                engine = engine,
                modifier = Modifier
                    .size(28.dp)
                    .pointerInput(engine) {
                        detectTapGestures(
                            onTap = { expanded = true },
                            onLongPress = { expanded = true }
                        )
                    }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SearchEngine.entries.forEach { candidate ->
                val selected = customEngine == null && candidate == engine
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchEngineBadge(candidate, Modifier.size(26.dp))
                            Text(
                                text = candidate.displayName,
                                modifier = Modifier.padding(start = 10.dp),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onCustomEngineSelected(null)
                        onEngineSelected(candidate)
                    }
                )
            }

            customEngines.forEach { candidate ->
                val selected = customEngine?.id == candidate.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CustomSearchEngineBadge(candidate, Modifier.size(26.dp))
                            Text(
                                text = candidate.displayName,
                                modifier = Modifier.padding(start = 10.dp),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onCustomEngineSelected(candidate)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchEngineBadge(engine: SearchEngine, modifier: Modifier = Modifier) {
    var bitmap by remember(engine) { mutableStateOf(SearchEngineIconCache.peek(engine)) }

    LaunchedEffect(engine) {
        if (bitmap == null) bitmap = SearchEngineIconCache.loadOnce(engine)
    }

    val loaded = bitmap
    if (loaded != null) {
        ComposeImage(
            bitmap = loaded.asImageBitmap(),
            contentDescription = engine.displayName,
            modifier = modifier.padding(1.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        SearchEngineFallbackBadge(engine, modifier)
    }
}

@Composable
private fun CustomSearchEngineBadge(
    engine: CustomSearchEngine,
    modifier: Modifier = Modifier
) {
    val initial = engine.displayName.trim().firstOrNull()?.toString()?.uppercase() ?: "?"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchEngineFallbackBadge(engine: SearchEngine, modifier: Modifier = Modifier) {
    val (mark, background) = when (engine) {
        SearchEngine.GOOGLE -> "G" to Color(0xFF4285F4)
        SearchEngine.YANDEX -> "Y" to Color(0xFFFC3F1D)
        SearchEngine.DUCKDUCKGO -> "D" to Color(0xFFDE5833)
        SearchEngine.BRAVE -> "B" to Color(0xFFFB542B)
        SearchEngine.BING -> "b" to Color(0xFF008373)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = mark,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private object SearchEngineIconCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmaps = mutableMapOf<SearchEngine, Bitmap>()
    private val inFlight = mutableMapOf<SearchEngine, Deferred<Bitmap?>>()

    @Synchronized
    fun peek(engine: SearchEngine): Bitmap? = bitmaps[engine] ?: BrowserIconCache.peek(engine)?.also { bitmaps[engine] = it }

    suspend fun loadOnce(engine: SearchEngine): Bitmap? {
        val job = synchronized(this) {
            bitmaps[engine]?.let { return it }
            BrowserIconCache.peek(engine)?.let {
                bitmaps[engine] = it
                return it
            }
            inFlight[engine]?.let { return@synchronized it }
            scope.async {
                val loaded = BrowserIconCache.loadOnce(engine)
                synchronized(this@SearchEngineIconCache) {
                    if (loaded != null) bitmaps[engine] = loaded
                    inFlight.remove(engine)
                }
                loaded
            }.also { inFlight[engine] = it }
        }
        return job.await()
    }
}

@Composable
private fun OmniboxSuggestionsInline(
    expanded: Boolean,
    query: String,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    allowRemote: Boolean,
    onSelect: (String) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = rememberOmniboxSuggestions(
        expanded = expanded,
        query = query,
        searchEngine = searchEngine,
        history = history,
        allowRemote = allowRemote
    )

    LaunchedEffect(expanded, suggestions.isEmpty()) {
        if (!expanded || suggestions.isEmpty()) onBoundsChanged(Rect.Zero)
    }
    if (!expanded || suggestions.isEmpty()) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottom = with(density) { imeBottomPx.toDp() }
    val safeListHeight = (configuration.screenHeightDp.dp - imeBottom - 142.dp)
        .coerceIn(132.dp, 340.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) },
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = safeListHeight)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            suggestions.take(8).forEach { suggestion ->
                OmniboxSuggestionRow(
                    suggestion = suggestion,
                    onClick = { onSelect(suggestion.value) }
                )
            }
        }
    }
}

internal fun calculateOmniboxPopupY(
    placeAbove: Boolean,
    anchorTop: Int,
    anchorBottom: Int,
    popupHeight: Int,
    windowHeight: Int,
    imeBottom: Int,
    verticalGap: Int
): Int {
    // Popup is rendered in a separate window from the IME. windowHeight alone therefore includes
    // the keyboard area; cap the popup's bottom at the IME top so landscape keyboards cannot cover
    // the last suggestions.
    val safeBottom = (windowHeight - imeBottom).coerceIn(0, windowHeight)
    val maxY = (safeBottom - popupHeight).coerceAtLeast(0)
    val aboveY = anchorTop - popupHeight - verticalGap
    val belowY = anchorBottom + verticalGap
    val preferredY = if (placeAbove) aboveY else belowY
    val alternateY = if (placeAbove) belowY else aboveY
    return if (preferredY in 0..maxY) preferredY else alternateY.coerceIn(0, maxY)
}

private class OmniboxPopupPositionProvider(
    private val placeAbove: Boolean,
    private val verticalGapPx: Int,
    private val imeBottomPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = anchorBounds.left.coerceIn(0, maxX)
        val y = calculateOmniboxPopupY(
            placeAbove = placeAbove,
            anchorTop = anchorBounds.top,
            anchorBottom = anchorBounds.bottom,
            popupHeight = popupContentSize.height,
            windowHeight = windowSize.height,
            imeBottom = imeBottomPx,
            verticalGap = verticalGapPx
        )
        return IntOffset(x, y)
    }
}

@Composable
private fun OmniboxSuggestionsMenu(
    expanded: Boolean,
    query: String,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    allowRemote: Boolean,
    placeAbove: Boolean,
    onBoundsChanged: (Rect) -> Unit = { },
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val suggestions = rememberOmniboxSuggestions(
        expanded = expanded,
        query = query,
        searchEngine = searchEngine,
        history = history,
        allowRemote = allowRemote
    )
    LaunchedEffect(expanded, suggestions.isEmpty()) {
        if (!expanded || suggestions.isEmpty()) onBoundsChanged(Rect.Zero)
    }
    if (!expanded || suggestions.isEmpty()) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottom = with(density) { imeBottomPx.toDp() }
    val availableListHeight = (configuration.screenHeightDp.dp - imeBottom - 16.dp)
        .coerceAtLeast(1.dp)
    val maxListHeight = minOf(availableListHeight, 220.dp)
    val popupWidth = (configuration.screenWidthDp.dp * 0.72f)
        .coerceIn(260.dp, 560.dp)
    val gapPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(placeAbove, gapPx, imeBottomPx) {
        OmniboxPopupPositionProvider(placeAbove, gapPx, imeBottomPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        // The IME is a separate Android window. Do not treat keyboard taps as
        // outside-popup dismissals, otherwise every key press clears focus.
        onDismissRequest = { },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = popupWidth, max = popupWidth)
                .heightIn(max = maxListHeight)
                .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) },
            shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                suggestions.take(8).forEach { suggestion ->
                    OmniboxSuggestionRow(
                        suggestion = suggestion,
                        onClick = { onSelect(suggestion.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OmniboxSuggestionRow(
    suggestion: OmniboxSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (suggestion.kind == OmniboxSuggestionKind.HISTORY) {
                        Icons.Rounded.History
                    } else {
                        Icons.Rounded.Search
                    },
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = suggestion.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (suggestion.subtitle.isNotBlank()) {
                Text(
                    text = suggestion.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rememberOmniboxSuggestions(
    expanded: Boolean,
    query: String,
    searchEngine: SearchEngine,
    history: List<HistoryItem>,
    allowRemote: Boolean
): List<OmniboxSuggestion> {
    var remoteSuggestions by remember(searchEngine) { mutableStateOf<List<String>>(emptyList()) }
    val trimmed = query.trim()

    LaunchedEffect(expanded, trimmed, searchEngine, allowRemote) {
        remoteSuggestions = emptyList()
        if (!expanded || !allowRemote || trimmed.length < 2 || looksLikeNavigation(trimmed)) {
            return@LaunchedEffect
        }
        delay(180L)
        remoteSuggestions = fetchRemoteSuggestions(searchEngine, trimmed)
    }

    return buildOmniboxSuggestions(
        query = trimmed,
        engine = searchEngine,
        history = history,
        remote = remoteSuggestions
    )
}

private fun buildOmniboxSuggestions(
    query: String,
    engine: SearchEngine,
    history: List<HistoryItem>,
    remote: List<String>
): List<OmniboxSuggestion> {
    val normalized = query.lowercase()
    val uniqueHistory = history.distinctBy { it.url }.take(300)
    val historyMatches = if (query.isBlank()) {
        uniqueHistory.take(6)
    } else {
        uniqueHistory
            .mapNotNull { item ->
                val host = runCatching { Uri.parse(item.url).host.orEmpty().removePrefix("www.") }
                    .getOrDefault("")
                val title = item.title.lowercase()
                val url = item.url.lowercase()
                val hostLower = host.lowercase()
                if (normalized !in title && normalized !in url && normalized !in hostLower) return@mapNotNull null
                val score = when {
                    hostLower.startsWith(normalized) -> 400
                    title.startsWith(normalized) -> 300
                    url.startsWith(normalized) -> 250
                    normalized in hostLower -> 180
                    normalized in title -> 140
                    else -> 100
                } + (100 - uniqueHistory.indexOf(item).coerceAtMost(100))
                item to score
            }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    val result = mutableListOf<OmniboxSuggestion>()
    historyMatches.forEach { item ->
        val host = runCatching { Uri.parse(item.url).host.orEmpty().removePrefix("www.") }
            .getOrDefault("")
        result += OmniboxSuggestion(
            kind = OmniboxSuggestionKind.HISTORY,
            value = item.url,
            title = item.title.ifBlank { host.ifBlank { item.url } },
            subtitle = host.ifBlank { item.url }
        )
    }

    if (query.isNotBlank() && !looksLikeNavigation(query)) {
        remote.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() != normalized }
            .distinctBy { it.lowercase() }
            .take(5)
            .forEach { suggestion ->
                result += OmniboxSuggestion(
                    kind = OmniboxSuggestionKind.SEARCH,
                    value = suggestion,
                    title = suggestion,
                    subtitle = engine.displayName
                )
            }

        if (result.none { it.kind == OmniboxSuggestionKind.SEARCH && it.value.equals(query, true) }) {
            result += OmniboxSuggestion(
                kind = OmniboxSuggestionKind.SEARCH,
                value = query,
                title = query,
                subtitle = engine.displayName
            )
        }
    }

    return result.distinctBy { "${it.kind}|${it.value.lowercase()}" }.take(8)
}

private fun looksLikeNavigation(value: String): Boolean {
    val text = value.trim()
    if (text.startsWith("http://", true) || text.startsWith("https://", true)) return true
    return !text.contains(' ') && (text.contains('.') || text.startsWith("localhost", true))
}

private suspend fun fetchSearchEngineIcon(engine: SearchEngine): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val siteUrl = when (engine) {
            SearchEngine.GOOGLE -> "https://www.google.com/"
            SearchEngine.YANDEX -> "https://yandex.com/"
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/"
            SearchEngine.BRAVE -> "https://search.brave.com/"
            SearchEngine.BING -> "https://www.bing.com/"
        }
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val connection = (URL("https://www.google.com/s2/favicons?sz=128&domain_url=$encoded").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ILYRO/0.16 Android")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private suspend fun fetchRemoteSuggestions(engine: SearchEngine, query: String): List<String> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val endpoint = when (engine) {
            SearchEngine.GOOGLE -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            SearchEngine.YANDEX -> "https://suggest.yandex.ru/suggest-ya.cgi?v=4&part=$encoded"
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/ac/?q=$encoded&type=list"
            SearchEngine.BING -> "https://api.bing.com/osjson.aspx?query=$encoded"
            SearchEngine.BRAVE -> return@withContext emptyList()
        }

        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1600
                readTimeout = 1800
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ILYRO/0.16 Android")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching emptyList<String>()
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                parseSuggestionPayload(engine, payload)
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())
    }

private fun parseSuggestionPayload(engine: SearchEngine, payload: String): List<String> {
    val root = JSONArray(payload)
    if (engine == SearchEngine.DUCKDUCKGO) {
        return buildList {
            for (i in 0 until root.length()) {
                root.optJSONObject(i)?.optString("phrase")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
    val suggestions = root.optJSONArray(1) ?: return emptyList()
    return buildList {
        for (i in 0 until suggestions.length()) {
            suggestions.optString(i)?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
