package my.noveldokusha.tooling.firebase_sync.repository

import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.tooling.firebase_sync.data.*
import my.noveldokusha.tooling.firebase_sync.image.ImageDiscoveryService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val imageDiscoveryService: ImageDiscoveryService
) : LocalLibraryRepository {

    override suspend fun getAllBooks(): List<LibraryBook> {
        return try {
            appDatabase.libraryDao().getAll().map { book ->
                // Get chapters for this book
                val chapters = try {
                    appDatabase.chapterDao().chapters(book.url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
                
                val readChapters = chapters.count { chapter -> 
                    try {
                        chapter.read
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                LibraryBook(
                    url = book.url,
                    title = book.title,
                    completed = book.completed,
                    lastReadChapter = book.lastReadChapter,  // Fixed: correct property name
                    description = book.description,
                    coverImageUrl = book.coverImageUrl,
                    inLibrary = book.inLibrary,
                    lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
                    lastUpdatedEpochTimeMilli = book.lastReadEpochTimeMilli, // Using lastReadEpochTimeMilli as fallback
                    chaptersCount = chapters.size,
                    chaptersReadCount = readChapters
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list if database access fails
            emptyList()
        }
    }

    override suspend fun getBooksSince(timestamp: Long): List<LibraryBook> {
        return try {
            // Since getAllSince() doesn't exist, filter by timestamp after getting all books
            appDatabase.libraryDao().getAll()
                .filter { book -> book.lastReadEpochTimeMilli >= timestamp }
                .map { book ->
                    val chapters = try {
                        appDatabase.chapterDao().chapters(book.url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                    
                    val readChapters = chapters.count { chapter -> 
                        try {
                            chapter.read
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }

                    LibraryBook(
                        url = book.url,
                        title = book.title,
                        completed = book.completed,
                        lastReadChapter = book.lastReadChapter,  // Fixed: correct property name
                        description = book.description,
                        coverImageUrl = book.coverImageUrl,
                        inLibrary = book.inLibrary,
                        lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
                        lastUpdatedEpochTimeMilli = book.lastReadEpochTimeMilli, // Using lastReadEpochTimeMilli as fallback
                        chaptersCount = chapters.size,
                        chaptersReadCount = readChapters
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list if database access fails
            emptyList()
        }
    }

    override suspend fun updateBooks(books: List<LibraryBook>) {
        try {
            appDatabase.libraryDao().insertReplace(books.map { it.asEntityBook })
        } catch (e: Exception) {
            e.printStackTrace()
            // Log error but continue with other books
        }
    }

    override suspend fun getUserLibrary(userId: String): UserLibrary {
        val books = getAllBooks()
        return UserLibrary(
            userId = userId,
            books = books.associateBy { it.url },
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    // Chapter operations
    override suspend fun getAllChapters(): List<ChapterSync> {
        return try {
            appDatabase.chapterDao().getAll().map { chapter ->
                ChapterSync(
                    url = chapter.url,
                    title = chapter.title,
                    bookUrl = chapter.bookUrl,
                    position = chapter.position,
                    read = chapter.read,
                    lastReadPosition = chapter.lastReadPosition,
                    lastReadOffset = chapter.lastReadOffset,
                    lastUpdatedEpochTimeMilli = chapter.lastUpdatedEpochTimeMilli
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getChaptersSince(timestamp: Long): List<ChapterSync> {
        return try {
            appDatabase.chapterDao().getAll()
                .filter { it.lastUpdatedEpochTimeMilli >= timestamp }
                .map { chapter ->
                    ChapterSync(
                        url = chapter.url,
                        title = chapter.title,
                        bookUrl = chapter.bookUrl,
                        position = chapter.position,
                        read = chapter.read,
                        lastReadPosition = chapter.lastReadPosition,
                        lastReadOffset = chapter.lastReadOffset,
                        lastUpdatedEpochTimeMilli = chapter.lastUpdatedEpochTimeMilli
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getChaptersForBook(bookUrl: String): List<ChapterSync> {
        return try {
            appDatabase.chapterDao().chapters(bookUrl).map { chapter ->
                ChapterSync(
                    url = chapter.url,
                    title = chapter.title,
                    bookUrl = chapter.bookUrl,
                    position = chapter.position,
                    read = chapter.read,
                    lastReadPosition = chapter.lastReadPosition,
                    lastReadOffset = chapter.lastReadOffset,
                    lastUpdatedEpochTimeMilli = chapter.lastUpdatedEpochTimeMilli
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun updateChapters(chapters: List<ChapterSync>) {
        try {
            chapters.forEach { chapterSync ->
                val chapter = my.noveldokusha.feature.local_database.tables.Chapter(
                    title = chapterSync.title,
                    url = chapterSync.url,
                    bookUrl = chapterSync.bookUrl,
                    position = chapterSync.position,
                    read = chapterSync.read,
                    lastReadPosition = chapterSync.lastReadPosition,
                    lastReadOffset = chapterSync.lastReadOffset,
                    createdEpochTimeMilli = System.currentTimeMillis(),
                    lastUpdatedEpochTimeMilli = chapterSync.lastUpdatedEpochTimeMilli
                )
                appDatabase.chapterDao().insert(listOf(chapter))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getUserChapters(userId: String): UserChapters {
        val chapters = getAllChapters()
        return UserChapters(
            userId = userId,
            chapters = chapters.associateBy { it.url },
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    // ChapterBody operations
    override suspend fun getAllChapterBodies(): List<ChapterBodySync> {
        return try {
            appDatabase.chapterBodyDao().getAll().map { chapterBody ->
                ChapterBodySync(
                    url = chapterBody.url,
                    body = chapterBody.body,
                    lastUpdatedEpochTimeMilli = chapterBody.lastUpdatedEpochTimeMilli
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getChapterBodiesSince(timestamp: Long): List<ChapterBodySync> {
        return try {
            appDatabase.chapterBodyDao().getAll()
                .filter { it.lastUpdatedEpochTimeMilli >= timestamp }
                .map { chapterBody ->
                    ChapterBodySync(
                        url = chapterBody.url,
                        body = chapterBody.body,
                        lastUpdatedEpochTimeMilli = chapterBody.lastUpdatedEpochTimeMilli
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getChapterBodiesForBook(bookUrl: String): List<ChapterBodySync> {
        return try {
            val bookChapters = appDatabase.chapterDao().chapters(bookUrl)
            val chapterUrls = bookChapters.map { it.url }

            appDatabase.chapterBodyDao().getAll()
                .filter { it.url in chapterUrls }
                .map { chapterBody ->
                    ChapterBodySync(
                        url = chapterBody.url,
                        body = chapterBody.body,
                        lastUpdatedEpochTimeMilli = chapterBody.lastUpdatedEpochTimeMilli
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun updateChapterBodies(chapterBodies: List<ChapterBodySync>) {
        try {
            chapterBodies.forEach { chapterBodySync ->
                val chapterBody = my.noveldokusha.feature.local_database.tables.ChapterBody(
                    url = chapterBodySync.url,
                    body = chapterBodySync.body,
                    createdEpochTimeMilli = System.currentTimeMillis(),
                    lastUpdatedEpochTimeMilli = chapterBodySync.lastUpdatedEpochTimeMilli
                )
                appDatabase.chapterBodyDao().insertReplace(chapterBody)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getUserChapterBodies(userId: String): UserChapterBodies {
        val chapterBodies = getAllChapterBodies()
        return UserChapterBodies(
            userId = userId,
            chapterBodies = chapterBodies.associateBy { it.url },
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    // Image operations
    override suspend fun getAllImages(): List<ImageSync> {
        return imageDiscoveryService.discoverAllImages()
    }

    override suspend fun getImagesSince(timestamp: Long): List<ImageSync> {
        val allImages = imageDiscoveryService.discoverAllImages()
        return allImages.filter { it.lastUpdatedEpochTimeMilli >= timestamp }
    }

    override suspend fun getImagesForBook(bookUrl: String): List<ImageSync> {
        return imageDiscoveryService.discoverImagesForBook(bookUrl)
    }

    override suspend fun updateImages(images: List<ImageSync>) {
        // For now, this doesn't need to do anything since images are discovered dynamically
        // In the future, we might want to store image metadata in a database table
        // But for now, the file system is the source of truth
    }

    override suspend fun getUserImages(userId: String): UserImages {
        val images = getAllImages()
        return UserImages(
            userId = userId,
            images = images.associateBy { it.path },
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    override suspend fun getImageFile(imagePath: String): File? {
        return try {
            val file = File(imagePath)
            if (file.exists() && file.isFile) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveImageFile(imagePath: String, data: ByteArray): Boolean {
        return try {
            val file = File(imagePath)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun calculateImageChecksum(file: File): String {
        return try {
            val bytes = file.readBytes()
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override suspend fun getImagesByPriority(priority: ImagePriority): List<ImageSync> {
        val allImages = imageDiscoveryService.discoverAllImages()
        return allImages.filter { it.priority == priority }
    }
}
