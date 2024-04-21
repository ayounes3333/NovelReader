package my.noveldokusha.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import my.noveldokusha.R
import my.noveldokusha.repository.AppFileResolver
import my.noveldokusha.repository.AppRepository
import my.noveldokusha.tools.epub.epubImporter
import my.noveldokusha.tools.epub.epubParser
import my.noveldokusha.ui.browse.extractor.utils.OpenFileReceiver
import my.noveldokusha.utils.Extra_Uri
import my.noveldokusha.utils.NotificationsCenter
import my.noveldokusha.utils.asSequence
import my.noveldokusha.utils.isServiceRunning
import my.noveldokusha.utils.removeProgressBar
import my.noveldokusha.utils.text
import my.noveldokusha.utils.title
import my.noveldokusha.utils.tryAsResponse
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class EpubImportService : Service() {
    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var notificationsCenter: NotificationsCenter

    @Inject
    lateinit var appFileResolver: AppFileResolver

    private class IntentData : Intent {
        var uri by Extra_Uri()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, uri: Uri, openAfterImporting: Boolean) : super(ctx, EpubImportService::class.java) {
            this.uri = uri
            putExtra("openAfterImporting", openAfterImporting)
        }
        val openAfterImporting: Boolean
            get() = getBooleanExtra("openAfterImporting", false)
    }

    companion object {
        fun start(ctx: Context, uri: Uri, openAfterImporting: Boolean = false) {
            if (!isRunning(ctx))
                ContextCompat.startForegroundService(ctx, IntentData(ctx, uri, openAfterImporting))
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(EpubImportService::class.java)
    }

    private val channelName by lazy { getString(R.string.notification_channel_name_import_epub) }
    private val channelId = "Import EPUB"
    private val notificationId = channelId.hashCode()

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = notificationsCenter.showNotification(
            notificationId = notificationId,
            channelId = channelId,
            channelName = channelName,
        )
        startForeground(notificationId, notificationBuilder.build())
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val intentData = IntentData(intent)
        job = CoroutineScope(Dispatchers.IO).launch {
            tryAsResponse {
                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    title = getString(R.string.import_epub)
                    foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    setProgress(100, 0, true)
                }
                val inputStream = contentResolver.openInputStream(intentData.uri)
                if (inputStream == null) {
                    notificationsCenter.showNotification(
                        channelName = channelName,
                        channelId = channelId,
                        notificationId = "Import EPUB failure".hashCode()
                    ) {
                        text = getString(R.string.failed_get_file)
                        removeProgressBar()
                    }
                    return@tryAsResponse
                }

                val fileName = intentData.uri.toFile().name

                val epub = inputStream.use { epubParser(inputStream = it) }

                notificationsCenter.modifyNotification(
                    notificationBuilder,
                    notificationId = notificationId
                ) {
                    text = getString(R.string.importing_epub)
                }
                val url = epubImporter(
                    storageFolderName = fileName,
                    appFileResolver = appFileResolver,
                    appRepository = appRepository,
                    epub = epub,
                    addToLibrary = true
                )
                if (intentData.openAfterImporting) {
                    val fileIntent = Intent()
                    fileIntent.action = OpenFileReceiver.ACTION
                    fileIntent.setPackage(packageName)
                    fileIntent.putExtra("title", fileName)
                    fileIntent.putExtra("url", url)
                    sendBroadcast(fileIntent)
                }
            }.onError {
                Timber.e(it.exception)
                notificationsCenter.showNotification(
                    channelName = channelName,
                    channelId = channelId,
                    notificationId = "Import EPUB failure".hashCode()
                ) {
                    text = getString(R.string.failed_to_import_epub)
                    setSubText(it.message)
                    removeProgressBar()
                }
            }

            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}