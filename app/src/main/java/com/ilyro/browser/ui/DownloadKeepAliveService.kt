package com.ilyro.browser.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ilyro.browser.ACTION_OPEN_DOWNLOADS
import com.ilyro.browser.R
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class DownloadKeepAliveService : Service() {
    private data class TrackedDownload(
        val name: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val paused: Boolean = false
    )

    private val activeDownloads = linkedMapOf<Long, TrackedDownload>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE, ACTION_RESUME -> {
                val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)
                if (id != Long.MIN_VALUE) {
                    val pause = intent.action == ACTION_PAUSE
                    val changed = controlHandler?.invoke(id, pause) == true
                    if (changed) setPausedTracked(id, pause)
                }
                if (activeDownloads.isEmpty()) stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)
                if (id != Long.MIN_VALUE) {
                    val changed = cancelHandler?.invoke(id) == true
                    if (changed) Companion.finish(id)
                }
                if (activeDownloads.isEmpty()) stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_TRACK -> Unit
            else -> {
                if (activeDownloads.isEmpty()) stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        val id = intent.getLongExtra(EXTRA_ID, Long.MIN_VALUE)
        if (id == Long.MIN_VALUE) {
            if (activeDownloads.isEmpty()) stopSelf(startId)
            return START_NOT_STICKY
        }

        // A foreground-service start that Android rejected can still race with a previously
        // queued ACTION_TRACK. Keep the marker until replace()/finish() consumes it so a
        // provisional download id can be translated to the real persisted record safely.
        if (foregroundUnavailableIds.contains(id)) {
            activeIds.remove(id)
            getSystemService(NotificationManager::class.java).apply {
                cancel(individualNotificationId(id))
                cancel(NOTIFICATION_ID)
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // A tiny file can finish before Android delivers the foreground-service start command.
        startedIds.add(id)
        if (finishedBeforeStart.remove(id)) {
            startedIds.remove(id)
            if (activeDownloads.isEmpty()) {
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val name = intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: "Download"
        val current = activeDownloads[id]
        activeDownloads[id] = current?.copy(name = name) ?: TrackedDownload(name = name)
        promote()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ gives dataSync foreground services only a short grace period after
        // timeout. Persist resumable Gecko/HLS work as Paused first, then stop the service
        // immediately so the platform cannot raise RemoteServiceException.
        if (fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0) {
            pauseTrackedForForegroundStop()
        }
        stopAfterForegroundLoss()
    }

    override fun onDestroy() {
        if (liveService() === this) instanceRef = null
        val manager = getSystemService(NotificationManager::class.java)
        activeDownloads.keys.forEach { manager.cancel(individualNotificationId(it)) }
        activeDownloads.clear()
        activeIds.clear()
        startedIds.clear()
        super.onDestroy()
    }

    private fun updateTracked(id: Long, fileName: String, downloaded: Long, total: Long) {
        mainHandler.post {
            val current = activeDownloads[id] ?: return@post
            activeDownloads[id] = current.copy(
                name = fileName.takeIf { it.isNotBlank() } ?: current.name,
                downloadedBytes = downloaded.coerceAtLeast(0L),
                totalBytes = total
            )
            promote()
        }
    }

    private fun setPausedTracked(id: Long, paused: Boolean) {
        mainHandler.post {
            val current = activeDownloads[id] ?: return@post
            activeDownloads[id] = current.copy(paused = paused)
            promote()
        }
    }

    private fun replaceTracked(oldId: Long, newId: Long, fileName: String) {
        mainHandler.post {
            val manager = getSystemService(NotificationManager::class.java)
            val previous = activeDownloads.remove(oldId)
            val current = activeDownloads[newId]
            val name = fileName.takeIf { it.isNotBlank() }
                ?: current?.name
                ?: previous?.name
                ?: "Download"
            activeDownloads[newId] = when {
                current != null -> current.copy(name = name)
                previous != null -> previous.copy(name = name)
                else -> TrackedDownload(name = name)
            }
            manager.cancel(individualNotificationId(oldId))
            finishedBeforeStart.remove(newId)
            promote()
        }
    }

    private fun finishTracked(id: Long) {
        mainHandler.post {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(individualNotificationId(id))
            val existed = activeDownloads.remove(id) != null
            if (existed) finishedBeforeStart.remove(id)
            if (activeDownloads.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                manager.cancel(NOTIFICATION_ID)
                stopSelf()
            } else {
                promote()
            }
        }
    }

    private fun pauseTrackedForForegroundStop() {
        activeDownloads.entries.toList().forEach { (id, item) ->
            if (item.paused) return@forEach
            val paused = runCatching { controlHandler?.invoke(id, true) == true }
                .getOrDefault(false)
            if (paused) {
                activeDownloads[id] = item.copy(paused = true)
                foregroundUnavailableIds.remove(id)
            } else {
                markForegroundUnavailable(id)
            }
        }
    }

    private fun stopAfterForegroundLoss() {
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        manager.cancel(NOTIFICATION_ID)
        activeDownloads.keys.forEach { manager.cancel(individualNotificationId(it)) }
        stopSelf()
    }

    private fun controlPendingIntent(id: Long, paused: Boolean): PendingIntent {
        val action = if (paused) ACTION_RESUME else ACTION_PAUSE
        val intent = Intent(this, DownloadKeepAliveService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getService(
            this,
            actionRequestCode(id, paused),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPendingIntent(id: Long): PendingIntent {
        val intent = Intent(this, DownloadKeepAliveService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getService(
            this,
            cancelRequestCode(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.setAction(ACTION_OPEN_DOWNLOADS)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDownloadNotification(id: Long, item: TrackedDownload): Notification {
        val title = item.name
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ilyro_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        launchPendingIntent()?.let { builder.setContentIntent(it) }

        if (item.paused) {
            val detail = if (item.totalBytes > 0L) {
                "Paused • ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
            } else {
                "Paused • ${formatBytes(item.downloadedBytes)}"
            }
            builder
                .setContentText(detail)
                .setProgress(
                    if (item.totalBytes > 0L) 100 else 0,
                    if (item.totalBytes > 0L) ((item.downloadedBytes * 100L) / item.totalBytes).toInt().coerceIn(0, 100) else 0,
                    item.totalBytes <= 0L
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "Resume",
                        controlPendingIntent(id, true)
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancel",
                        cancelPendingIntent(id)
                    ).build()
                )
            return builder.build()
        }

        if (item.totalBytes > 0L) {
            val percent = ((item.downloadedBytes * 100L) / item.totalBytes)
                .toInt()
                .coerceIn(0, 100)
            builder
                .setContentText(
                    "$percent% • ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                )
                .setProgress(100, percent, false)
        } else {
            builder
                .setContentText("Downloading • ${formatBytes(item.downloadedBytes)}")
                .setProgress(0, 0, true)
        }
        builder
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    controlPendingIntent(id, false)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    cancelPendingIntent(id)
                ).build()
            )
        return builder.build()
    }

    private fun promote() {
        val manager = getSystemService(NotificationManager::class.java)
        val count = activeDownloads.size
        if (count <= 0) return

        val foregroundNotification = if (count == 1) {
            val (id, item) = activeDownloads.entries.first()
            manager.cancel(individualNotificationId(id))
            buildDownloadNotification(id, item)
        } else {
            activeDownloads.forEach { (id, item) ->
                manager.notify(individualNotificationId(id), buildDownloadNotification(id, item))
            }
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ilyro_download)
                .setContentTitle("ILYRO · $count downloads")
                .setContentText("Manage downloads in ILYRO")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(0, 0, true)
                .apply { launchPendingIntent()?.let(::setContentIntent) }
                .build()
        }

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        val promoted = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, foregroundNotification, serviceType)
        }.isSuccess
        if (!promoted) {
            pauseTrackedForForegroundStop()
            stopAfterForegroundLoss()
        }
    }

    private fun formatBytes(bytes: Long): String = Companion.formatBytes(bytes)

    companion object {
        private const val CHANNEL_ID = "ilyro_background_downloads"
        private const val NOTIFICATION_ID = 0x494C59
        private const val ACTION_TRACK = "com.ilyro.browser.action.TRACK_DOWNLOAD"
        private const val ACTION_PAUSE = "com.ilyro.browser.action.PAUSE_DOWNLOAD"
        private const val ACTION_RESUME = "com.ilyro.browser.action.RESUME_DOWNLOAD"
        private const val ACTION_CANCEL = "com.ilyro.browser.action.CANCEL_DOWNLOAD"
        private const val EXTRA_ID = "download_id"
        private const val EXTRA_NAME = "download_name"

        @Volatile
        private var instanceRef: WeakReference<DownloadKeepAliveService>? = null
        @Volatile
        private var controlHandler: ((Long, Boolean) -> Boolean)? = null
        @Volatile
        private var cancelHandler: ((Long) -> Boolean)? = null

        private fun liveService(): DownloadKeepAliveService? = instanceRef?.get()

        private const val MAX_FINISHED_BEFORE_START_IDS = 128
        private const val MAX_FOREGROUND_UNAVAILABLE_IDS = 128
        private val finishedBeforeStart = ConcurrentHashMap.newKeySet<Long>()
        private val foregroundUnavailableIds = ConcurrentHashMap.newKeySet<Long>()
        private val activeIds = ConcurrentHashMap.newKeySet<Long>()
        private val startedIds = ConcurrentHashMap.newKeySet<Long>()

        fun hasActiveDownloads(): Boolean = activeIds.isNotEmpty()

        fun setControlHandler(handler: (Long, Boolean) -> Boolean) {
            controlHandler = handler
        }

        fun setCancelHandler(handler: (Long) -> Boolean) {
            cancelHandler = handler
        }

        fun track(context: Context, id: Long, fileName: String): Boolean {
            val appContext = context.applicationContext
            finishedBeforeStart.remove(id)
            foregroundUnavailableIds.remove(id)
            activeIds.add(id)
            ensureChannel(appContext)

            // Post a visible starting notification synchronously. The service then promotes the
            // exact same notification to foreground status, removing the perceptible startup lag.
            postStartingNotification(appContext, id, fileName)

            val intent = Intent(appContext, DownloadKeepAliveService::class.java)
                .setAction(ACTION_TRACK)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NAME, fileName)
            val started = runCatching {
                ContextCompat.startForegroundService(appContext, intent)
                true
            }.getOrDefault(false)

            if (!started) {
                activeIds.remove(id)
                startedIds.remove(id)
                finishedBeforeStart.remove(id)
                markForegroundUnavailable(id)

                // Real persisted ids can be paused immediately. Provisional ids stay marked and
                // replace() pauses the real record once Gecko/HLS resolves it.
                val paused = runCatching { controlHandler?.invoke(id, true) == true }
                    .getOrDefault(false)
                if (paused) foregroundUnavailableIds.remove(id)

                appContext.getSystemService(NotificationManager::class.java).apply {
                    cancel(individualNotificationId(id))
                    cancel(NOTIFICATION_ID)
                }
            }
            return started
        }

        fun replace(context: Context, oldId: Long, newId: Long, fileName: String) {
            if (oldId == newId) return
            val appContext = context.applicationContext
            val foregroundWasUnavailable = foregroundUnavailableIds.remove(oldId)
            activeIds.remove(oldId)

            if (foregroundWasUnavailable) {
                startedIds.remove(oldId)
                finishedBeforeStart.remove(oldId)
                activeIds.remove(newId)
                val paused = runCatching { controlHandler?.invoke(newId, true) == true }
                    .getOrDefault(false)
                if (!paused) markForegroundUnavailable(newId)
                appContext.getSystemService(NotificationManager::class.java).apply {
                    cancel(individualNotificationId(oldId))
                    cancel(individualNotificationId(newId))
                    cancel(NOTIFICATION_ID)
                }
                return
            }

            activeIds.add(newId)

            val live = liveService()
            val oldStartAlreadyDelivered = startedIds.remove(oldId)
            if (oldStartAlreadyDelivered || live != null) {
                startedIds.add(newId)
            }
            // Ignore a stale ACTION_TRACK only when the provisional start has not arrived yet.
            if (oldStartAlreadyDelivered) {
                finishedBeforeStart.remove(oldId)
            } else {
                markFinishedBeforeStart(oldId)
            }
            finishedBeforeStart.remove(newId)
            ensureChannel(appContext)

            if (live != null) {
                live.replaceTracked(oldId, newId, fileName)
            } else {
                postStartingNotification(appContext, newId, fileName)
                val intent = Intent(appContext, DownloadKeepAliveService::class.java)
                    .setAction(ACTION_TRACK)
                    .putExtra(EXTRA_ID, newId)
                    .putExtra(EXTRA_NAME, fileName)
                runCatching { ContextCompat.startForegroundService(appContext, intent) }
            }
        }

        fun updateProgress(id: Long, fileName: String, downloaded: Long, total: Long) {
            liveService()?.updateTracked(id, fileName, downloaded, total)
        }

        fun setPaused(id: Long, paused: Boolean) {
            liveService()?.setPausedTracked(id, paused)
        }

        fun finish(context: Context, id: Long) {
            activeIds.remove(id)
            foregroundUnavailableIds.remove(id)
            val serviceAlreadyStartedThisId = startedIds.remove(id)
            if (serviceAlreadyStartedThisId) {
                finishedBeforeStart.remove(id)
            } else {
                markFinishedBeforeStart(id)
            }
            liveService()?.finishTracked(id)

            // Use the caller's application context only for this operation instead of retaining a
            // process-wide Context/Service reference in the companion object.
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager.cancel(individualNotificationId(id))
            if (activeIds.isEmpty() && liveService() == null) manager.cancel(NOTIFICATION_ID)
        }

        private fun markFinishedBeforeStart(id: Long) {
            // This is a race marker, not download history. Bound it so a failed/never-delivered
            // foreground-service start cannot leave an ever-growing process-wide set.
            if (finishedBeforeStart.size >= MAX_FINISHED_BEFORE_START_IDS) {
                finishedBeforeStart.clear()
            }
            finishedBeforeStart.add(id)
        }

        private fun markForegroundUnavailable(id: Long) {
            // This marker only bridges a failed/expired foreground-service start to a real
            // persisted download id. Keep it bounded in case Gecko never resolves the request.
            if (foregroundUnavailableIds.size >= MAX_FOREGROUND_UNAVAILABLE_IDS) {
                foregroundUnavailableIds.clear()
            }
            foregroundUnavailableIds.add(id)
        }

        private fun ensureChannel(context: Context) {
            // minSdk is 26, so notification channels are always available.
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Background downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps ILYRO downloads active in the background"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        private fun postStartingNotification(context: Context, id: Long, fileName: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.setAction(ACTION_OPEN_DOWNLOADS)
                ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val pauseIntent = Intent(context, DownloadKeepAliveService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_ID, id)
            val pausePendingIntent = PendingIntent.getService(
                context,
                actionRequestCode(id, false),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ilyro_download)
                .setContentTitle(fileName.ifBlank { "Download" })
                .setContentText("Starting download…")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(0, 0, true)
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        pausePendingIntent
                    ).build()
                )
                .apply { contentIntent?.let(::setContentIntent) }
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        private fun actionRequestCode(id: Long, paused: Boolean): Int {
            val folded = (id xor (id ushr 32)).toInt()
            return folded xor if (paused) 0x52534D else 0x504155
        }

        private fun cancelRequestCode(id: Long): Int {
            val folded = (id xor (id ushr 32)).toInt()
            return folded xor 0x43414E
        }

        private fun individualNotificationId(id: Long): Int {
            val folded = (id xor (id ushr 32)).toInt() and 0x0fffffff
            return 0x50000000 or folded
        }

        private fun formatBytes(bytes: Long): String {
            val safe = bytes.coerceAtLeast(0L)
            return when {
                safe >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", safe / (1024.0 * 1024.0 * 1024.0))
                safe >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
                safe >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", safe / 1024.0)
                else -> "$safe B"
            }
        }
    }
}
