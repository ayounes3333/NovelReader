package my.noveldokusha.tooling.quick_setup.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupExporter
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class QuickSetupServerService : Service() {

    @Inject
    lateinit var exporter: QuickSetupExporter

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var server: QuickSetupServer? = null

    companion object {
        private const val CHANNEL_ID = "quick_setup_server"
        private const val NOTIFICATION_ID = 7701
        const val EXTRA_PORT = "port"
        const val EXTRA_SESSION_TOKEN = "session_token"
        const val DEFAULT_PORT = 8765

        fun start(context: Context, port: Int = DEFAULT_PORT, sessionToken: String) {
            val intent = Intent(context, QuickSetupServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QuickSetupServerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
            ?: run {
                Timber.e("No session token provided to QuickSetupServerService")
                stopSelf()
                return START_NOT_STICKY
            }

        startForeground(NOTIFICATION_ID, buildNotification("Preparing Quick Setup server..."))

        scope.launch {
            try {
                val serverInstance = QuickSetupServer(
                    port = port,
                    exporter = exporter,
                    sessionToken = sessionToken,
                    onClientConnected = {
                        updateNotification("Device connected. Transferring data...")
                    },
                    onTransferProgress = { progress ->
                        updateNotification(progress)
                    },
                    onCancelRequested = {
                        updateNotification("Transfer cancelled")
                    },
                    onIdleTimeout = {
                        updateNotification("Server shut down due to inactivity")
                        stopSelf()
                    },
                )
                serverInstance.startWithIdleTimeout()
                server = serverInstance
                updateNotification("Waiting for connection on port $port...")
                Timber.i("Quick Setup server started on port $port")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Quick Setup server")
                updateNotification("Failed to start server: ${e.message}")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Setup Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick Setup file transfer server"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Quick Setup")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
