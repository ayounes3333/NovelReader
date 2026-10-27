package my.noveldokusha.tooling.application_workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldoksuha.coreui.states.NotificationsCenter
import my.noveldoksuha.coreui.states.text
import my.noveldoksuha.coreui.states.title
import my.noveldoksuha.data.AiBulkJobProgress
import my.noveldoksuha.data.AiBulkJobStore
import my.noveldoksuha.data.AiRepository
import my.noveldokusha.core.Response
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.tooling.ai_engine.domain.AiErrorCategory
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import timber.log.Timber

/**
 * Runs an AI task over a set of chapters sequentially. Job input (chapter
 * list, task, model, regenerate flag) is loaded from [AiBulkJobStore] by
 * [DATA_JOB_ID]; the store is also where per-chapter progress and failures
 * are persisted, which is what the UI reads for status and retry.
 *
 * Resume behavior: chapters that already have an active revision for the
 * task are skipped unless `regenerate` was set, so a stopped and re-run
 * job continues where it left off.
 */
@HiltWorker
internal class AiBulkTaskWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val aiRepository: AiRepository,
    private val jobStore: AiBulkJobStore,
    private val notificationsCenter: NotificationsCenter,
    private val navigationRoutes: NavigationRoutes,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val TAG = "AiBulkTask"
        private const val DATA_JOB_ID = "jobId"
        private const val CHANNEL_ID = "AI bulk tasks"

        fun createWorkName(jobId: String) = "AiBulk:$jobId"

        fun enqueue(workManager: WorkManager, jobId: String) {
            val request = OneTimeWorkRequestBuilder<AiBulkTaskWorker>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf(DATA_JOB_ID to jobId))
                .build()
            workManager.beginUniqueWork(
                createWorkName(jobId),
                ExistingWorkPolicy.KEEP,
                request
            ).enqueue()
        }
    }

    private val notificationId = "AiBulk:$id".hashCode()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(DATA_JOB_ID)
            ?: return Result.failure()

        val job = jobStore.getJob(jobId)
            ?: return Result.failure(workDataOf("error" to "Unknown job $jobId"))

        val task = job.input.toTask()
        Timber.d("AiBulkTaskWorker: starting ${task.key} over ${job.input.chaptersUrl.size} chapters")

        val failures = mutableListOf<AiBulkJobProgress.Failure>()
        var done = 0
        var skipped = 0

        withContext(Dispatchers.Main) {
            showProgressNotification(job, task, done, skipped, job.input.chaptersUrl.size)
        }

        for (chapterUrl in job.input.chaptersUrl) {
            // Resume support: skip chapters that already have the revision
            if (!job.input.regenerate) {
                val existing = aiRepository.getActiveRevision(chapterUrl, task)
                if (existing != null) {
                    skipped++
                    done++
                    jobStore.updateProgress(
                        jobId,
                        AiBulkJobProgress(
                            done = done,
                            skipped = skipped,
                            total = job.input.chaptersUrl.size,
                            currentChapterUrl = chapterUrl,
                            finished = false,
                            failures = failures,
                        )
                    )
                    continue
                }
            }

            val result = aiRepository.runTask(
                chapterUrl = chapterUrl,
                task = task,
                model = job.input.model,
                onChunkProgress = { chunkDone, chunkTotal ->
                    // Long chapters are processed in several LLM calls;
                    // reflect the inner progress in the notification.
                    withContext(Dispatchers.Main) {
                        showProgressNotification(
                            job, task, done, skipped, job.input.chaptersUrl.size,
                            chunkLabel = context.getString(
                                my.noveldokusha.strings.R.string.notification_ai_bulk_chunk,
                                chunkDone + 1, chunkTotal
                            )
                        )
                    }
                },
            )
            if (result is Response.Error) {
                Timber.w("AiBulkTaskWorker: chapter failed: ${result.message}")
                failures += AiBulkJobProgress.Failure(
                    chapterUrl = chapterUrl,
                    message = result.message.take(500),
                    category = AiErrorCategory.fromMessage(result.message).name,
                    epochTimeMilli = System.currentTimeMillis(),
                )
            }

            done++
            withContext(Dispatchers.Main) {
                showProgressNotification(job, task, done, skipped, job.input.chaptersUrl.size)
            }
            jobStore.updateProgress(
                jobId,
                AiBulkJobProgress(
                    done = done,
                    skipped = skipped,
                    total = job.input.chaptersUrl.size,
                    currentChapterUrl = chapterUrl,
                    finished = false,
                    failures = failures,
                )
            )
        }

        jobStore.updateProgress(
            jobId,
            AiBulkJobProgress(
                done = done,
                skipped = skipped,
                total = job.input.chaptersUrl.size,
                currentChapterUrl = null,
                finished = true,
                failures = failures,
            )
        )

        withContext(Dispatchers.Main) {
            showFinishedNotification(job, task, done, failures.size)
        }

        Timber.d("AiBulkTaskWorker: finished. done=$done failures=${failures.size}")
        return Result.success()
    }

    /** Opens the book's chapters list with the AI bulk sheet expanded. */
    private fun openBookPendingIntent(job: my.noveldoksuha.data.AiBulkJob): PendingIntent {
        val intent = navigationRoutes.chapters(
            context,
            BookMetadata(url = job.input.bookUrl, title = job.input.bookTitle)
        ).putExtra(NavigationRoutes.EXTRA_OPEN_AI_BULK_SHEET, true)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showProgressNotification(
        job: my.noveldoksuha.data.AiBulkJob,
        task: AiTask,
        done: Int,
        skipped: Int,
        total: Int,
        chunkLabel: String? = null,
    ) {
        val title = context.getString(
            my.noveldokusha.strings.R.string.notification_ai_bulk_running,
            taskLabel(task),
            done - skipped,
            total - skipped
        )
        notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = context.getString(my.noveldokusha.strings.R.string.notification_channel_name_ai_bulk),
            notificationId = notificationId,
            importance = android.app.NotificationManager.IMPORTANCE_LOW
        ) {
            this.title = if (chunkLabel != null) "$title · $chunkLabel" else title
            text = job.input.bookTitle
            setProgress(total, done, false)
            setContentIntent(openBookPendingIntent(job))
        }
    }

    private fun showFinishedNotification(
        job: my.noveldoksuha.data.AiBulkJob,
        task: AiTask,
        done: Int,
        failureCount: Int,
    ) {
        if (failureCount == 0) {
            notificationsCenter.close(notificationId = notificationId)
            return
        }
        notificationsCenter.showNotification(
            channelId = CHANNEL_ID,
            channelName = context.getString(my.noveldokusha.strings.R.string.notification_channel_name_ai_bulk),
            notificationId = notificationId,
            importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
        ) {
            title = context.getString(
                my.noveldokusha.strings.R.string.notification_ai_bulk_finished_with_failures,
                taskLabel(task),
                done - failureCount,
                failureCount
            )
            text = job.input.bookTitle
            setContentIntent(openBookPendingIntent(job))
            setAutoCancel(true)
        }
    }

    private fun taskLabel(task: AiTask): String = when (task) {
        is AiTask.CreateTitle -> context.getString(my.noveldokusha.strings.R.string.ai_run_task_title)
        is AiTask.CreateSummary -> context.getString(my.noveldokusha.strings.R.string.ai_run_task_summary)
        is AiTask.FixNarration -> context.getString(my.noveldokusha.strings.R.string.ai_run_task_narration)
        is AiTask.FixGrammar -> context.getString(my.noveldokusha.strings.R.string.ai_run_task_grammar)
        is AiTask.Translate -> context.getString(my.noveldokusha.strings.R.string.ai_run_task_translate)
        is AiTask.Custom -> context.getString(my.noveldokusha.strings.R.string.ai_tools)
    }
}
