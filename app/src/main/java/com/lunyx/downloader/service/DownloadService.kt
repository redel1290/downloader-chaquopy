package com.lunyx.downloader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lunyx.downloader.MainActivity
import com.lunyx.downloader.R
import com.lunyx.downloader.model.DownloadItem
import com.lunyx.downloader.utils.PythonDownloader
import com.lunyx.downloader.utils.Prefs
import kotlinx.coroutines.*

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIF_ID = 1
        const val ACTION_CANCEL = "action_cancel"

        fun start(ctx: Context, url: String, format: String, quality: String) {
            val intent = Intent(ctx, DownloadService::class.java).apply {
                putExtra("url", url)
                putExtra("format", format)
                putExtra("quality", quality)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            })
        }
    }

    inner class LocalBinder : Binder() {
        fun getService() = this@DownloadService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onResult: ((PythonDownloader.DownloadResult) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null
    var isDownloading = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            PythonDownloader.cancel()
            return START_NOT_STICKY
        }

        val url     = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        val format  = intent.getStringExtra("format") ?: "video"
        val quality = intent.getStringExtra("quality") ?: "best"

        startForeground(NOTIF_ID, buildNotification(getString(R.string.downloading_notification)))
        isDownloading = true

        scope.launch {
            val result = PythonDownloader.download(
                url = url,
                format = format,
                quality = quality,
                onStatus = { msg ->
                    onStatus?.invoke(msg)
                    updateNotification(msg)
                }
            )

            isDownloading = false

            if (result.success && result.item != null) {
                Prefs.addHistory(applicationContext, result.item)
            }

            withContext(Dispatchers.Main) {
                onResult?.invoke(result)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop), cancelIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
