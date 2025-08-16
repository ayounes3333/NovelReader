package my.noveldokusha.tooling.backup_restore

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldoksuha.coreui.states.NotificationsCenter
import my.noveldoksuha.coreui.states.removeProgressBar
import my.noveldoksuha.coreui.states.text
import my.noveldoksuha.coreui.states.title
import my.noveldoksuha.data.AppRepository
import my.noveldoksuha.data.BookChaptersRepository
import my.noveldoksuha.data.ChapterBodyRepository
import my.noveldoksuha.data.DownloaderRepository
import my.noveldoksuha.data.LibraryBooksRepository
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.core.utils.Extra_Uri
import my.noveldokusha.core.utils.isServiceRunning
import my.noveldokusha.feature.local_database.AppDatabase
import timber.log.Timber
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

@AndroidEntryPoint
class RestoreDataService : Service() {
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var appFileResolver: AppFileResolver

    @Inject
    lateinit var notificationsCenter: NotificationsCenter

    @Inject
    lateinit var appCoroutineScope: AppCoroutineScope

    @Inject
    lateinit var downloaderRepository: DownloaderRepository

    private class IntentData : Intent {
        var uri by Extra_Uri()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, uri: Uri) : super(ctx, RestoreDataService::class.java) {
            this.uri = uri
        }
    }

    companion object {
        fun start(ctx: Context, uri: Uri) {
            if (!isRunning(ctx))
                ContextCompat.startForegroundService(ctx, IntentData(ctx, uri))
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(RestoreDataService::class.java)
    }

    private val channelName by lazy { getString(R.string.notification_channel_name_restore_backup) }
    private val channelId = "Restore backup"
    private val notificationId = channelId.hashCode()


    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationBuilder = notificationsCenter.showNotification(
            notificationId = notificationId,
            channelId = channelId,
            channelName = channelName
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

        if (job?.isActive == true) return START_NOT_STICKY
        job = CoroutineScope(Dispatchers.IO).launch {
            tryAsResponse {
                restoreData(intentData.uri)
                appRepository.eventDataRestored.emit(Unit)
            }.onError {
                Timber.e(it.exception)
            }

            stopSelf(startId)
        }
        return START_STICKY
    }

    /**
     * Restore data function. Restores the library and images data given an uri.
     * The uri must point to a zip file where there must be a root file
     * "database.sqlite3" and an optional "books" folder where all the images
     * are stored (each subfolder is a book with its own structure).
     *
     * This function assumes the READ_EXTERNAL_STORAGE permission is granted.
     * This function will also show a status notificaton of the restoration progress.
     */
    private suspend fun restoreData(uri: Uri) = withContext(Dispatchers.IO) {
        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            title = getString(R.string.restore_data)
            text = getString(R.string.loading_data)
            setProgress(100, 0, true)
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry: ZipEntry?
                while (zipStream.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) continue

                    when {
                        currentEntry.name == "database.sqlite3" -> processDatabaseEntry(zipStream)
                        currentEntry.name.startsWith("books/") -> processBookEntry(zipStream, currentEntry)
                    }
                    zipStream.closeEntry()
                }
            }
        } ?: run {
            showErrorNotification(R.string.failed_to_restore_cant_access_file)
            return@withContext
        }

        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            removeProgressBar()
            text = getString(R.string.data_restored)
        }
    }

    private suspend fun processDatabaseEntry(zipStream: ZipInputStream) {
        val tempFile = File.createTempFile("restore_db", ".tmp", context.cacheDir).apply {
            deleteOnExit()
        }

        try {
            // Stream database to temp file
            tempFile.outputStream().use { output ->
                zipStream.copyTo(output)
            }

            // Check database version before attempting to restore
            val backupVersion = getDatabaseVersion(tempFile)
            val currentVersion = 9 // Current database version

            notificationsCenter.modifyNotification(
                notificationBuilder,
                notificationId = notificationId
            ) {
                title = getString(R.string.restore_data)
                text = if (backupVersion < currentVersion) {
                    "Migrating database from version $backupVersion to $currentVersion..."
                } else {
                    "Processing database..."
                }
                setProgress(100, 25, false)
            }

            // Create database from temp file with automatic migration support
            val backupDatabase = TempDatabase(
                newDatabase = AppDatabase.createRoomFromFile(context, "temp_database", tempFile),
                context = context,
                appFileResolver = appFileResolver,
                appCoroutineScope = appCoroutineScope,
                downloaderRepository = downloaderRepository
            )

            notificationsCenter.modifyNotification(
                notificationBuilder,
                notificationId = notificationId
            ) {
                text = "Processing restored data..."
                setProgress(100, 50, false)
            }

            // Process data in batches
            processInBatches(backupDatabase.libraryBooks, backupDatabase.bookChapters, backupDatabase.chapterBody)

            backupDatabase.close()
        } catch (e: Exception) {
            when {
                e.message?.contains("migration", ignoreCase = true) == true -> {
                    Timber.e(e, "Database migration failed during restore")
                    showErrorNotification(R.string.failed_to_restore_invalid_backup_database)
                }
                e.message?.contains("sqlite", ignoreCase = true) == true -> {
                    Timber.e(e, "Database restore failed - incompatible or corrupted database")
                    showErrorNotification(R.string.failed_to_restore_invalid_backup_database)
                }
                else -> {
                    Timber.e(e, "Database restore failed")
                    showErrorNotification(R.string.failed_to_restore_invalid_backup_database)
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Get the database version from a SQLite file
     */
    private fun getDatabaseVersion(dbFile: File): Int {
        return try {
            // Simple approach: try to open the database and check if it has the latest schema
            // If it fails, we assume it's an older version that needs migration
            val testDb = AppDatabase.createRoomFromFile(context, "version_check_temp", dbFile)
            testDb.closeDatabase()
            AppDatabase.deleteDatabaseFiles(context, "version_check_temp")
            9 // If successful, it's already at current version
        } catch (e: Exception) {
            Timber.w(e, "Could not determine database version, assuming older version")
            1 // Assume it's an older version that needs migration
        }
    }

    private suspend fun processInBatches(
        libraryBooks: LibraryBooksRepository,
        bookChapters: BookChaptersRepository,
        chapterBody: ChapterBodyRepository
    ) {
        // Process library books in batches
        val booksBatchSize = 100
        var bookOffset = 0
        do {
            println("processing from book offset $bookOffset")
            val booksBatch = withContext(Dispatchers.IO) {
                libraryBooks.getBatch(bookOffset, booksBatchSize)
            }
            appRepository.libraryBooks.insertReplace(booksBatch)
            bookOffset += booksBatchSize
        } while (booksBatch.size == booksBatchSize)

        // Process chapters in batches
        val chaptersBatchSize = 200
        var chaptersOffset = 0
        do {
            println("processing from chapter offset $chaptersOffset")
            val chaptersBatch = withContext(Dispatchers.IO) {
                bookChapters.getBatch(chaptersOffset, chaptersBatchSize)
            }
            appRepository.bookChapters.insert(chaptersBatch)
            chaptersOffset += chaptersBatchSize
        } while (chaptersBatch.size == chaptersBatchSize)

        // Process chapter bodies in smallest batches
        val bodiesBatchSize = 50
        var bodiesOffset = 0
        do {
            println("processing from body offset $bodiesOffset")
            val bodiesBatch = withContext(Dispatchers.IO) {
                chapterBody.getBatch(bodiesOffset, bodiesBatchSize)
            }
            appRepository.chapterBody.insertReplace(bodiesBatch)
            bodiesOffset += bodiesBatchSize
        } while (bodiesBatch.size == bodiesBatchSize)
    }

    private fun processBookEntry(zipStream: ZipInputStream, entry: ZipEntry) {
        val targetFile = File(appRepository.settings.folderBooks.parentFile, entry.name)
        if (targetFile.isDirectory) return

        targetFile.parentFile?.mkdirs()
        if (targetFile.parentFile?.exists() != true) return

        notificationsCenter.modifyNotification(
            notificationBuilder,
            notificationId = notificationId
        ) {
            text = getString(R.string.adding_images)
        }

        targetFile.outputStream().use { output ->
            zipStream.copyTo(output)
        }
    }

    private fun showErrorNotification(errorResId: Int) {
        notificationsCenter.showNotification(
            channelName = channelName,
            channelId = channelId,
            notificationId = "Backup restore failure".hashCode()
        ) {
            removeProgressBar()
            text = getString(errorResId)
        }
    }

    private class TempDatabase(
        val newDatabase: AppDatabase,
        val context: Context,
        appFileResolver: AppFileResolver,
        appCoroutineScope: AppCoroutineScope,
        downloaderRepository: DownloaderRepository
    ) {
        val bookChapters = BookChaptersRepository(
            chapterDao = newDatabase.chapterDao(),
        )

        val chapterBody = ChapterBodyRepository(
            chapterBodyDao = newDatabase.chapterBodyDao(),
            appDatabase = newDatabase,
            bookChaptersRepository = bookChapters,
            downloaderRepository = downloaderRepository
        )

        val libraryBooks = LibraryBooksRepository(
            libraryDao = newDatabase.libraryDao(),
            appDatabase = newDatabase,
            context = context,
            appFileResolver = appFileResolver,
            appCoroutineScope = appCoroutineScope
        )

        fun close() {
            newDatabase.closeDatabase()
            AppDatabase.deleteDatabaseFiles(context, "temp_database")
        }
    }
}