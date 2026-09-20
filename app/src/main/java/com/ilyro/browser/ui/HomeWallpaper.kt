package com.ilyro.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CUSTOM_WALLPAPER_PIXELS = 3_145_728L

@Composable
internal fun HomeWallpaper(settings: BrowserSettings) {
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (darkTheme && settings.useSeparateDarkBackground) {
        settings.darkHomeBackground
    } else {
        settings.homeBackground
    }
    val customUri = if (darkTheme && settings.useSeparateDarkBackground) {
        settings.darkCustomWallpaperUri
    } else {
        settings.customWallpaperUri
    }
    if (background == HomeBackground.NONE) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val blurRadius = settings.wallpaperBlur.radiusDp.dp
    val blurred = settings.wallpaperBlur != WallpaperBlur.OFF
    val effectScale = when (settings.wallpaperBlur) {
        WallpaperBlur.OFF -> 1f
        WallpaperBlur.SOFT -> 1.025f
        WallpaperBlur.MEDIUM -> 1.055f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { clip = true }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurred) {
                        Modifier
                            .graphicsLayer {
                                scaleX = effectScale
                                scaleY = effectScale
                            }
                            .blur(blurRadius)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (background == HomeBackground.CUSTOM) {
                CustomWallpaperImage(customUri, settings.wallpaperFit)
            } else {
                BuiltInWallpaper(background)
            }
        }

        if (settings.wallpaperDim.alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.wallpaperDim.alpha))
            )
        }
    }
}

@Composable
private fun CustomWallpaperImage(uriString: String?, fit: WallpaperFit) {
    androidx.compose.runtime.key(uriString) {
        CustomWallpaperImageContent(uriString, fit)
    }
}

@Composable
private fun CustomWallpaperImageContent(uriString: String?, fit: WallpaperFit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeCustomWallpaper(context, uriString)
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.Center,
            contentScale = when (fit) {
                WallpaperFit.FILL -> ContentScale.Crop
                WallpaperFit.FIT -> ContentScale.Fit
                WallpaperFit.CENTER -> ContentScale.None
            }
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

private fun decodeCustomWallpaper(context: Context, uriString: String?): Bitmap? {
    if (uriString.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) >
        MAX_CUSTOM_WALLPAPER_PIXELS
    ) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

@Composable
private fun BuiltInWallpaper(background: HomeBackground) {
    BuiltInWallpaperPreview(background = background, modifier = Modifier.fillMaxSize())
}

@Composable
internal fun BuiltInWallpaperPreview(
    background: HomeBackground,
    modifier: Modifier = Modifier
) {
    // Prefer the real bundled wallpaper files when present. Keeping the Canvas
    // fallback lets the source compile before/while binary assets are being added.
    val context = LocalContext.current
    if (background == HomeBackground.NONE) {
        Box(modifier.background(MaterialTheme.colorScheme.background))
        return
    }
    val assetName = when (background) {
        HomeBackground.NONE -> null
        HomeBackground.CLEAN -> "wallpaper_space_nebula"
        HomeBackground.SOFT_GRADIENT -> "wallpaper_mountain_sunrise"
        HomeBackground.MOUNTAIN_DUSK -> "wallpaper_aurora_lake"
        HomeBackground.BLUE_HORIZON -> "wallpaper_forest_waterfall"
        HomeBackground.NIGHT_WAVES -> "wallpaper_moonlit_valley"
        HomeBackground.AURORA -> "wallpaper_desert_sunset"
        HomeBackground.CUSTOM -> null
    }
    val assetId = assetName?.let {
        context.resources.getIdentifier(it, "drawable", context.packageName)
    } ?: 0

    if (assetId != 0) {
        Image(
            painter = painterResource(assetId),
            contentDescription = null,
            modifier = modifier,
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop
        )
        return
    }

    Canvas(modifier = modifier) {
        when (background) {
            HomeBackground.NONE -> Unit
            HomeBackground.CLEAN -> {
                // Space nebula
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF050817), Color(0xFF121A3D), Color(0xFF2A1247)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF8D6CFF).copy(alpha = 0.55f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.36f),
                        radius = size.width * 0.42f
                    ),
                    radius = size.width * 0.42f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.36f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF42B8FF).copy(alpha = 0.38f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.63f),
                        radius = size.width * 0.36f
                    ),
                    radius = size.width * 0.36f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.63f)
                )
                repeat(78) { index ->
                    val x = ((index * 97 + 31) % 997) / 997f * size.width
                    val y = ((index * 173 + 53) % 991) / 991f * size.height
                    val r = if (index % 9 == 0) 2.2f else if (index % 4 == 0) 1.5f else 1.0f
                    drawCircle(
                        color = Color.White.copy(alpha = if (index % 7 == 0) 0.82f else 0.48f),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }

            HomeBackground.SOFT_GRADIENT -> {
                // Mountain sunrise
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF1F4F75), Color(0xFFF28C6E), Color(0xFFFFC47D)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    color = Color(0xFFFFE5AA).copy(alpha = 0.96f),
                    radius = size.minDimension * 0.09f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.30f)
                )
                val far = Path().apply {
                    moveTo(0f, size.height * 0.72f)
                    lineTo(size.width * 0.18f, size.height * 0.45f)
                    lineTo(size.width * 0.34f, size.height * 0.66f)
                    lineTo(size.width * 0.53f, size.height * 0.37f)
                    lineTo(size.width * 0.74f, size.height * 0.67f)
                    lineTo(size.width, size.height * 0.48f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(far, color = Color(0xFF425466).copy(alpha = 0.78f))
                val near = Path().apply {
                    moveTo(0f, size.height * 0.82f)
                    lineTo(size.width * 0.29f, size.height * 0.58f)
                    lineTo(size.width * 0.48f, size.height * 0.77f)
                    lineTo(size.width * 0.70f, size.height * 0.54f)
                    lineTo(size.width, size.height * 0.77f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(near, color = Color(0xFF172531).copy(alpha = 0.96f))
            }

            HomeBackground.MOUNTAIN_DUSK -> {
                // Aurora lake
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF03131B), Color(0xFF0B2A36), Color(0xFF12384A)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF42F5B9).copy(alpha = 0.58f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.27f),
                        radius = size.width * 0.46f
                    ),
                    radius = size.width * 0.46f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.27f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF6B6DFF).copy(alpha = 0.38f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.33f),
                        radius = size.width * 0.42f
                    ),
                    radius = size.width * 0.42f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.33f)
                )
                val ridge = Path().apply {
                    moveTo(0f, size.height * 0.61f)
                    lineTo(size.width * 0.20f, size.height * 0.47f)
                    lineTo(size.width * 0.39f, size.height * 0.59f)
                    lineTo(size.width * 0.59f, size.height * 0.43f)
                    lineTo(size.width * 0.78f, size.height * 0.59f)
                    lineTo(size.width, size.height * 0.49f)
                    lineTo(size.width, size.height * 0.66f)
                    lineTo(0f, size.height * 0.66f)
                    close()
                }
                drawPath(ridge, color = Color(0xFF07161D))
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF12384A).copy(alpha = 0.70f), Color(0xFF020B10)),
                        startY = size.height * 0.64f,
                        endY = size.height
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.64f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.36f)
                )
                repeat(7) { index ->
                    val y = size.height * (0.70f + index * 0.038f)
                    drawRect(
                        color = if (index % 2 == 0) Color(0xFF38D8B0).copy(alpha = 0.10f)
                        else Color(0xFF777CFF).copy(alpha = 0.09f),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, y),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.68f, 2.2f)
                    )
                }
            }

            HomeBackground.BLUE_HORIZON -> {
                // Forest waterfall
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF173E42), Color(0xFF1D5C4E), Color(0xFF0B291F)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFBFE8C7).copy(alpha = 0.25f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.18f),
                        radius = size.width * 0.35f
                    ),
                    radius = size.width * 0.35f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.18f)
                )
                repeat(15) { index ->
                    val x = (index / 14f) * size.width
                    val h = size.height * (0.20f + ((index * 37) % 9) * 0.014f)
                    val tree = Path().apply {
                        moveTo(x - size.width * 0.035f, size.height * 0.66f)
                        lineTo(x, size.height * 0.66f - h)
                        lineTo(x + size.width * 0.035f, size.height * 0.66f)
                        close()
                    }
                    drawPath(tree, color = Color(0xFF09271E).copy(alpha = 0.92f))
                }
                drawRect(
                    color = Color(0xFFD9F4F3).copy(alpha = 0.88f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.465f, size.height * 0.35f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.07f, size.height * 0.39f)
                )
                drawRect(
                    color = Color(0xFF8AD1CF).copy(alpha = 0.55f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.72f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.20f)
                )
                drawCircle(
                    color = Color(0xFFB9E7E0).copy(alpha = 0.50f),
                    radius = size.width * 0.14f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.87f)
                )
            }

            HomeBackground.NIGHT_WAVES -> {
                // Moonlit valley
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF040817), Color(0xFF0B1734), Color(0xFF20395A)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    color = Color(0xFFF4F1D8).copy(alpha = 0.94f),
                    radius = size.minDimension * 0.085f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.24f)
                )
                repeat(38) { index ->
                    val x = ((index * 79 + 13) % 983) / 983f * size.width
                    val y = ((index * 113 + 29) % 530) / 1000f * size.height
                    drawCircle(
                        Color.White.copy(alpha = 0.48f),
                        radius = if (index % 8 == 0) 1.8f else 1.0f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
                val back = Path().apply {
                    moveTo(0f, size.height * 0.74f)
                    lineTo(size.width * 0.18f, size.height * 0.49f)
                    lineTo(size.width * 0.37f, size.height * 0.70f)
                    lineTo(size.width * 0.58f, size.height * 0.45f)
                    lineTo(size.width * 0.81f, size.height * 0.70f)
                    lineTo(size.width, size.height * 0.52f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(back, color = Color(0xFF14233A).copy(alpha = 0.92f))
                val front = Path().apply {
                    moveTo(0f, size.height * 0.86f)
                    lineTo(size.width * 0.27f, size.height * 0.69f)
                    lineTo(size.width * 0.46f, size.height * 0.82f)
                    lineTo(size.width * 0.67f, size.height * 0.64f)
                    lineTo(size.width, size.height * 0.84f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(front, color = Color(0xFF07101D))
            }

            HomeBackground.AURORA -> {
                // Desert sunset
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF502A54), Color(0xFFE56D5B), Color(0xFFFFB56B)),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawCircle(
                    color = Color(0xFFFFE0A3).copy(alpha = 0.95f),
                    radius = size.minDimension * 0.10f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.69f, size.height * 0.34f)
                )
                val farDune = Path().apply {
                    moveTo(0f, size.height * 0.70f)
                    cubicTo(
                        size.width * 0.25f, size.height * 0.58f,
                        size.width * 0.48f, size.height * 0.76f,
                        size.width, size.height * 0.59f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(farDune, color = Color(0xFFD98955).copy(alpha = 0.95f))
                val nearDune = Path().apply {
                    moveTo(0f, size.height * 0.86f)
                    cubicTo(
                        size.width * 0.31f, size.height * 0.67f,
                        size.width * 0.62f, size.height * 0.88f,
                        size.width, size.height * 0.72f
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(nearDune, color = Color(0xFF8B4A3B))
            }

            HomeBackground.CUSTOM -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF343941), Color(0xFF59616D)),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
        }
    }
}
