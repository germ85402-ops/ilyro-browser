package com.ilyro.browser

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.work.Configuration
import com.ilyro.browser.ui.BrowserIconCache
import com.ilyro.browser.ui.BrowserMemoryCoordinator

/**
 * Supplies WorkManager configuration for safe on-demand initialization.
 * WorkManager is created only when automatic sync is enabled.
 */
class IlyroApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            BrowserIconCache.trimMemory()
            BrowserMemoryCoordinator.trim(level)
        }
    }

    override fun onLowMemory() {
        BrowserIconCache.trimMemory()
        BrowserMemoryCoordinator.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        super.onLowMemory()
    }
}
