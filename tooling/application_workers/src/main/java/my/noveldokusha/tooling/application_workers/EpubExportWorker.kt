package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import my.noveldoksuha.data.DownloaderRepository
import my.noveldokusha.core.Response
import my.noveldokusha.epub_tooling.EpubCreator
import my.noveldokusha.network.NetworkClient
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that exports a book (identified by URL) as an EPUB file
 * into the device's public Downloads directory.
 *
 * Input data keys:
 *  - [DATA_BOOK_URL]   : String – the book page URL (must match a registered source)
 *  - [DATA_BOOK_TITLE] : String – human-readable title used as the EPUB filename
 *
 * Output data keys on success:
 *  - [DATA_OUTPUT_PATH] : String – absolute path of the written .epub file
 */
internal class EpubExportWorker(
    context: Context,
    workerParameters: WorkerParameters,
    private val downloaderRepository: DownloaderRepository,
    private val networkClient: NetworkClient,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val TAG = "EpubExport"

        private const val DATA_BOOK_URL = "bookUrl"
        private const val DATA_BOOK_TITLE = "bookTitle"
        const val DATA_OUTPUT_PATH = "outputPath"

        fun createRequest(bookUrl: String, bookTitle: String): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(DATA_BOOK_URL, bookUrl)
                .putString(DATA_BOOK_TITLE, bookTitle)
                .build()
            return OneTimeWorkRequestBuilder<EpubExportWorker>()
                .addTag(TAG)
                .setInitialDelay(0, TimeUnit.SECONDS)
                .setInputData(inputData)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val bookUrl = inputData.getString(DATA_BOOK_URL)
            ?: return Result.failure(failureData("Missing bookUrl input"))
        val bookTitle = inputData.getString(DATA_BOOK_TITLE)
            ?: bookUrl.trimEnd('/').substringAfterLast('/')

        Timber.d("EpubExportWorker: starting export for '$bookTitle' ($bookUrl)")

        return try {
            val outputFile = buildEpub(bookUrl, bookTitle)
            Timber.i("EpubExportWorker: epub written to ${outputFile.absolutePath}")
            Result.success(
                Data.Builder()
                    .putString(DATA_OUTPUT_PATH, outputFile.absolutePath)
                    .build()
            )
        } catch (e: Exception) {
            Timber.e(e, "EpubExportWorker: failed")
            Result.failure(failureData(e.message ?: "Unknown error"))
        }
    }

    // ── Core logic ─────────────────────────────────────────────────────────

    private suspend fun buildEpub(bookUrl: String, bookTitle: String): File {
        // 1. Fetch chapter list
        val chaptersResult = downloaderRepository.bookChaptersList(bookUrl)
        val chapterEntries = when (chaptersResult) {
            is Response.Error -> throw Exception("Could not load chapter list: ${chaptersResult.message}")
            is Response.Success -> chaptersResult.data
        }
        if (chapterEntries.isEmpty()) throw Exception("Book has no chapters: $bookUrl")

        // 2. Fetch cover image bytes (best-effort)
        val coverBytes: ByteArray? = try {
            when (val r = downloaderRepository.bookCoverImageUrl(bookUrl)) {
                is Response.Success -> r.data?.let { url ->
                    networkClient.get(url).body.bytes()
                }
                is Response.Error -> null
            }
        } catch (e: Exception) {
            Timber.w(e, "EpubExportWorker: could not fetch cover image")
            null
        }

        // 3. Fetch description (best-effort)
        val description: String? = try {
            when (val r = downloaderRepository.bookDescription(bookUrl)) {
                is Response.Success -> r.data
                is Response.Error -> null
            }
        } catch (e: Exception) {
            Timber.w(e, "EpubExportWorker: could not fetch description")
            null
        }

        // 4. Download each chapter body
        val chapters = chapterEntries.mapIndexed { index, chapter ->
            val chapterTitle = chapter.title.ifBlank { "Chapter ${index + 1}" }
            val body = try {
                when (val r = downloaderRepository.bookChapter(chapter.url)) {
                    is Response.Success -> r.data.body
                    is Response.Error -> {
                        Timber.w("EpubExportWorker: chapter '$chapterTitle' failed: ${r.message}")
                        ""
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "EpubExportWorker: chapter '$chapterTitle' threw")
                ""
            }
            EpubCreator.Chapter(title = chapterTitle, body = body)
        }

        // 5. Write epub
        val outputFile = resolveOutputFile(bookTitle)
        outputFile.outputStream().use { stream ->
            EpubCreator.write(
                outputStream = stream,
                title = bookTitle,
                author = null,
                description = description,
                coverImageBytes = coverBytes,
                chapters = chapters,
            )
        }
        return outputFile
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Returns a writable File in the public Downloads directory.
     * Illegal filename characters are replaced with underscores.
     */
    private fun resolveOutputFile(bookTitle: String): File {
        val safeTitle = bookTitle
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .take(200)
            .trim()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).also { it.mkdirs() }
        var file = File(downloadsDir, "$safeTitle.epub")
        var counter = 1
        while (file.exists()) {
            file = File(downloadsDir, "${safeTitle}_${counter++}.epub")
        }
        return file
    }

    private fun failureData(message: String) =
        Data.Builder().putString("error", message).build()
}
