package my.noveldokusha.tooling.firebase_sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import my.noveldokusha.tooling.firebase_sync.sync.LibrarySyncService
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncService: LibrarySyncService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting background library sync")
            val result = syncService.performIncrementalSync()

            if (result.isSuccess) {
                Timber.d("Background sync completed successfully")
                Result.success()
            } else {
                Timber.w("Background sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "Background sync worker failed")
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "library_sync_work"
        private const val SYNC_INTERVAL_HOURS = 6L

        fun enqueuePeriodicSync(context: Context) {
            val syncWorkRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
                SYNC_INTERVAL_HOURS, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )

            Timber.d("Periodic sync work enqueued")
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Periodic sync work cancelled")
        }
    }
}
