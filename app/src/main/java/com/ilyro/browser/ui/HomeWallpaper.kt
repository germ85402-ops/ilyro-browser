package com.ilyro.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ilyro.browser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CUSTOM_WALLPAPER_PIXELS = 3_145_728L

@Composable
internal fun HomeWallpaper(
    settings: BrowserSettings,
    builtInContentScale: ContentScale = ContentScale.Crop
) {
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
    // Full-screen blur is GPU-heavy; cap the strongest preset while keeping its visual character.
    val blurRadius = settings.wallpaperBlur.radiusDp.coerceAtMost(10).dp
    val blurred = settings.wallpaperBlur != WallpaperBlur.OFF
    val effectScale = when (settings.wallpaperBlur) {
        WallpaperBlur.OFF -> 1f
        WallpaperBlur.SOFT -> 1.015f
        WallpaperBlur.MEDIUM -> 1.03f
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
                BuiltInWallpaper(background, builtInContentScale)
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
private fun BuiltInWallpaper(background: HomeBackground, contentScale: ContentScale) {
    BuiltInWallpaperPreview(
        background = background,
        modifier = Modifier.fillMaxSize(),
        contentScale = contentScale
    )
}

internal fun builtInWallpaperResource(background: HomeBackground, isTablet: Boolean): Int? =
    when (background) {
        HomeBackground.NONE, HomeBackground.CUSTOM -> null
        HomeBackground.STILLWATER -> if (isTablet) {
            R.drawable.wallpaper_tablet_stillwater
        } else {
            R.drawable.wallpaper_phone_stillwater
        }
        HomeBackground.PINE_DUSK -> if (isTablet) {
            R.drawable.wallpaper_tablet_pine_dusk
        } else {
            R.drawable.wallpaper_phone_pine_dusk
        }
        HomeBackground.ALPINE_DAWN -> if (isTablet) {
            R.drawable.wallpaper_tablet_alpine_dawn
        } else {
            R.drawable.wallpaper_phone_alpine_dawn
        }
        HomeBackground.OBSIDIAN -> if (isTablet) {
            R.drawable.wallpaper_tablet_obsidian
        } else {
            R.drawable.wallpaper_phone_obsidian
        }
        HomeBackground.INK_WASH -> if (isTablet) {
            R.drawable.wallpaper_tablet_ink_wash
        } else {
            R.drawable.wallpaper_phone_ink_wash
        }
        HomeBackground.EMBER_DUNES -> if (isTablet) {
            R.drawable.wallpaper_tablet_ember_dunes
        } else {
            R.drawable.wallpaper_phone_ember_dunes
        }
        HomeBackground.PRISM_FLOW -> if (isTablet) {
            R.drawable.wallpaper_tablet_prism_flow
        } else {
            R.drawable.wallpaper_phone_prism_flow
        }
    }

@Composable
internal fun BuiltInWallpaperPreview(
    background: HomeBackground,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (background == HomeBackground.NONE) {
        Box(modifier.background(MaterialTheme.colorScheme.background))
        return
    }

    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val assetId = builtInWallpaperResource(background, isTablet)

    if (assetId != null) {
        Image(
            painter = painterResource(assetId),
            contentDescription = null,
            modifier = modifier,
            alignment = Alignment.Center,
            contentScale = contentScale
        )
        return
    }

    val fallbackColors = when (background) {
        HomeBackground.NONE -> listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
        HomeBackground.STILLWATER -> listOf(Color(0xFF0B111B), Color(0xFF243958))
        HomeBackground.PINE_DUSK -> listOf(Color(0xFF0A151C), Color(0xFF263F56))
        HomeBackground.ALPINE_DAWN -> listOf(Color(0xFF111A27), Color(0xFF34455D))
        HomeBackground.OBSIDIAN -> listOf(Color(0xFF080A0F), Color(0xFF18345D))
        HomeBackground.INK_WASH -> listOf(Color(0xFF101724), Color(0xFF344052))
        HomeBackground.EMBER_DUNES -> listOf(Color(0xFF1D1425), Color(0xFF9B422E))
        HomeBackground.PRISM_FLOW -> listOf(Color(0xFF080912), Color(0xFF27364F))
        HomeBackground.CUSTOM -> listOf(Color(0xFF343941), Color(0xFF59616D))
    }
    Box(modifier.background(Brush.verticalGradient(fallbackColors)))
}
