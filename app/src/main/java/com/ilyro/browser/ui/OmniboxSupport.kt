package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
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
        if (!insideField && !insideSuggestions) dismissAction?.invoke()
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
    onCustomSearchEngineChange: (CustomSearchEngine?) -> Unit = {},
    bookmarks: List<BookmarkItem> = emptyList(),
    quickLinks: List<QuickLink> = emptyList(),
    onlineSearchSuggestionsEnabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val ownerToken = remember { Any() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldBounds by remember { mutableStateOf(Rect.Zero) }
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
                        fieldBounds = it.boundsInWindow()
                        HomeOmniboxTouchCoordinator.setFieldBounds(ownerToken, fieldBounds)
                    }
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        onFocusChanged(state.isFocused)
                        HomeOmniboxTouchCoordinator.setActive(ownerToken, state.isFocused)
                        if (!state.isFocused) {
                            fieldBounds = Rect.Zero
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
                    focusedBorderColor = if (isPrivate) privateModeAccent() else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
                )
            )

            OmniboxSuggestionsMenu(
                expanded = focused,
                query = value,
                searchEngine = searchEngine,
                history = history,
                bookmarks = bookmarks,
                quickLinks = quickLinks,
                allowRemote = onlineSearchSuggestionsEnabled && !isPrivate && customSearchEngine == null,
                placeAbove = false,
                anchorBounds = fieldBounds,
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
    customSearchEngine: CustomSearchEngine? = null,
    bookmarks: List<BookmarkItem> = emptyList(),
    quickLinks: List<QuickLink> = emptyList(),
    onlineSearchSuggestionsEnabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val ownerToken = remember { Any() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val secure = value.text.startsWith("https://", ignoreCase = true)
    var fieldBounds by remember { mutableStateOf(Rect.Zero) }
    val suggestionQuery = if (
        focused && value.text.isNotBlank() &&
        value.selection.start == 0 && value.selection.end == value.text.length
    ) {
        ""
    } else {
        value.text
    }

    val dismissEditing: () -> Unit = {
        focused = false
        onFocusChanged(false)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        AddressOmniboxTouchCoordinator.setSuggestionBounds(ownerToken, Rect.Zero)
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
                    fieldBounds = it.boundsInWindow()
                    AddressOmniboxTouchCoordinator.setFieldBounds(ownerToken, fieldBounds)
                }
                .onFocusChanged { state ->
                    focused = state.isFocused
                    AddressOmniboxTouchCoordinator.setActive(ownerToken, state.isFocused)
                    if (!state.isFocused) {
                        fieldBounds = Rect.Zero
                        AddressOmniboxTouchCoordinator.setSuggestionBounds(ownerToken, Rect.Zero)
                    }
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
                        isPrivate -> privateModeAccent().copy(alpha = 0.12f)
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
                                isPrivate -> privateModeAccent()
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
                focusedBorderColor = if (isPrivate) privateModeAccent() else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
            )
        )

        OmniboxSuggestionsMenu(
            expanded = focused,
            query = suggestionQuery,
            searchEngine = searchEngine,
            history = history,
            bookmarks = bookmarks,
            quickLinks = quickLinks,
            allowRemote = onlineSearchSuggestionsEnabled && !isPrivate && customSearchEngine == null,
            placeAbove = suggestionsAbove,
            anchorBounds = fieldBounds,
            onBoundsChanged = {
                AddressOmniboxTouchCoordinator.setSuggestionBounds(ownerToken, it)
            },
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

internal fun calculateOmniboxPopupAvailableHeight(
    placeAbove: Boolean,
    anchorTop: Int,
    anchorBottom: Int,
    windowHeight: Int,
    imeBottom: Int,
    verticalGap: Int
): Int {
    val safeBottom = (windowHeight - imeBottom).coerceIn(0, windowHeight)
    return if (placeAbove) {
        (anchorTop - verticalGap).coerceAtLeast(0)
    } else {
        (safeBottom - anchorBottom - verticalGap).coerceAtLeast(0)
    }
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
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceIn(0, maxX)
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
    bookmarks: List<BookmarkItem>,
    quickLinks: List<QuickLink>,
    allowRemote: Boolean,
    placeAbove: Boolean,
    anchorBounds: Rect,
    onBoundsChanged: (Rect) -> Unit = { },
    onSelect: (String) -> Unit
) {
    val suggestions = rememberOmniboxSuggestions(
        expanded = expanded,
        query = query,
        searchEngine = searchEngine,
        history = history,
        bookmarks = bookmarks,
        quickLinks = quickLinks,
        allowRemote = allowRemote
    )
    LaunchedEffect(expanded, suggestions.isEmpty()) {
        if (!expanded || suggestions.isEmpty()) onBoundsChanged(Rect.Zero)
    }
    if (!expanded || suggestions.isEmpty()) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val gapPx = with(density) { 8.dp.roundToPx() }
    val windowHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val availableHeightPx = calculateOmniboxPopupAvailableHeight(
        placeAbove = placeAbove,
        anchorTop = anchorBounds.top.toInt(),
        anchorBottom = anchorBounds.bottom.toInt(),
        windowHeight = windowHeightPx,
        imeBottom = imeBottomPx,
        verticalGap = gapPx
    )
    val maxPanelHeight = with(density) { availableHeightPx.coerceAtLeast(1).toDp() }
    val panelHeight by animateDpAsState(
        targetValue = if (expanded && suggestions.isNotEmpty()) maxPanelHeight else 0.dp,
        animationSpec = tween(
            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
            easing = IlyroVisualTokens.MotionEnterEasing
        ),
        label = "omnibox-suggestions-height"
    )
    val popupWidth = (configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp)
    val positionProvider = remember(placeAbove, gapPx, imeBottomPx) {
        OmniboxPopupPositionProvider(placeAbove, gapPx, imeBottomPx)
    }

    if (panelHeight <= 0.dp || anchorBounds == Rect.Zero) return

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
                .width(popupWidth)
                .height(panelHeight)
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
                    .fillMaxSize()
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
    val subtitle = suggestion.subtitle.ifBlank {
        if (suggestion.kind == OmniboxSuggestionKind.ADDRESS) {
            tr("Go to address", "Перейти по адресу")
        } else {
            ""
        }
    }
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
                    imageVector = when (suggestion.kind) {
                        OmniboxSuggestionKind.HISTORY -> Icons.Rounded.History
                        OmniboxSuggestionKind.BOOKMARK -> Icons.Rounded.Bookmark
                        OmniboxSuggestionKind.QUICK_LINK -> Icons.Rounded.Link
                        OmniboxSuggestionKind.ADDRESS -> Icons.Rounded.Link
                        OmniboxSuggestionKind.SEARCH -> Icons.Rounded.Search
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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
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
    bookmarks: List<BookmarkItem>,
    quickLinks: List<QuickLink>,
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
        bookmarks = bookmarks,
        quickLinks = quickLinks,
        remote = remoteSuggestions
    )
}

private suspend fun fetchSearchEngineIcon(engine: SearchEngine): Bitmap? =
    withContext(Dispatchers.IO) {
        val siteUrl = when (engine) {
            SearchEngine.GOOGLE -> "https://www.google.com/"
            SearchEngine.YANDEX -> "https://yandex.com/"
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/"
            SearchEngine.BRAVE -> "https://search.brave.com/"
            SearchEngine.BING -> "https://www.bing.com/"
        }
        SiteIconFetcher.fetch(siteUrl)
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
