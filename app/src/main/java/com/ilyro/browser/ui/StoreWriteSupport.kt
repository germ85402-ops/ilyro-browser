package com.ilyro.browser.ui

import android.os.Looper
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Waits for an ordered store write, but never on the main thread.
 *
 * The writer is a single-threaded executor, so submitting already preserves the ordering that
 * user-initiated saves need. Never wait on the main thread; background restore flows can await
 * completion and surface a failed write before reading the restored state.
 */
internal fun awaitStoreWrite(future: Future<*>) {
    val onMainThread = runCatching {
        Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()
    }.getOrDefault(false)
    if (onMainThread) return
    future.get(STORE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
}

private const val STORE_WRITE_TIMEOUT_SECONDS = 5L
