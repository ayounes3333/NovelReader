package my.noveldokusha.tooling.quick_setup.transfer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.tooling.quick_setup.PreferencesSnapshot
import my.noveldoksuha.data.AppRepository
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSetupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val appFileResolver: AppFileResolver,
    private val appPreferences: AppPreferences,
) {

    @Volatile
    var importState: ImportState = ImportState.Idle
        private set

    sealed class ImportState {
        data object Idle : ImportState()
        data class InProgress(
            val phase: TransferProgress.Phase,
            val itemsTransferred: Int,
            val totalItems: Int,
        ) : ImportState()
        data object Completed : ImportState()
        data class Error(val message: String) : ImportState()
    }

    suspend fun importBooks(books: List<Book>) = withContext(Dispatchers.IO) {
        appRepository.libraryBooks.insertReplace(books)
    }

    suspend fun importChapters(chapters: List<Chapter>) = withContext(Dispatchers.IO) {
        appRepository.bookChapters.insert(chapters)
    }

    suspend fun importChapterBodies(bodies: List<ChapterBody>) = withContext(Dispatchers.IO) {
        appRepository.chapterBody.insertReplace(bodies)
    }

    suspend fun importChapterBody(url: String, body: String) = withContext(Dispatchers.IO) {
        val chapterBody = ChapterBody(
            url = url,
            body = body,
            createdEpochTimeMilli = System.currentTimeMillis(),
            lastUpdatedEpochTimeMilli = System.currentTimeMillis(),
        )
        appRepository.chapterBody.insertReplace(listOf(chapterBody))
    }

    /**
     * Import an image file with path traversal protection and SHA-256 verification.
     * @param relativePath Path relative to the books folder (must not contain "..")
     * @param data Image bytes
     * @param expectedSha256 Expected SHA-256 hash of the data
     * @throws SecurityException if path traversal is detected
     * @throws IllegalArgumentException if SHA-256 doesn't match
     */
    suspend fun importImage(
        relativePath: String,
        data: ByteArray,
        expectedSha256: String? = null,
    ) = withContext(Dispatchers.IO) {
        val booksFolder = appFileResolver.folderBooks
        val targetFile = File(booksFolder, relativePath)

        // Path traversal protection: canonical path must be strictly inside the books folder
        val normalizedPath = targetFile.canonicalPath
        if (!normalizedPath.startsWith(booksFolder.canonicalPath + File.separator)) {
            throw SecurityException(
                "Path traversal detected: $relativePath resolves to $normalizedPath"
            )
        }

        // SHA-256 verification if expected hash is provided
        if (expectedSha256 != null) {
            val actualHash = data.sha256()
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                throw IllegalArgumentException(
                    "SHA-256 mismatch for $relativePath: expected=$expectedSha256, actual=$actualHash"
                )
            }
        }

        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(data)
    }

    suspend fun importPreferences(json: String) = withContext(Dispatchers.IO) {
        PreferencesSnapshot.import(json, appPreferences.getSharedPreferences())
    }

    fun reset() {
        importState = ImportState.Idle
    }

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(this)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
