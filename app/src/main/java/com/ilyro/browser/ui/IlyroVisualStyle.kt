package com.ilyro.browser.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared system sans-serif scale for browser chrome and utility screens. */
internal val IlyroTypography: Typography = Typography().let { base ->
    fun TextStyle.systemSans() = copy(fontFamily = FontFamily.SansSerif)

    base.copy(
        displayLarge = base.displayLarge.systemSans(),
        displayMedium = base.displayMedium.systemSans(),
        displaySmall = base.displaySmall.systemSans(),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            lineHeight = 18.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

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

    // Shared motion vocabulary. Keep entry, exit, and control feedback distinct.
    const val MotionMicroMs = 120
    const val MotionFastMs = 160
    const val MotionStandardMs = 220
    const val MotionScreenMs = 240

    val MotionEnterEasing: Easing = FastOutSlowInEasing
    val MotionExitEasing: Easing = FastOutLinearInEasing

    /** Respect Android's global animation switch, including accessibility "Remove animations". */
    fun motionDuration(durationMillis: Int): Int =
        if (ValueAnimator.areAnimatorsEnabled()) durationMillis else 0

    /** Keep choreography delays in step with the corresponding Compose animation. */
    fun motionDelay(durationMillis: Int): Long = motionDuration(durationMillis).toLong()

    fun systemMotionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    val LightCanvasTop = Color(0xFFFCFDFF)
    val LightCanvasBottom = Color(0xFFF4F7FB)
    val DarkCanvasTop = Color(0xFF151C27)
    val DarkCanvasBottom = Color(0xFF0C121A)
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
