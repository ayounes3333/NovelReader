package my.noveldoksuha.data

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import my.noveldokusha.tooling.ai_engine.domain.AiErrorCategory
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Input of a bulk AI job, persisted before enqueueing so the worker can
 * load arbitrarily large chapter lists (WorkManager Data is size-limited)
 * and so progress/failures survive process death.
 */
data class AiBulkJobInput(
    val jobId: String,
    val bookUrl: String,
    val bookTitle: String,
    val chaptersUrl: List<String>,
    val taskKey: String,
    val taskMeta: String,
    val model: String?,
    val regenerate: Boolean,
    val createdAtEpochMilli: Long = System.currentTimeMillis(),
) {
    fun toTask(): AiTask = when (taskKey) {
        AiTask.CreateTitle.key -> AiTask.CreateTitle
        AiTask.CreateSummary.key -> AiTask.CreateSummary
        AiTask.Translate("").key -> AiTask.Translate(targetLanguage = taskMeta)
        AiTask.FixNarration.key -> AiTask.FixNarration
        AiTask.FixGrammar.key -> AiTask.FixGrammar
        else -> AiTask.Custom(promptId = taskMeta.toLongOrNull() ?: 0L)
    }
}

data class AiBulkJobProgress(
    val done: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val currentChapterUrl: String? = null,
    val finished: Boolean = false,
    val failures: List<Failure> = emptyList(),
) {
    /**
     * A failed chapter of a bulk job. [category] and [epochTimeMilli] may be
     * absent in entries persisted by older app versions (Gson leaves them
     * null/0), so they are nullable with accessors that degrade gracefully.
     */
    data class Failure(
        val chapterUrl: String,
        val message: String,
        val category: String? = null,
        val epochTimeMilli: Long = 0L,
    ) {
        val errorCategory: AiErrorCategory
            get() = category
                ?.let { runCatching { AiErrorCategory.valueOf(it) }.getOrNull() }
                ?: AiErrorCategory.UNKNOWN
    }
}

data class AiBulkJob(
    val input: AiBulkJobInput,
    val progress: AiBulkJobProgress,
)

/**
 * Persists bulk AI jobs (input + progress) in a private preferences file as
 * Gson JSON. Keeps only the most recent [MAX_KEPT_JOBS] jobs.
 *
 * All mutations are also mirrored into an in-memory state flow so UI can
 * observe job progress reactively (the worker runs in the same process).
 */
@Singleton
class AiBulkJobStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        context.getSharedPreferences("ai_bulk_jobs", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    private val jobsFlow = MutableStateFlow(loadAll())

    private fun loadAll(): Map<String, AiBulkJob> =
        preferences.all.keys.mapNotNull { key ->
            decode(preferences.getString(key, null) ?: return@mapNotNull null)
        }.associateBy { it.input.jobId }

    /**
     * Emits the most recent job for [bookUrl] (any state) on every change,
     * plus the current value on collection.
     */
    fun flowOfLatestJobForBook(bookUrl: String): Flow<AiBulkJob?> =
        jobsFlow.map { jobs ->
            jobs.values
                .filter { it.input.bookUrl == bookUrl }
                .maxByOrNull { it.input.createdAtEpochMilli }
        }.distinctUntilChanged()

    fun createJob(
        bookUrl: String,
        bookTitle: String,
        chaptersUrl: List<String>,
        task: AiTask,
        model: String?,
        regenerate: Boolean,
    ): AiBulkJob {
        val input = AiBulkJobInput(
            jobId = UUID.randomUUID().toString(),
            bookUrl = bookUrl,
            bookTitle = bookTitle,
            chaptersUrl = chaptersUrl,
            taskKey = task.key,
            taskMeta = task.metaKey,
            model = model,
            regenerate = regenerate,
        )
        val job = AiBulkJob(
            input = input,
            progress = AiBulkJobProgress(total = chaptersUrl.size)
        )
        save(job)
        return job
    }

    fun getJob(jobId: String): AiBulkJob? =
        preferences.getString(prefKey(jobId), null)?.let(::decode)

    fun updateProgress(jobId: String, progress: AiBulkJobProgress) {
        val job = getJob(jobId) ?: return
        save(job.copy(progress = progress))
    }

    /** Most recent job for a book (any state), used by the UI for status/retry. */
    fun getLatestJobForBook(bookUrl: String): AiBulkJob? =
        jobsFlow.value.values
            .filter { it.input.bookUrl == bookUrl }
            .maxByOrNull { it.input.createdAtEpochMilli }

    private fun save(job: AiBulkJob) {
        preferences.edit().putString(prefKey(job.input.jobId), gson.toJson(job)).apply()
        jobsFlow.value = jobsFlow.value + (job.input.jobId to job)
        prune()
    }

    private fun prune() {
        val entries = preferences.all.keys
            .mapNotNull { key ->
                val value = preferences.getString(key, null) ?: return@mapNotNull null
                key to (decode(value)?.input?.createdAtEpochMilli ?: 0L)
            }
            .sortedByDescending { it.second }
            .drop(MAX_KEPT_JOBS)
        entries.forEach { preferences.edit().remove(it.first).apply() }
        if (entries.isNotEmpty())
            jobsFlow.value = jobsFlow.value - entries.map { it.first }.toSet()
    }

    private fun decode(json: String?): AiBulkJob? = runCatching {
        gson.fromJson(json, AiBulkJob::class.java)
    }.getOrNull()

    private fun prefKey(jobId: String) = "job_$jobId"

    private companion object {
        const val MAX_KEPT_JOBS = 10
    }
}
