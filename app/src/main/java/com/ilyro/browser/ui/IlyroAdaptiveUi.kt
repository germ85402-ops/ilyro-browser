package com.ilyro.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One adaptive layout model for the whole ILYRO UI.
 *
 * Compact: phones and narrow foldables (< 600dp)
 * Medium: small tablets / unfolded devices (600..839dp)
 * Expanded: large tablets and desktop-like windows (>= 840dp)
 *
 * Keeping these thresholds in one place prevents individual screens from slowly
 * developing their own incompatible ideas of what a phone or tablet is.
 */
internal enum class IlyroWindowClass { COMPACT, MEDIUM, EXPANDED }

internal val LocalIlyroUiDensity = staticCompositionLocalOf { UiDensity.COMPACT }

@Immutable
internal data class IlyroLayoutMetrics(
    val windowClass: IlyroWindowClass,
    val widthDp: Int,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionGap: Dp,
    val itemGap: Dp,
    val headerButtonVisualSize: Dp,
    val touchTarget: Dp,
    val searchHeight: Dp,
    val contentMaxWidth: Dp
) {
    val isCompact: Boolean get() = windowClass == IlyroWindowClass.COMPACT
    val isMedium: Boolean get() = windowClass == IlyroWindowClass.MEDIUM
    val isExpanded: Boolean get() = windowClass == IlyroWindowClass.EXPANDED
    val isNarrowPhone: Boolean get() = widthDp < 360
    val isSmallPhone: Boolean get() = widthDp < 400
}

@Composable
internal fun rememberIlyroLayoutMetrics(): IlyroLayoutMetrics {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalIlyroUiDensity.current
    val windowClass = when {
        width < 600 -> IlyroWindowClass.COMPACT
        width < 840 -> IlyroWindowClass.MEDIUM
        else -> IlyroWindowClass.EXPANDED
    }

    return when (windowClass) {
        IlyroWindowClass.COMPACT -> when (density) {
            UiDensity.COMPACT -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = if (width < 360) 10.dp else 14.dp,
                verticalPadding = 8.dp,
                sectionGap = 10.dp,
                itemGap = 6.dp,
                headerButtonVisualSize = 38.dp,
                touchTarget = 48.dp,
                searchHeight = 52.dp,
                contentMaxWidth = 720.dp
            )
            UiDensity.STANDARD -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = if (width < 360) 12.dp else 16.dp,
                verticalPadding = 12.dp,
                sectionGap = 14.dp,
                itemGap = 8.dp,
                headerButtonVisualSize = 42.dp,
                touchTarget = 48.dp,
                searchHeight = 52.dp,
                contentMaxWidth = 720.dp
            )
            UiDensity.COMFORTABLE -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = if (width < 360) 14.dp else 18.dp,
                verticalPadding = 14.dp,
                sectionGap = 18.dp,
                itemGap = 10.dp,
                headerButtonVisualSize = 44.dp,
                touchTarget = 48.dp,
                searchHeight = 54.dp,
                contentMaxWidth = 720.dp
            )
        }
        IlyroWindowClass.MEDIUM -> when (density) {
            UiDensity.COMPACT -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 18.dp,
                verticalPadding = 12.dp,
                sectionGap = 12.dp,
                itemGap = 8.dp,
                headerButtonVisualSize = 40.dp,
                touchTarget = 48.dp,
                searchHeight = 52.dp,
                contentMaxWidth = 820.dp
            )
            UiDensity.STANDARD -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 22.dp,
                verticalPadding = 16.dp,
                sectionGap = 16.dp,
                itemGap = 10.dp,
                headerButtonVisualSize = 44.dp,
                touchTarget = 48.dp,
                searchHeight = 54.dp,
                contentMaxWidth = 920.dp
            )
            UiDensity.COMFORTABLE -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 26.dp,
                verticalPadding = 18.dp,
                sectionGap = 20.dp,
                itemGap = 12.dp,
                headerButtonVisualSize = 46.dp,
                touchTarget = 48.dp,
                searchHeight = 56.dp,
                contentMaxWidth = 960.dp
            )
        }
        IlyroWindowClass.EXPANDED -> when (density) {
            UiDensity.COMPACT -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 22.dp,
                verticalPadding = 14.dp,
                sectionGap = 14.dp,
                itemGap = 8.dp,
                headerButtonVisualSize = 40.dp,
                touchTarget = 48.dp,
                searchHeight = 52.dp,
                contentMaxWidth = 860.dp
            )
            UiDensity.STANDARD -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 28.dp,
                verticalPadding = 18.dp,
                sectionGap = 18.dp,
                itemGap = 12.dp,
                headerButtonVisualSize = 44.dp,
                touchTarget = 48.dp,
                searchHeight = 54.dp,
                contentMaxWidth = 1040.dp
            )
            UiDensity.COMFORTABLE -> IlyroLayoutMetrics(
                windowClass = windowClass,
                widthDp = width,
                horizontalPadding = 32.dp,
                verticalPadding = 22.dp,
                sectionGap = 22.dp,
                itemGap = 14.dp,
                headerButtonVisualSize = 46.dp,
                touchTarget = 48.dp,
                searchHeight = 56.dp,
                contentMaxWidth = 1080.dp
            )
        }
    }
}

/** Shared header for Tabs, History, Downloads, Settings and other service screens. */
@Composable
internal fun IlyroScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = metrics.verticalPadding, bottom = if (dense) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (dense) 7.dp else 10.dp)
    ) {
        IlyroBackButton(onClick = onBack)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = when {
                    dense -> MaterialTheme.typography.titleLarge
                    metrics.isCompact -> MaterialTheme.typography.headlineSmall
                    else -> MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        actions()
    }
}

/** Visual button can stay compact while the physical hit target remains 48dp. */
@Composable
internal fun IlyroBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberIlyroLayoutMetrics()
    Box(
        modifier = modifier.size(metrics.touchTarget),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(metrics.headerButtonVisualSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = tr("Back", "Назад"),
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Shared low-noise search field used by utility screens. */
@Composable
internal fun IlyroSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val metrics = rememberIlyroLayoutMetrics()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.searchHeight),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = tr("Clear search", "Очистить поиск"),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        } else null,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        ),
        minLines = 1
    )
}

/**
 * Centers service-screen content on tablets while preserving edge-to-edge efficiency
 * on phones. The same wrapper can be used by every full-screen utility section.
 */
@Composable
internal fun IlyroAdaptiveContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = metrics.contentMaxWidth)
                .padding(horizontal = metrics.horizontalPadding)
        ) {
            content()
        }
    }
}
