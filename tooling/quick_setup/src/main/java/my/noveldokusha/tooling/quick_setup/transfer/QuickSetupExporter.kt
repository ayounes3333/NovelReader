package my.noveldokusha.tooling.quick_setup.transfer

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.tooling.quick_setup.PreferencesSnapshot
import my.noveldokusha.tooling.quick_setup.transfer.dto.BookDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterBodyDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterDto
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSetupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val appFileResolver: AppFileResolver,
    private val appPreferences: AppPreferences,
) {

    private val booksFolder: File get() = appFileResolver.folderBooks

    suspend fun createManifest(): TransferManifest = withContext(Dispatchers.IO) {
        val libraryDao = appDatabase.libraryDao()
        val chapterDao = appDatabase.chapterDao()
        val chapterBodyDao = appDatabase.chapterBodyDao()

        val booksCount = libraryDao.getAllInLibraryCount()
        val chaptersCount = chapterDao.getCountAll()
        val chapterBodiesCount = chapterBodyDao.getCountAll()

        val imageFiles = booksFolder.walkTopDown()
            .filter { it.isFile }
            .toList()
        val totalImageBytes = imageFiles.sumOf { it.length() }

        TransferManifest(
            schemaVersion = 9,
            booksCount = booksCount,
            chaptersCount = chaptersCount,
            chapterBodiesCount = chapterBodiesCount,
            imagesCount = imageFiles.size,
            totalImageBytes = totalImageBytes,
            hasPreferences = true,
            deviceName = Build.MODEL ?: "Unknown Device",
        )
    }

    suspend fun getBooksPage(page: Int, pageSize: Int = 100): List<BookDto> =
        withContext(Dispatchers.IO) {
            // Ordered + filtered to match the manifest's in-library count, so
            // page boundaries are stable across requests (resume correctness).
            val books = appDatabase.libraryDao().getAllInLibraryBatchOrdered(page * pageSize, pageSize)
            books.map { BookDto.fromBook(it) }
        }

    suspend fun getChaptersPage(page: Int, pageSize: Int = 200): List<ChapterDto> =
        withContext(Dispatchers.IO) {
            val chapters = appDatabase.chapterDao().getBatchOrdered(page * pageSize, pageSize)
            chapters.map { ChapterDto.fromChapter(it) }
        }

    suspend fun getChapterBodiesPage(page: Int, pageSize: Int = 50): List<ChapterBodyDto> =
        withContext(Dispatchers.IO) {
            val bodies = appDatabase.chapterBodyDao().getBatchOrdered(page * pageSize, pageSize)
            bodies.map { ChapterBodyDto.fromChapterBody(it) }
        }

    suspend fun getChapterBody(url: String): String? = withContext(Dispatchers.IO) {
        appDatabase.chapterBodyDao().get(url)?.body
    }

    suspend fun getImageManifest(): List<ImageManifestEntry> = withContext(Dispatchers.IO) {
        booksFolder.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val relativePath = booksFolder.toPath().relativize(file.toPath()).toString()
                ImageManifestEntry(
                    relativePath = relativePath,
                    sha256 = file.sha256(),
                    sizeBytes = file.length(),
                )
            }
            .toList()
    }

    suspend fun getImageBlob(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(booksFolder, relativePath)
        // Never serve files outside the books folder
        if (!file.canonicalPath.startsWith(booksFolder.canonicalPath + File.separator)) {
            Timber.w("Rejected image blob request outside books folder: %s", relativePath)
            return@withContext null
        }
        if (file.exists() && file.isFile) file.readBytes() else null
    }

    suspend fun getPreferencesJson(): String = withContext(Dispatchers.IO) {
        PreferencesSnapshot.export(appPreferences.getSharedPreferences())
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
