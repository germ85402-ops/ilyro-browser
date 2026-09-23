package com.ilyro.browser.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun OnboardingScreen(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit,
    onFinish: (Set<String>) -> Unit,
    onSkip: () -> Unit = { onFinish(emptySet()) }
) {
    val metrics = rememberIlyroLayoutMetrics()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 4

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingTopBar(step = step, totalSteps = totalSteps)

            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionStandardMs),
                            easing = IlyroVisualTokens.MotionEnterEasing
                        ),
                        initialOffsetX = { width -> if (forward) width / 6 else -width / 6 }
                    ) + fadeIn(
                        tween(
                            durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                            easing = IlyroVisualTokens.MotionEnterEasing
                        )
                    )) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                                easing = IlyroVisualTokens.MotionExitEasing
                            ),
                            targetOffsetX = { width -> if (forward) -width / 7 else width / 7 }
                        ) + fadeOut(
                            tween(
                                durationMillis = IlyroVisualTokens.motionDuration(IlyroVisualTokens.MotionFastMs),
                                easing = IlyroVisualTokens.MotionExitEasing
                            )
                        ))
                },
                label = "ilyro-onboarding-step"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = metrics.horizontalPadding,
                            vertical = if (metrics.isNarrowPhone) 10.dp else 16.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                    ) {
                        when (currentStep) {
                            0 -> AppearanceSetupStep(settings, onSettingsChange)
                            1 -> BrowserSetupStep(settings, onSettingsChange)
                            2 -> ExtensionsStep(
                                settings = settings,
                                onSettingsChange = onSettingsChange
                            )
                            else -> FinishStep(settings)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            OnboardingBottomBar(
                step = step,
                totalSteps = totalSteps,
                onBack = { if (step > 0) step-- },
                onContinue = {
                    if (step < totalSteps - 1) step++
                    else onFinish(emptySet())
                },
                onSkip = onSkip
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(step: Int, totalSteps: Int) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = if (dense) 6.dp else if (metrics.isNarrowPhone) 8.dp else 12.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (dense) 38.dp else if (metrics.isNarrowPhone) 42.dp else 48.dp),
                shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = IlyroVisualTokens.SubtleBorderAlpha
                    )
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppBrandIcon(
                        modifier = Modifier.size(if (dense) 30.dp else if (metrics.isNarrowPhone) 34.dp else 38.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = if (dense) 9.dp else 11.dp)
                    .weight(1f)
            ) {
                Text(
                    "ILYRO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp
                )
                if (!metrics.isNarrowPhone) {
                    Text(
                        tr("Make it yours", "Настройте под себя"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${step + 1}/$totalSteps",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(top = if (dense) 6.dp else if (metrics.isNarrowPhone) 8.dp else 12.dp, bottom = if (dense) 4.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepSectionLabel(step),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!metrics.isNarrowPhone) {
                Text(
                    text = tr(
                        "Step ${step + 1} of $totalSteps",
                        "Шаг ${step + 1} из $totalSteps"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { (step + 1f) / totalSteps.toFloat() },
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    step: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = if (dense) 7.dp else if (metrics.isNarrowPhone) 9.dp else 12.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (dense) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    TextButton(
                        modifier = Modifier
                            .width(if (dense) 78.dp else if (metrics.isNarrowPhone) 84.dp else 104.dp)
                            .height(if (dense) 46.dp else 50.dp),
                        onClick = onBack
                    ) {
                        Text(tr("Back", "Назад"))
                    }
                }

                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (dense) 46.dp else 50.dp),
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
                    onClick = onContinue
                ) {
                    Text(
                        if (step == totalSteps - 1) {
                            tr("Start ILYRO", "Начать работу")
                        } else {
                            tr("Continue", "Далее")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            TextButton(
                modifier = Modifier.padding(top = if (dense) 0.dp else 2.dp),
                onClick = onSkip
            ) {
                Text(
                    tr("Skip setup", "Пропустить настройку"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!metrics.isNarrowPhone) {
                Text(
                    text = tr(
                        "Everything can be changed later in Settings",
                        "Все параметры можно изменить позже в настройках"
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppearanceSetupStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    StepTitle(
        tr("Appearance", "Оформление"),
        tr(
            "Choose the theme and start-page background. You can change both later.",
            "Выберите тему и фон стартовой страницы. Всё можно изменить позже."
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
    ThemeStep(settings, onSettingsChange, showTitle = false)
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        tr("Start-page background", "Фон стартовой страницы"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    WallpaperStep(settings, onSettingsChange, showTitle = false)
}

@Composable
private fun BrowserSetupStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    StepTitle(
        tr("Browser controls", "Управление браузером"),
        tr(
            "Place the address bar where it is comfortable and choose your default search.",
            "Расположите адресную строку удобно и выберите поисковик по умолчанию."
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
    AddressBarStep(settings, onSettingsChange, showTitle = false)
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        tr("Default search", "Поисковик по умолчанию"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    SearchStep(settings, onSettingsChange, showTitle = false)
}

@Composable
private fun ThemeStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit,
    showTitle: Boolean = true
) {
    val metrics = rememberIlyroLayoutMetrics()
    if (showTitle) {
        StepTitle(
            tr("Choose a theme", "Выберите тему"),
            tr(
                "Preview the browser, not just the name.",
                "Сразу посмотрите, как будет выглядеть браузер."
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (metrics.isCompact) {
        val themes = BrowserTheme.entries
        val firstRow = themes.take(2)
        val bottomTheme = themes.getOrNull(2)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                firstRow.forEach { theme ->
                    ThemeVisualCard(
                        theme = theme,
                        selected = settings.theme == theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onSettingsChange(settings.copy(theme = theme)) }
                    )
                }
            }

            bottomTheme?.let { theme ->
                ThemeVisualCard(
                    theme = theme,
                    selected = settings.theme == theme,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSettingsChange(settings.copy(theme = theme)) }
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrowserTheme.entries.forEach { theme ->
                ThemeVisualCard(
                    theme = theme,
                    selected = settings.theme == theme,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChange(settings.copy(theme = theme)) }
                )
            }
        }
    }
}

@Composable
private fun ThemeVisualCard(
    theme: BrowserTheme,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val systemDark = isSystemInDarkTheme()
    val isDarkPreview = when (theme) {
        BrowserTheme.DARK -> true
        BrowserTheme.LIGHT -> false
        BrowserTheme.SYSTEM -> systemDark
    }
    val previewBackground = if (isDarkPreview) Color(0xFF101216) else Color(0xFFF7F8FA)
    val previewSurface = if (isDarkPreview) Color(0xFF20242A) else Color.White
    val previewText = if (isDarkPreview) Color(0xFFF2F3F5) else Color(0xFF1A1C20)

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = IlyroVisualTokens.SubtleBorderAlpha
                )
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (metrics.isNarrowPhone) 88.dp else 104.dp),
                shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                color = previewBackground
            ) {
                Column(modifier = Modifier.padding(7.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(19.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = previewSurface
                    ) {}
                    Spacer(modifier = Modifier.height(9.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(7.dp),
                        shape = CircleShape,
                        color = previewText.copy(alpha = 0.18f)
                    ) {}
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.50f)
                            .height(7.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                themeLabel(theme, LocalIlyroLanguage.current),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (selected) "✓" else " ",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WallpaperStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit,
    showTitle: Boolean = true
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val context = LocalContext.current
    val isDarkTheme = when (settings.theme) {
        BrowserTheme.DARK -> true
        BrowserTheme.LIGHT -> false
        BrowserTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val selectedBackground = if (isDarkTheme) settings.darkHomeBackground else settings.homeBackground
    val selectedCustomUri = if (isDarkTheme) settings.darkCustomWallpaperUri else settings.customWallpaperUri
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSettingsChange(
                if (isDarkTheme) {
                    settings.copy(
                        useSeparateDarkBackground = true,
                        darkHomeBackground = HomeBackground.CUSTOM,
                        darkCustomWallpaperUri = uri.toString()
                    )
                } else {
                    settings.copy(
                        useSeparateDarkBackground = true,
                        homeBackground = HomeBackground.CUSTOM,
                        customWallpaperUri = uri.toString()
                    )
                )
            )
        }
    }

    if (showTitle) {
        StepTitle(
            tr("Choose your look", "Выберите оформление"),
            tr(
                "Wallpaper and interface accent work together automatically.",
                "Обои и акцент интерфейса подстраиваются друг под друга автоматически."
            )
        )
        Spacer(modifier = Modifier.height(14.dp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (dense) { if (metrics.isNarrowPhone) 142.dp else 172.dp } else { if (metrics.isNarrowPhone) 176.dp else 214.dp }),
        shape = RoundedCornerShape(IlyroVisualTokens.LargeRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeWallpaper(
                settings.copy(
                    homeBackground = selectedBackground,
                    customWallpaperUri = selectedCustomUri,
                    useSeparateDarkBackground = false
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (metrics.isNarrowPhone) 12.dp else 16.dp)
            ) {
                Text(
                    "ILYRO",
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(9.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                        Text(
                            tr("Search or enter address", "Поиск или адрес"),
                            modifier = Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    Text(
        homeBackgroundLabel(selectedBackground),
        modifier = Modifier.padding(top = 11.dp, bottom = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        wallpaperPresetOptions(isDarkTheme, selectedBackground).forEach { background ->
            WallpaperMiniCard(
                background = background,
                customUri = selectedCustomUri,
                selected = selectedBackground == background,
                compact = metrics.isCompact,
                onClick = {
                    if (
                        background == HomeBackground.CUSTOM &&
                        selectedCustomUri.isNullOrBlank()
                    ) {
                        wallpaperPicker.launch(arrayOf("image/*"))
                    } else if (isDarkTheme) {
                        onSettingsChange(
                            settings.copy(
                                useSeparateDarkBackground = true,
                                darkHomeBackground = background
                            )
                        )
                    } else {
                        onSettingsChange(
                            settings.copy(
                                useSeparateDarkBackground = true,
                                homeBackground = background
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun WallpaperMiniCard(
    background: HomeBackground,
    customUri: String?,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val cardWidth = when {
        isTablet -> 184.dp
        compact -> 142.dp
        else -> 158.dp
    }
    val cardHeight = when {
        isTablet -> 128.dp
        compact -> 112.dp
        else -> 124.dp
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = cardWidth, height = cardHeight),
        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (background == HomeBackground.CUSTOM && !customUri.isNullOrBlank()) {
                CustomWallpaperThumbnail(
                    uriString = customUri,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BuiltInWallpaperPreview(
                    background = background,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    widePreview = true
                )
            }
            val lightWallpaper = background.isLightWallpaper()
            val labelShade = when {
                lightWallpaper -> 0.12f
                background == HomeBackground.NONE -> 0f
                else -> 0.35f
            }
            if (labelShade > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = labelShade))
                            )
                        )
                )
            }
            Text(
                homeBackgroundLabel(background),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                color = when {
                    lightWallpaper -> Color(0xFF18232D)
                    background == HomeBackground.NONE -> MaterialTheme.colorScheme.onBackground
                    else -> Color.White
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                SelectionBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                )
            }
        }
    }
}

@Composable
private fun AddressBarStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit,
    showTitle: Boolean = true
) {
    val metrics = rememberIlyroLayoutMetrics()
    if (showTitle) {
        StepTitle(
            tr("Where should the address bar live?", "Где разместить адресную строку?"),
            tr(
                "Choose visually — you can switch it later at any time.",
                "Выберите визуально — положение всегда можно поменять позже."
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (metrics.isNarrowPhone) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolbarPositionCard(
                position = ToolbarPosition.BOTTOM,
                selected = settings.toolbarPosition == ToolbarPosition.BOTTOM,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
                onClick = {
                    onSettingsChange(settings.copy(toolbarPosition = ToolbarPosition.BOTTOM))
                }
            )
            ToolbarPositionCard(
                position = ToolbarPosition.TOP,
                selected = settings.toolbarPosition == ToolbarPosition.TOP,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
                onClick = {
                    onSettingsChange(settings.copy(toolbarPosition = ToolbarPosition.TOP))
                }
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolbarPositionCard(
                position = ToolbarPosition.BOTTOM,
                selected = settings.toolbarPosition == ToolbarPosition.BOTTOM,
                modifier = Modifier.weight(1f),
                compact = metrics.isCompact,
                onClick = {
                    onSettingsChange(settings.copy(toolbarPosition = ToolbarPosition.BOTTOM))
                }
            )
            ToolbarPositionCard(
                position = ToolbarPosition.TOP,
                selected = settings.toolbarPosition == ToolbarPosition.TOP,
                modifier = Modifier.weight(1f),
                compact = metrics.isCompact,
                onClick = {
                    onSettingsChange(settings.copy(toolbarPosition = ToolbarPosition.TOP))
                }
            )
        }
    }
}

@Composable
private fun ToolbarPositionCard(
    position: ToolbarPosition,
    selected: Boolean,
    modifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrowserLayoutMock(position, compact)
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                if (position == ToolbarPosition.BOTTOM) tr("Bottom", "Снизу")
                else tr("Top", "Сверху"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (position == ToolbarPosition.BOTTOM) {
                    tr("Easy to reach", "Ближе к пальцам")
                } else {
                    tr("Classic layout", "Классический вид")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BrowserLayoutMock(position: ToolbarPosition, compact: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 132.dp else 160.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            if (position == ToolbarPosition.TOP) {
                FakeToolbar()
                Spacer(modifier = Modifier.height(7.dp))
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    ) {}
                    Spacer(modifier = Modifier.height(7.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.80f)
                            .height(6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    ) {}
                }
            }
            if (position == ToolbarPosition.BOTTOM) {
                Spacer(modifier = Modifier.height(7.dp))
                FakeToolbar()
            }
        }
    }
}

@Composable
private fun FakeToolbar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {}
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(23.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {}
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {}
        }
    }
}

@Composable
private fun SearchStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit,
    showTitle: Boolean = true
) {
    if (showTitle) {
        StepTitle(
        tr("Default search engine", "Поисковик по умолчанию"),
        tr(
            "Used for searches typed into the address bar.",
            "Используется для запросов из адресной строки."
        )
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
    SearchEngine.entries.forEach { engine ->
        SearchEngineChoiceCard(
            engine = engine,
            subtitle = when (engine) {
                SearchEngine.GOOGLE -> tr(
                    "Familiar and broad results",
                    "Привычный поиск и широкая выдача"
                )
                SearchEngine.YANDEX -> tr("Search with Yandex", "Поиск через Яндекс")
                SearchEngine.DUCKDUCKGO -> tr(
                    "Privacy-focused search",
                    "Поиск с упором на приватность"
                )
                SearchEngine.BRAVE -> tr(
                    "Independent privacy-focused index",
                    "Независимый поиск с упором на приватность"
                )
                SearchEngine.BING -> tr("Microsoft search", "Поиск Microsoft")
            },
            selected = settings.searchEngine == engine,
            onClick = { onSettingsChange(settings.copy(searchEngine = engine)) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ExtensionsStep(
    settings: BrowserSettings,
    onSettingsChange: (BrowserSettings) -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    StepTitle(
        tr("Protection", "Защита"),
        tr(
            "Choose the built-in protection features you want. No extra add-ons are required.",
            "Выберите встроенные функции защиты. Дополнительные расширения не нужны."
        )
    )
    Spacer(modifier = Modifier.height(14.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = IlyroVisualTokens.SelectedBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(if (metrics.isNarrowPhone) 12.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnboardingExtensionIcon(
                slug = "ublock-origin",
                fallback = "uB",
                modifier = Modifier.size(if (metrics.isNarrowPhone) 42.dp else 48.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    "ILYRO Shield",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    tr(
                        "Blocks ads and trackers with uBlock Origin",
                        "Блокирует рекламу и трекеры с uBlock Origin"
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.adBlockingEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(adBlockingEnabled = it))
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(if (metrics.isNarrowPhone) 12.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnboardingExtensionIcon(
                slug = "darkreader",
                fallback = "DR",
                modifier = Modifier.size(if (metrics.isNarrowPhone) 42.dp else 48.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    tr("Dark theme for websites", "Тёмная тема сайтов"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    tr(
                        "Darkens supported websites automatically. Powered by Dark Reader.",
                        "Автоматически затемняет поддерживаемые сайты. На базе Dark Reader."
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.darkWebsitesEnabled,
                onCheckedChange = {
                    onSettingsChange(settings.copy(darkWebsitesEnabled = it))
                }
            )
        }
    }
}

@Composable
private fun FinishStep(settings: BrowserSettings) {
    val metrics = rememberIlyroLayoutMetrics()
    val darkTheme = when (settings.theme) {
        BrowserTheme.DARK -> true
        BrowserTheme.LIGHT -> false
        BrowserTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val selectedBackground = if (darkTheme) settings.darkHomeBackground else settings.homeBackground
    val selectedCustomUri = if (darkTheme) settings.darkCustomWallpaperUri else settings.customWallpaperUri
    StepTitle(
        tr("ILYRO is ready", "ILYRO готов"),
        tr(
            "This is how your browser will feel from the first tab.",
            "Вот как будет выглядеть ваш браузер уже с первой вкладки."
        )
    )
    Spacer(modifier = Modifier.height(14.dp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (metrics.isNarrowPhone) 190.dp else 230.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.LargeRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeWallpaper(
                settings.copy(
                    homeBackground = selectedBackground,
                    customWallpaperUri = selectedCustomUri,
                    useSeparateDarkBackground = false
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (metrics.isNarrowPhone) 11.dp else 14.dp)
            ) {
                if (settings.toolbarPosition == ToolbarPosition.TOP) {
                    FinishToolbarPreview()
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ILYRO", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(9.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.74f)
                                .height(36.dp),
                            shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
                        ) {}
                    }
                }
                if (settings.toolbarPosition == ToolbarPosition.BOTTOM) {
                    Spacer(modifier = Modifier.height(9.dp))
                    FinishToolbarPreview()
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    SummaryChipPair(
        leftLabel = tr("Theme", "Тема"),
        leftValue = themeLabel(settings.theme, settings.language),
        rightLabel = tr("Wallpaper", "Обои"),
        rightValue = homeBackgroundLabel(selectedBackground)
    )
    Spacer(modifier = Modifier.height(8.dp))
    SummaryChipPair(
        leftLabel = tr("Address bar", "Адресная строка"),
        leftValue = if (settings.toolbarPosition == ToolbarPosition.BOTTOM) {
            tr("Bottom", "Снизу")
        } else {
            tr("Top", "Сверху")
        },
        rightLabel = tr("Search", "Поиск"),
        rightValue = settings.searchEngine.displayName
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            tr(
                "Shield ${if (settings.adBlockingEnabled) "on" else "off"} · Dark websites ${if (settings.darkWebsitesEnabled) "on" else "off"}",
                "Shield ${if (settings.adBlockingEnabled) "включён" else "выключен"} · Тёмные сайты ${if (settings.darkWebsitesEnabled) "включены" else "выключены"}"
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FinishToolbarPreview() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(25.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {}
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(27.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f)
            ) {}
            Surface(
                modifier = Modifier.size(25.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            ) {}
        }
    }
}

@Composable
private fun SummaryChipPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    val metrics = rememberIlyroLayoutMetrics()
    if (metrics.isNarrowPhone) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip(leftLabel, leftValue, Modifier.fillMaxWidth())
            SummaryChip(rightLabel, rightValue, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip(leftLabel, leftValue, Modifier.weight(1f))
            SummaryChip(rightLabel, rightValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    val metrics = rememberIlyroLayoutMetrics()
    Text(
        title,
        style = if (metrics.isNarrowPhone) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineSmall
        },
        fontWeight = FontWeight.Bold
    )
    Text(
        text = subtitle,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SelectionBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(25.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun stepSectionLabel(step: Int): String = when (step) {
    0 -> tr("Appearance", "Оформление")
    1 -> tr("Browser", "Браузер")
    2 -> tr("Protection", "Защита")
    else -> tr("Ready", "Готово")
}
