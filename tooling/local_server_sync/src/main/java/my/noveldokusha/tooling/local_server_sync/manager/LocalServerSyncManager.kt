package my.noveldokusha.tooling.local_server_sync.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import my.noveldokusha.tooling.local_server_sync.repository.LocalLibraryRepository
import my.noveldokusha.tooling.local_server_sync.data.*
import my.noveldokusha.tooling.local_server_sync.image.LocalServerImageService
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalServerSyncManager @Inject constructor(
    private val authService: LocalServerAuthService,
    private val syncRepository: LocalServerSyncRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val imageService: LocalServerImageService
) {

    suspend fun syncLibrary(): Result<Unit> {
        return try {
            if (!authService.isAuthenticated()) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Get local library data
            val localBooks = localLibraryRepository.getAllBooks()
            val localLibraryBooks = localBooks.map { book ->
                LibraryBook(
                    url = book.url,
                    title = book.title,
                    completed = book.completed,
                    lastReadChapter = book.lastReadChapter.takeIf { it?.isNotBlank() == true },
                    description = book.description,
                    coverImageUrl = book.coverImageUrl,
                    inLibrary = book.inLibrary,
                    lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
                    lastUpdatedEpochTimeMilli = System.currentTimeMillis(),
                    chaptersCount = localLibraryRepository.getChaptersCount(book.url),
                    chaptersReadCount = localLibraryRepository.getReadChaptersCount(book.url)
                )
            }.associateBy { it.url }

            val userLibrary = UserLibrary(
                userId = authService.getCurrentUserId() ?: "",
                books = localLibraryBooks,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            // Upload to server
            val uploadResult = syncRepository.uploadLibrary(userLibrary)
            uploadResult.fold(
                onSuccess = {
                    Timber.d("Library sync completed successfully")

                    // Also sync images after successful library sync
                    val imageResult = syncImages()
                    imageResult.fold(
                        onSuccess = {
                            Timber.d("Images synced successfully")
                        },
                        onFailure = { exception ->
                            Timber.w(exception, "Image sync failed, but library sync succeeded")
                        }
                    )

                    Result.success(Unit)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to sync library")
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error during library sync")
            Result.failure(e)
        }
    }

    suspend fun downloadAndMergeLibrary(): Result<Unit> {
        return try {
            if (!authService.isAuthenticated()) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Download from server
            val downloadResult = syncRepository.downloadLibrary()
            downloadResult.fold(
                onSuccess = { serverLibrary ->
                    // Merge with local data
                    serverLibrary.books.values.forEach { serverBook ->
                        val localBook = serverBook.asEntityBook
                        localLibraryRepository.updateOrInsertBook(localBook)
                    }

                    Timber.d("Library download and merge completed successfully")
                    Result.success(Unit)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to download library")
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error during library download")
            Result.failure(e)
        }
    }

    suspend fun performCompleteSync(): Result<Unit> {
        return try {
            if (!authService.isAuthenticated()) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Get all local data
            val localBooks = localLibraryRepository.getAllBooks()
            val localLibraryBooks = localBooks.map { book ->
                LibraryBook(
                    url = book.url,
                    title = book.title,
                    completed = book.completed,
                    lastReadChapter = book.lastReadChapter.takeIf { it?.isNotBlank() == true },
                    description = book.description,
                    coverImageUrl = book.coverImageUrl,
                    inLibrary = book.inLibrary,
                    lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
                    lastUpdatedEpochTimeMilli = System.currentTimeMillis(),
                    chaptersCount = localLibraryRepository.getChaptersCount(book.url),
                    chaptersReadCount = localLibraryRepository.getReadChaptersCount(book.url)
                )
            }.associateBy { it.url }

            val userLibrary = UserLibrary(
                userId = authService.getCurrentUserId() ?: "",
                books = localLibraryBooks,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            // Get chapters for all books
            val allChapters = mutableMapOf<String, List<BookChapter>>()
            localBooks.forEach { book ->
                val chapters = localLibraryRepository.getChapters(book.url).map { chapter ->
                    BookChapter(
                        url = chapter.url,
                        bookUrl = book.url,
                        title = chapter.title,
                        position = chapter.position.toFloat(),
                        read = chapter.read,
                        lastReadOffset = chapter.lastReadOffset,
                        lastReadEpochTimeMilli = 0L // Chapter entity doesn't have this field
                    )
                }
                if (chapters.isNotEmpty()) {
                    allChapters[book.url] = chapters
                }
            }

            // Get chapter bodies (this might be limited due to size)
            val allChapterBodies = mutableMapOf<String, ChapterBody>()
            // For now, we'll skip chapter bodies due to potential size constraints
            // In a real implementation, you might want to sync these selectively

            // Perform complete sync
            val syncResult = syncRepository.uploadCompleteSync(
                userLibrary = userLibrary,
                chapters = allChapters,
                chapterBodies = allChapterBodies
            )

            syncResult.fold(
                onSuccess = { response ->
                    // Optionally merge any server data back to local
                    response.library?.let { serverLibrary ->
                        serverLibrary.books.values.forEach { serverBook ->
                            val localBook = serverBook.asEntityBook
                            localLibraryRepository.updateOrInsertBook(localBook)
                        }
                    }

                    // Also sync images after successful complete sync
                    val imageResult = syncImages()
                    imageResult.fold(
                        onSuccess = {
                            Timber.d("Images synced successfully during complete sync")
                        },
                        onFailure = { exception ->
                            Timber.w(exception, "Image sync failed during complete sync, but main sync succeeded")
                        }
                    )

                    Timber.d("Complete sync completed successfully")
                    Result.success(Unit)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to perform complete sync")
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error during complete sync")
            Result.failure(e)
        }
    }

    suspend fun isUserAuthenticated(): Boolean {
        return authService.isAuthenticated()
    }

    suspend fun getCurrentUserId(): String? {
        return authService.getCurrentUserId()
    }

    suspend fun syncImages(): Result<Unit> {
        return try {
            if (!authService.isAuthenticated()) {
                return Result.failure(Exception("User not authenticated"))
            }

            // Check available memory before starting
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
            val totalMemory = runtime.totalMemory() / 1024 / 1024 // MB
            val freeMemory = runtime.freeMemory() / 1024 / 1024 // MB
            val usedMemory = totalMemory - freeMemory
            val availableMemory = maxMemory - usedMemory

            Timber.d("Memory status before image sync - Available: ${availableMemory}MB, Used: ${usedMemory}MB, Max: ${maxMemory}MB")

            if (availableMemory < 30) { // Require at least 30MB free
                Timber.w("Insufficient memory for image sync (${availableMemory}MB available), skipping")
                return Result.failure(Exception("Insufficient memory for image sync"))
            }

            // Use streaming approach instead of loading all images at once
            Timber.d("Starting streaming image sync...")
            val uploadedCount = streamingImageSync()

            logMemoryStatus("End of streaming image sync")
            Timber.d("Streaming image sync completed: $uploadedCount images uploaded successfully")
            Result.success(Unit)

        } catch (e: OutOfMemoryError) {
            Timber.e("OutOfMemoryError during image sync, attempting emergency cleanup")
            System.gc()
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Error during image sync")
            Result.failure(e)
        }
    }

    private suspend fun streamingImageSync(): Int = withContext(Dispatchers.IO) {
        var totalUploadedCount = 0
        var currentBatch = mutableListOf<ImageBackupItem>()
        val batchSize = 3 // Very small batch size for memory safety

        try {
            val booksDirectory = imageService.folderBooks
            if (!booksDirectory.exists() || !booksDirectory.isDirectory) {
                Timber.w("Books directory does not exist")
                return@withContext 0
            }

            val bookFolders = booksDirectory.listFiles() ?: emptyArray()
            Timber.d("Processing ${bookFolders.size} book folders in streaming mode")

            bookFolders.forEachIndexed { bookIndex, bookFolder ->
                if (bookFolder.isDirectory) {
                    logMemoryStatus("Processing book ${bookIndex + 1}/${bookFolders.size}: ${bookFolder.name}")

                    val bookUrl = imageService.decodeBookFolderName(bookFolder.name)

                    // Process this book's images in streaming mode
                    val bookUploadedCount = processBookImagesStreaming(
                        bookFolder,
                        bookUrl,
                        "",
                        currentBatch,
                        batchSize
                    )

                    totalUploadedCount += bookUploadedCount

                    // Upload any remaining images in the current batch
                    if (currentBatch.isNotEmpty()) {
                        val batchUploadedCount = uploadBatch(currentBatch, "final-book-${bookIndex}")
                        totalUploadedCount += batchUploadedCount
                        currentBatch.clear()

                        // Aggressive cleanup after each book
                        System.gc()
                        logMemoryStatus("After processing book ${bookIndex + 1}")
                    }
                }
            }

            // Upload any final remaining images
            if (currentBatch.isNotEmpty()) {
                val finalUploadedCount = uploadBatch(currentBatch, "final")
                totalUploadedCount += finalUploadedCount
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in streaming image sync")
        }

        totalUploadedCount
    }

    private suspend fun processBookImagesStreaming(
        directory: File,
        bookUrl: String,
        relativePath: String,
        currentBatch: MutableList<ImageBackupItem>,
        batchSize: Int
    ): Int = withContext(Dispatchers.IO) {
        var uploadedCount = 0

        try {
            directory.listFiles()?.forEach { file ->
                // Check memory before processing each file
                val freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
                if (freeMemory < 10) {
                    Timber.w("Memory critically low (${freeMemory}MB), stopping image processing")
                    return@withContext uploadedCount
                }

                if (file.isDirectory) {
                    // Recursively process subdirectories
                    val subPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                    val subUploadedCount = processBookImagesStreaming(file, bookUrl, subPath, currentBatch, batchSize)
                    uploadedCount += subUploadedCount
                } else if (file.isFile && imageService.isImageFile(file)) {
                    // Process single image
                    val filePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

                    // Create image backup item immediately and add to batch
                    val imageItem = imageService.createImageBackupItemSafe(file, bookUrl, filePath)
                    if (imageItem != null) {
                        currentBatch.add(imageItem)

                        // Upload batch when it reaches the size limit
                        if (currentBatch.size >= batchSize) {
                            val batchUploadedCount = uploadBatch(currentBatch, "streaming")
                            uploadedCount += batchUploadedCount
                            currentBatch.clear()

                            // Force cleanup after each batch
                            System.gc()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing directory: ${directory.absolutePath}")
        }

        uploadedCount
    }

    private suspend fun uploadBatch(batch: List<ImageBackupItem>, batchType: String): Int {
        return try {
            logMemoryStatus("Before uploading $batchType batch of ${batch.size} images")

            val uploadResult = syncRepository.uploadImages(batch)
            uploadResult.fold(
                onSuccess = { response ->
                    logMemoryStatus("After uploading $batchType batch")
                    Timber.d("$batchType batch uploaded successfully: ${response.uploadedCount} images")
                    response.uploadedCount
                },
                onFailure = { exception ->
                    Timber.w(exception, "Failed to upload $batchType batch")
                    0
                }
            )
        } catch (e: Exception) {
            Timber.w(e, "Error uploading $batchType batch")
            0
        }
    }

    private fun logMemoryStatus(context: String) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
        val totalMemory = runtime.totalMemory() / 1024 / 1024 // MB
        val freeMemory = runtime.freeMemory() / 1024 / 1024 // MB
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxMemory - usedMemory

        Timber.d("[$context] SYNC Memory - Free: ${freeMemory}MB, Used: ${usedMemory}MB, Available: ${availableMemory}MB, Max: ${maxMemory}MB")
    }
}
