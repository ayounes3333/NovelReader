package my.noveldokusha.tooling.local_server_sync.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import my.noveldokusha.tooling.local_server_sync.manager.LocalServerSyncManager
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that performs a background sync when WiFi connectivity is detected.
 * Tries each configured server URL until one responds, then syncs.
 */
@HiltWorker
class WiFiSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: LocalServerSyncManager,
    private val syncRepository: LocalServerSyncRepository,
    private val tokenStorage: AuthTokenStorage
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "WiFiSyncWorker"
        private const val TAG_ONE_TIME = "WiFiSyncWorker_OneTime"

        fun createOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            return OneTimeWorkRequestBuilder<WiFiSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .addTag(TAG_ONE_TIME)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        Timber.d("WiFiSyncWorker: starting background sync")

        if (!tokenStorage.isAutoSyncEnabled()) {
            Timber.d("WiFiSyncWorker: auto-sync disabled, skipping")
            return Result.success()
        }

        // Try to discover a reachable server from configured URLs
        val serverUrls = tokenStorage.getServerUrls()
        if (serverUrls.isEmpty()) {
            Timber.d("WiFiSyncWorker: no server URLs configured, skipping")
            return Result.success()
        }

        var reachableUrl: String? = null
        for (url in serverUrls) {
            Timber.d("WiFiSyncWorker: trying server at $url")
            if (syncRepository.pingServer(url)) {
                reachableUrl = url
                Timber.d("WiFiSyncWorker: server reachable at $url")
                break
            }
        }

        if (reachableUrl == null) {
            Timber.d("WiFiSyncWorker: no server reachable, will retry")
            return Result.retry()
        }

        // Update the active server URL
        tokenStorage.saveServerUrl(reachableUrl)

        // Perform sync
        return try {
            if (!syncManager.isUserAuthenticated()) {
                Timber.d("WiFiSyncWorker: user not authenticated, skipping")
                return Result.success()
            }

            val result = syncManager.performCompleteSync()
            result.fold(
                onSuccess = {
                    Timber.d("WiFiSyncWorker: sync completed successfully")
                    Result.success()
                },
                onFailure = { e ->
                    Timber.e(e, "WiFiSyncWorker: sync failed")
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "WiFiSyncWorker: unexpected error")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
