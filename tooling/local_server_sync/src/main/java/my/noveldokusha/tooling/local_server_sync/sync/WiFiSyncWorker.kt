package my.noveldokusha.tooling.local_server_sync.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.local_server_sync.manager.LocalServerSyncManager
import my.noveldokusha.tooling.local_server_sync.manager.SyncProgress
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Bulk sync worker. Runs in the foreground with a progress notification so
 * Android does not kill it mid-transfer when the user navigates away.
 *
 * Triggered by [WiFiSyncScheduler] on home-WiFi BSSID matches. Constraints
 * enforce UNMETERED network.
 */
@HiltWorker
class WiFiSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: LocalServerSyncManager,
    private val syncRepository: LocalServerSyncRepository,
    private val tokenStorage: AuthTokenStorage
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "WiFiSyncWorker"

        fun createOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            return OneTimeWorkRequestBuilder<WiFiSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun getForegroundInfo() =
        SyncNotifications.foregroundInfo(
            context = context,
            title = "Syncing library",
            text = "Preparing\u2026",
            indeterminate = true
        )

    override suspend fun doWork(): Result = coroutineScope {
        Timber.d("WiFiSyncWorker: starting")
        setForeground(getForegroundInfo())

        if (!tokenStorage.isAutoSyncEnabled()) return@coroutineScope Result.success()
        if (!syncManager.isUserAuthenticated()) return@coroutineScope Result.success()

        val candidates = tokenStorage.getServerUrls().ifEmpty {
            listOfNotNull(tokenStorage.getServerUrl()?.takeIf { it.isNotBlank() })
        }
        if (candidates.isEmpty()) {
            Timber.d("WiFiSyncWorker: no servers configured")
            return@coroutineScope Result.success()
        }

        val reachable = candidates.firstOrNull { syncRepository.pingServer(it) }
            ?: return@coroutineScope if (runAttemptCount < 3) Result.retry() else Result.failure()
        tokenStorage.saveServerUrl(reachable)

        // Republish notifications as syncManager.progress evolves.
        val progressJob: Job = launch {
            syncManager.progress.collect { progress ->
                val (title, text, current, total, indeterminate) = describe(progress)
                runCatching {
                    setForeground(
                        SyncNotifications.foregroundInfo(
                            context = context,
                            title = title,
                            text = text,
                            progress = current,
                            max = total,
                            indeterminate = indeterminate
                        )
                    )
                }.onFailure { Timber.w(it, "setForeground failed") }
            }
        }

        val result = syncManager.performBulkSync()
        progressJob.cancel()

        result.fold(
            onSuccess = {
                Timber.d("WiFiSyncWorker: bulk sync done")
                Result.success()
            },
            onFailure = { e ->
                Timber.w(e, "WiFiSyncWorker: bulk sync failed")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        )
    }

    private data class Display(
        val title: String,
        val text: String,
        val current: Int,
        val total: Int,
        val indeterminate: Boolean
    )

    private fun describe(progress: SyncProgress): Display = when (progress) {
        is SyncProgress.Idle -> Display("Syncing library", "Preparing\u2026", 0, 0, true)
        is SyncProgress.Running -> Display(
            title = "Syncing library",
            text = progress.stage,
            current = progress.current,
            total = progress.total,
            indeterminate = progress.total <= 0
        )
        is SyncProgress.Done -> Display("Sync complete", "Up to date", 0, 0, false)
        is SyncProgress.Error -> Display("Sync error", progress.message, 0, 0, false)
    }
}
