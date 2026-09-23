package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberWallpaperAccent(
    settings: BrowserSettings,
    darkTheme: Boolean
): BrowserAccent {
    val context = LocalContext.current
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

    if (background != HomeBackground.CUSTOM) {
        return background.wallpaperAccentPreset()
    }

    val detected by produceState(
        initialValue = BrowserAccent.BLUE,
        key1 = customUri,
        key2 = darkTheme
    ) {
        value = withContext(Dispatchers.IO) {
            detectWallpaperAccent(context.contentResolver, customUri)
        }
    }
    return detected
}

internal fun HomeBackground.wallpaperAccentPreset(): BrowserAccent = when (this) {
    HomeBackground.NONE -> BrowserAccent.ILYRO
    HomeBackground.STILLWATER -> BrowserAccent.BLUE
    HomeBackground.PINE_DUSK -> BrowserAccent.FOREST
    HomeBackground.ALPINE_DAWN -> BrowserAccent.BLUE
    HomeBackground.CUSTOM -> BrowserAccent.BLUE
}

private fun detectWallpaperAccent(
    resolver: android.content.ContentResolver,
    uriString: String?
): BrowserAccent {
    if (uriString.isNullOrBlank()) return BrowserAccent.BLUE
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return BrowserAccent.BLUE
    val bitmap = decodeSmallBitmap(resolver, uri) ?: return BrowserAccent.BLUE

    var neutralScore = 0.0
    var blueScore = 0.0
    var violetScore = 0.0
    var forestScore = 0.0
    val hsv = FloatArray(3)
    val stepX = (bitmap.width / 32).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha >= 128) {
                AndroidColor.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                if (value in 0.14f..0.96f) {
                    if (saturation < 0.16f) {
                        neutralScore += 0.18
                    } else {
                        val weight = (0.30 + saturation * 0.85 + value * 0.20).toDouble()
                        when {
                            hue in 70f..170f -> forestScore += weight
                            hue in 170f..255f -> blueScore += weight
                            hue in 255f..335f -> violetScore += weight
                            else -> violetScore += weight * 0.78
                        }
                    }
                }
            }
            x += stepX
        }
        y += stepY
    }
    bitmap.recycle()

    val chroma = blueScore + violetScore + forestScore
    if (chroma <= neutralScore * 1.15 || chroma < 2.0) return BrowserAccent.ILYRO
    return when (maxOf(blueScore, violetScore, forestScore)) {
        forestScore -> BrowserAccent.FOREST
        violetScore -> BrowserAccent.VIOLET
        else -> BrowserAccent.BLUE
    }
}

private fun decodeSmallBitmap(
    resolver: android.content.ContentResolver,
    uri: Uri
): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}.getOrNull()
