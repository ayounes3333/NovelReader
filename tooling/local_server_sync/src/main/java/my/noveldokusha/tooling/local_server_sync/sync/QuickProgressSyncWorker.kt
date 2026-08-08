package my.noveldokusha.tooling.local_server_sync.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import my.noveldokusha.tooling.local_server_sync.manager.LocalServerSyncManager
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Lightweight worker that performs a quick library + chapter sync (no images,
 * no chapter bodies). Designed to run when the app is brought to the
 * foreground so the user's progress on other devices appears with low latency.
 */
@HiltWorker
class QuickProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: LocalServerSyncManager,
    private val syncRepository: LocalServerSyncRepository,
    private val tokenStorage: AuthTokenStorage
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "QuickProgressSyncWorker"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.KEEP,
                createOneTimeRequest()
            )
        }

        private fun createOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return OneTimeWorkRequestBuilder<QuickProgressSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        if (!syncManager.isUserAuthenticated()) return Result.success()
        val urls = tokenStorage.getServerUrls()
        if (urls.isEmpty()) {
            tokenStorage.getServerUrl()?.takeIf { it.isNotBlank() }?.let { /* single fallback below */ }
        }
        // Try each configured server until one responds.
        val candidates = if (urls.isNotEmpty()) urls else listOfNotNull(tokenStorage.getServerUrl())
        if (candidates.isEmpty()) {
            Timber.d("QuickProgressSyncWorker: no server URLs configured")
            return Result.success()
        }
        val reachable = candidates.firstOrNull { syncRepository.pingServer(it) }
            ?: return if (runAttemptCount < 2) Result.retry() else Result.success()
        tokenStorage.saveServerUrl(reachable)

        return syncManager.performQuickProgressSync().fold(
            onSuccess = { Result.success() },
            onFailure = {
                Timber.w(it, "QuickProgressSyncWorker failed")
                if (runAttemptCount < 2) Result.retry() else Result.success()
            }
        )
    }
}
