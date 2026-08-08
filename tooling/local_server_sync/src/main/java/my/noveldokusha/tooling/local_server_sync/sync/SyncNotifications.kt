package my.noveldokusha.tooling.local_server_sync.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Single place for sync-related notification scaffolding so the foreground
 * notifications posted from [WiFiSyncWorker] (and any future workers) stay
 * consistent.
 */
internal object SyncNotifications {
    const val CHANNEL_ID = "novelreader_sync"
    const val NOTIFICATION_ID = 4711

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Library sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress notifications while syncing library, chapters, and images."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean
    ): Notification {
        ensureChannel(context)
        val smallIcon = context.applicationInfo.icon.takeIf { it != 0 }
            ?: android.R.drawable.stat_sys_upload
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (indeterminate || max <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(max, progress.coerceIn(0, max), false)
        }
        return builder.build()
    }

    fun foregroundInfo(
        context: Context,
        title: String,
        text: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true
    ): ForegroundInfo {
        val notification = build(context, title, text, progress, max, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
