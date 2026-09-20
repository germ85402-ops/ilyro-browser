package com.ilyro.browser.ui

import android.graphics.Bitmap

// Keep previews small enough for the tabs overview without retaining several full-resolution
// screenshots in the app process.
internal const val MAX_TAB_PREVIEWS = 4
private const val MAX_TAB_PREVIEW_WIDTH = 640
private const val MAX_TAB_PREVIEW_HEIGHT = 480

internal fun downscaleTabPreview(bitmap: Bitmap): Bitmap {
    if (bitmap.width <= MAX_TAB_PREVIEW_WIDTH && bitmap.height <= MAX_TAB_PREVIEW_HEIGHT) {
        return bitmap
    }

    val scale = minOf(
        MAX_TAB_PREVIEW_WIDTH.toFloat() / bitmap.width.toFloat(),
        MAX_TAB_PREVIEW_HEIGHT.toFloat() / bitmap.height.toFloat()
    )
    val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}


/**
 * Process-level memory-pressure bridge. The Application never owns BrowserTab instances; the
 * active BrowserScreen registers a short-lived callback and unregisters it with its composition.
 */
internal object BrowserMemoryCoordinator {
    private var trimHandler: ((Int) -> Unit)? = null

    @Synchronized
    fun bind(handler: (Int) -> Unit): () -> Unit {
        trimHandler = handler
        return {
            synchronized(this) {
                if (trimHandler === handler) trimHandler = null
            }
        }
    }

    fun trim(level: Int) {
        val handler = synchronized(this) { trimHandler }
        handler?.invoke(level)
    }
}
