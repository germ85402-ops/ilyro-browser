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
            ?: run {
                BrowserAutoSyncScheduler.recordWorkerFailure(
                    applicationContext,
                    "Google account is not signed in."
                )
                return Result.failure()
            }

        val authorization = GoogleDriveAuthorizationManager(applicationContext)
            .authorize(account.email)
        val accessToken = when (authorization) {
            is DriveAuthorizationResult.Authorized -> authorization.accessToken
            is DriveAuthorizationResult.ResolutionRequired -> {
                BrowserAutoSyncScheduler.recordWorkerFailure(
                    applicationContext,
                    "Google Drive authorization is required."
                )
                return Result.failure()
            }
            is DriveAuthorizationResult.Failure -> {
                BrowserAutoSyncScheduler.recordWorkerFailure(
                    applicationContext,
                    authorization.message ?: "Google Drive authorization failed."
                )
                return Result.failure()
            }
        }

        val browserPrefs = applicationContext.getSharedPreferences(
            "ilyro_browser",
            Context.MODE_PRIVATE
        )
        val settings = BrowserSettingsStore.restore(browserPrefs)
        val syncResult = BrowserSettingsSyncManager(applicationContext).sync(
            settings = settings,
            provider = GoogleDriveSyncProvider(accessToken)
        )
        return when (syncResult) {
            is SyncResult.Success -> {
                BrowserAutoSyncScheduler.recordWorkerSuccess(applicationContext)
                Result.success()
            }
            is SyncResult.NotAuthorized -> {
                BrowserAutoSyncScheduler.recordWorkerFailure(
                    applicationContext,
                    "Google Drive authorization expired."
                )
                Result.failure()
            }
            is SyncResult.Failure -> {
                BrowserAutoSyncScheduler.recordWorkerFailure(
                    applicationContext,
                    syncResult.message ?: "Automatic sync failed."
                )
                if (syncResult.recoverable) Result.retry() else Result.failure()
            }
        }
    }
}
