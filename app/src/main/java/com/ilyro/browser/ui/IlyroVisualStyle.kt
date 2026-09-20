package com.ilyro.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * ILYRO Visual 4 tokens.
 *
 * These are intentionally semantic rather than screen-specific. Screens may adapt
 * their layout through [rememberIlyroLayoutMetrics], but shape, spacing, surface
 * hierarchy and motion should come from this single visual language.
 */
internal object IlyroVisualTokens {
    // Shape hierarchy: controls -> cards -> large sections -> pills.
    val SmallRadius = 12.dp
    val ControlRadius = 16.dp
    val CardRadius = 18.dp
    val LargeRadius = 24.dp
    val PillRadius = 20.dp

    // Shared rhythm.
    val TightGap = 6.dp
    val CompactGap = 8.dp
    val StandardGap = 10.dp
    val SectionGap = 14.dp
    val ScreenPadding = 14.dp

    // Controls. Keep visual size and touch target separate on compact phones.
    val SmallIconSize = 18.dp
    val IconSize = 20.dp
    val LargeIconSize = 22.dp
    val IconContainerSize = 36.dp
    val MinimumTouchTarget = 48.dp

    // Surface language. Avoid piling strong borders/elevation on top of each other.
    const val SubtleSurfaceAlpha = 0.52f
    const val StrongSurfaceAlpha = 0.92f
    const val SubtleBorderAlpha = 0.18f
    const val SelectedBorderAlpha = 0.24f

    // One motion vocabulary for service UI.
    const val MotionFastMs = 160
    const val MotionStandardMs = 220

    val LightCanvasTop = Color(0xFFFCFDFF)
    val LightCanvasBottom = Color(0xFFF4F7FB)
    val DarkCanvasTop = Color(0xFF151922)
    val DarkCanvasBottom = Color(0xFF0D1016)
}

@Composable
internal fun IlyroServiceCanvasColors(): Pair<Color, Color> {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (darkTheme) {
        IlyroVisualTokens.DarkCanvasTop to IlyroVisualTokens.DarkCanvasBottom
    } else {
        IlyroVisualTokens.LightCanvasTop to IlyroVisualTokens.LightCanvasBottom
    }
}

/** Shared full-screen background for every utility section. */
@Composable
internal fun IlyroServiceBackdrop(
    modifier: Modifier = Modifier
) {
    val (top, bottom) = IlyroServiceCanvasColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(top, bottom)
                )
            )
    )
}

/** Backwards-compatible entry point for screens that already receive settings. */
@Composable
internal fun IlyroWallpaperBackdrop(
    settings: BrowserSettings,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val retainedSettings = settings
    IlyroServiceBackdrop(modifier = modifier)
}
