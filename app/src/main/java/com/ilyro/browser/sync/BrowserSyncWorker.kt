package com.ilyro.browser.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ilyro.browser.account.GoogleAccountManager
import com.ilyro.browser.ui.BrowserSettingsStore

class BrowserSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!BrowserAutoSyncScheduler.isEnabled(applicationContext)) {
            return Result.success()
        }

        val account = GoogleAccountManager(applicationContext).currentProfile()
            ?: return Result.success()

        val authorization = GoogleDriveAuthorizationManager(applicationContext)
            .authorize(account.email)
        val accessToken = when (authorization) {
            is DriveAuthorizationResult.Authorized -> authorization.accessToken
            is DriveAuthorizationResult.ResolutionRequired -> return Result.retry()
            is DriveAuthorizationResult.Failure -> return Result.retry()
        }

        val browserPrefs = applicationContext.getSharedPreferences(
            "ilyro_browser",
            Context.MODE_PRIVATE
        )
        val settings = BrowserSettingsStore.restore(browserPrefs)
        return when (
            BrowserSettingsSyncManager(applicationContext).sync(
                settings = settings,
                provider = GoogleDriveSyncProvider(accessToken)
            )
        ) {
            is SyncResult.Success -> Result.success()
            is SyncResult.NotAuthorized -> return Result.retry()
            is SyncResult.Failure -> Result.retry()
        }
    }
}
