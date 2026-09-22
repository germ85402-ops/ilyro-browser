package com.ilyro.browser.ui

import android.os.Looper
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Waits for an ordered store write, but never on the main thread.
 *
 * The writer is a single-threaded executor, so submitting already preserves the ordering that
 * user-initiated saves need. Blocking the main thread on top of that only risks an ANR when the
 * snapshot is large, so the wait is limited to background callers and bounded by a timeout.
 */
internal fun awaitStoreWrite(future: Future<*>) {
    val onMainThread = runCatching {
        Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()
    }.getOrDefault(false)
    if (onMainThread) return
    runCatching { future.get(STORE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
}

private const val STORE_WRITE_TIMEOUT_SECONDS = 5L
