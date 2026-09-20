package com.ilyro.browser.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BrowserAutoSyncScheduler {
    private const val PREFS_NAME = "ilyro_sync_state"
    private const val KEY_ENABLED = "automatic_sync_enabled_v1"
    private const val KEY_LAST_ERROR = "automatic_sync_last_error_v1"
    private const val WORK_NAME = "ilyro-automatic-browser-sync"

    private val initializationLock = Any()

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun lastError(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ERROR, null)

    suspend fun setEnabled(context: Context, enabled: Boolean): Boolean =
        withContext(Dispatchers.Default) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            try {
                if (enabled) {
                    // Save the preference only after WorkManager accepts the request.
                    schedule(appContext)
                } else {
                    workManager(appContext).cancelUniqueWork(WORK_NAME)
                }

                prefs.edit()
                    .putBoolean(KEY_ENABLED, enabled)
                    .remove(KEY_LAST_ERROR)
                    .apply()
                true
            } catch (error: Exception) {
                prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_LAST_ERROR, error.describe())
                    .apply()
                false
            }
        }

    suspend fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return

        withContext(Dispatchers.Default) {
            runCatching {
                schedule(appContext)
            }.onFailure { error ->
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_LAST_ERROR, error.describe())
                    .apply()
            }
        }
    }

    private fun workManager(context: Context): WorkManager {
        val appContext = context.applicationContext
        return try {
            WorkManager.getInstance(appContext)
        } catch (_: IllegalStateException) {
            synchronized(initializationLock) {
                try {
                    WorkManager.getInstance(appContext)
                } catch (_: IllegalStateException) {
                    val configuration = (appContext as? Configuration.Provider)
                        ?.workManagerConfiguration
                        ?: Configuration.Builder().build()

                    // Initialization is one-time. If another thread wins the race, the final
                    // getInstance call below returns the already-created manager.
                    try {
                        WorkManager.initialize(appContext, configuration)
                    } catch (_: IllegalStateException) {
                        // Already initialized between the two getInstance calls.
                    }
                    WorkManager.getInstance(appContext)
                }
            }
        }
    }

    private fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BrowserSyncWorker>(
            12L,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun Throwable.describe(): String =
        message?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
            ?: javaClass.simpleName
}
