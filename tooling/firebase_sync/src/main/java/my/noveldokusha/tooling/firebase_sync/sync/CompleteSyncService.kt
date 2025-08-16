package my.noveldokusha.tooling.firebase_sync.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import my.noveldokusha.tooling.firebase_sync.auth.FirebaseAuthService
import my.noveldokusha.tooling.firebase_sync.data.*
import my.noveldokusha.tooling.firebase_sync.repository.FirebaseSyncRepository
import my.noveldokusha.tooling.firebase_sync.repository.LocalLibraryRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompleteSyncService @Inject constructor(
    private val syncRepository: FirebaseSyncRepository,
    private val authService: FirebaseAuthService,
    private val localRepository: LocalLibraryRepository
) {
    private val _syncState = MutableStateFlow<CompleteSyncState>(CompleteSyncState.Idle)
    val syncState: Flow<CompleteSyncState> = _syncState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: Flow<SyncProgress> = _syncProgress.asStateFlow()

    suspend fun performCompleteSync(includeImages: Boolean = true): Result<Unit> {
        if (!authService.isSignedIn()) {
            return Result.failure(Exception("User not authenticated"))
        }

        _syncState.value = CompleteSyncState.Syncing
        _syncProgress.value = SyncProgress(currentOperation = "Starting complete sync...")

        return try {
            val userId = authService.getCurrentUserId() ?: ""

            // Phase 1: Sync Library (fast)
            _syncProgress.value = _syncProgress.value.copy(currentOperation = "Syncing library...")
            syncLibrary()

            // Phase 2: Sync Chapters (medium)
            _syncProgress.value = _syncProgress.value.copy(currentOperation = "Syncing chapters...")
            syncChapters()

            // Phase 3: Sync Chapter Bodies (large)
            _syncProgress.value = _syncProgress.value.copy(currentOperation = "Syncing chapter content...")
            syncChapterBodies()

            // Phase 4: Sync Images (potentially very large)
            if (includeImages) {
                _syncProgress.value = _syncProgress.value.copy(currentOperation = "Syncing images...")
                syncImages()
            }

            _syncState.value = CompleteSyncState.Success
            _syncProgress.value = _syncProgress.value.copy(
                currentOperation = "Sync completed successfully",
                processedItems = _syncProgress.value.totalItems
            )

            Timber.d("Complete sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = CompleteSyncState.Error(e.message ?: "Sync failed")
            _syncProgress.value = _syncProgress.value.copy(currentOperation = "Sync failed: ${e.message}")
            Timber.e(e, "Complete sync failed")
            Result.failure(e)
        }
    }

    suspend fun performIncrementalSync(includeImages: Boolean = false): Result<Unit> {
        if (!authService.isSignedIn()) {
            return Result.failure(Exception("User not authenticated"))
        }

        _syncState.value = CompleteSyncState.Syncing
        _syncProgress.value = SyncProgress(currentOperation = "Starting incremental sync...")

        return try {
            val lastSyncTimestamp = syncRepository.getLastSyncTimestamp().getOrElse { 0L }

            // Sync only changed data since last sync
            syncLibraryIncremental(lastSyncTimestamp)
            syncChaptersIncremental(lastSyncTimestamp)
            syncChapterBodiesIncremental(lastSyncTimestamp)

            if (includeImages) {
                syncImagesIncremental(lastSyncTimestamp)
            }

            _syncState.value = CompleteSyncState.Success
            Timber.d("Incremental sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = CompleteSyncState.Error(e.message ?: "Incremental sync failed")
            Timber.e(e, "Incremental sync failed")
            Result.failure(e)
        }
    }

    private suspend fun syncLibrary() {
        val localLibrary = localRepository.getUserLibrary(authService.getCurrentUserId() ?: "")
        val cloudLibrary = syncRepository.downloadLibrary().getOrElse {
            UserLibrary(userId = authService.getCurrentUserId() ?: "")
        }

        val mergedLibrary = mergeLibraries(localLibrary, cloudLibrary)
        localRepository.updateBooks(mergedLibrary.books.values.toList())
        syncRepository.uploadLibrary(mergedLibrary)
    }

    private suspend fun syncChapters() {
        val localChapters = localRepository.getUserChapters(authService.getCurrentUserId() ?: "")
        val cloudChapters = syncRepository.downloadChapters().getOrElse {
            UserChapters(userId = authService.getCurrentUserId() ?: "")
        }

        val mergedChapters = mergeChapters(localChapters, cloudChapters)
        localRepository.updateChapters(mergedChapters.chapters.values.toList())
        syncRepository.uploadChapters(mergedChapters)
    }

    private suspend fun syncChapterBodies() {
        val localChapterBodies = localRepository.getUserChapterBodies(authService.getCurrentUserId() ?: "")
        val cloudChapterBodies = syncRepository.downloadChapterBodies().getOrElse {
            UserChapterBodies(userId = authService.getCurrentUserId() ?: "")
        }

        // Update progress for large operation
        val totalBodies = maxOf(localChapterBodies.chapterBodies.size, cloudChapterBodies.chapterBodies.size)
        _syncProgress.value = _syncProgress.value.copy(totalItems = totalBodies)

        val mergedChapterBodies = mergeChapterBodies(localChapterBodies, cloudChapterBodies)
        localRepository.updateChapterBodies(mergedChapterBodies.chapterBodies.values.toList())

        // Upload in batches to handle large data
        uploadChapterBodiesInBatches(mergedChapterBodies)
    }

    private suspend fun syncImages() {
        val localImages = localRepository.getUserImages(authService.getCurrentUserId() ?: "")
        val cloudImages = syncRepository.downloadImages().getOrElse {
            UserImages(userId = authService.getCurrentUserId() ?: "")
        }

        // Prioritize high-priority images (book covers) first
        val highPriorityImages = localRepository.getImagesByPriority(ImagePriority.HIGH)
        val mediumPriorityImages = localRepository.getImagesByPriority(ImagePriority.MEDIUM)
        val lowPriorityImages = localRepository.getImagesByPriority(ImagePriority.LOW)

        val totalImages = highPriorityImages.size + mediumPriorityImages.size + lowPriorityImages.size
        _syncProgress.value = _syncProgress.value.copy(totalItems = totalImages)

        // Sync images by priority
        syncImagesByPriority(highPriorityImages, "book covers")
        syncImagesByPriority(mediumPriorityImages, "chapter images")
        syncImagesByPriority(lowPriorityImages, "other images")

        val mergedImages = mergeImages(localImages, cloudImages)
        localRepository.updateImages(mergedImages.images.values.toList())
        syncRepository.uploadImages(mergedImages)
    }

    private suspend fun syncImagesByPriority(images: List<ImageSync>, description: String) = coroutineScope {
        _syncProgress.value = _syncProgress.value.copy(currentOperation = "Syncing $description...")

        // Process images in parallel but with limited concurrency
        val semaphore = kotlinx.coroutines.sync.Semaphore(3) // Max 3 concurrent image uploads

        images.chunked(10).forEach { batch ->
            batch.map { imageSync ->
                async {
                    semaphore.withPermit {
                        try {
                            val localFile = localRepository.getImageFile(imageSync.path)
                            if (localFile != null) {
                                val imageData = localFile.readBytes()
                                syncRepository.uploadImageFile(imageSync.path, imageData)

                                _syncProgress.value = _syncProgress.value.copy(
                                    processedItems = _syncProgress.value.processedItems + 1,
                                    bytesTransferred = _syncProgress.value.bytesTransferred + imageData.size
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to sync image: ${imageSync.path}")
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun syncLibraryIncremental(lastSyncTimestamp: Long) {
        val changedBooks = localRepository.getBooksSince(lastSyncTimestamp)
        if (changedBooks.isNotEmpty()) {
            val currentLibrary = localRepository.getUserLibrary(authService.getCurrentUserId() ?: "")
            syncRepository.uploadLibrary(currentLibrary)
        }
    }

    private suspend fun syncChaptersIncremental(lastSyncTimestamp: Long) {
        val changedChapters = localRepository.getChaptersSince(lastSyncTimestamp)
        if (changedChapters.isNotEmpty()) {
            val currentChapters = localRepository.getUserChapters(authService.getCurrentUserId() ?: "")
            syncRepository.uploadChapters(currentChapters)
        }
    }

    private suspend fun syncChapterBodiesIncremental(lastSyncTimestamp: Long) {
        val changedChapterBodies = localRepository.getChapterBodiesSince(lastSyncTimestamp)
        if (changedChapterBodies.isNotEmpty()) {
            val currentChapterBodies = localRepository.getUserChapterBodies(authService.getCurrentUserId() ?: "")
            uploadChapterBodiesInBatches(currentChapterBodies)
        }
    }

    private suspend fun syncImagesIncremental(lastSyncTimestamp: Long) {
        val changedImages = localRepository.getImagesSince(lastSyncTimestamp)
        if (changedImages.isNotEmpty()) {
            syncImagesByPriority(changedImages, "changed images")
        }
    }

    private suspend fun uploadChapterBodiesInBatches(chapterBodies: UserChapterBodies) {
        val batchSize = 50 // Smaller batches for large content
        val bodies = chapterBodies.chapterBodies.values.toList()

        bodies.chunked(batchSize).forEachIndexed { index, batch ->
            val batchData = UserChapterBodies(
                userId = chapterBodies.userId,
                chapterBodies = batch.associateBy { it.url },
                lastSyncTimestamp = chapterBodies.lastSyncTimestamp
            )

            syncRepository.uploadChapterBodies(batchData)

            _syncProgress.value = _syncProgress.value.copy(
                processedItems = _syncProgress.value.processedItems + batch.size,
                currentOperation = "Uploading chapter content batch ${index + 1}..."
            )
        }
    }

    private fun mergeLibraries(local: UserLibrary, cloud: UserLibrary): UserLibrary {
        val mergedBooks = mutableMapOf<String, LibraryBook>()
        mergedBooks.putAll(local.books)

        cloud.books.forEach { (url, cloudBook) ->
            val localBook = mergedBooks[url]
            if (localBook == null || cloudBook.lastUpdatedEpochTimeMilli > localBook.lastUpdatedEpochTimeMilli) {
                mergedBooks[url] = cloudBook
            }
        }

        return UserLibrary(
            userId = local.userId,
            books = mergedBooks,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    private fun mergeChapters(local: UserChapters, cloud: UserChapters): UserChapters {
        val mergedChapters = mutableMapOf<String, ChapterSync>()
        mergedChapters.putAll(local.chapters)

        cloud.chapters.forEach { (url, cloudChapter) ->
            val localChapter = mergedChapters[url]
            if (localChapter == null || cloudChapter.lastUpdatedEpochTimeMilli > localChapter.lastUpdatedEpochTimeMilli) {
                mergedChapters[url] = cloudChapter
            }
        }

        return UserChapters(
            userId = local.userId,
            chapters = mergedChapters,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    private fun mergeChapterBodies(local: UserChapterBodies, cloud: UserChapterBodies): UserChapterBodies {
        val mergedBodies = mutableMapOf<String, ChapterBodySync>()
        mergedBodies.putAll(local.chapterBodies)

        cloud.chapterBodies.forEach { (url, cloudBody) ->
            val localBody = mergedBodies[url]
            if (localBody == null || cloudBody.lastUpdatedEpochTimeMilli > localBody.lastUpdatedEpochTimeMilli) {
                mergedBodies[url] = cloudBody
            }
        }

        return UserChapterBodies(
            userId = local.userId,
            chapterBodies = mergedBodies,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    private fun mergeImages(local: UserImages, cloud: UserImages): UserImages {
        val mergedImages = mutableMapOf<String, ImageSync>()
        mergedImages.putAll(local.images)

        cloud.images.forEach { (path, cloudImage) ->
            val localImage = mergedImages[path]
            if (localImage == null || cloudImage.lastUpdatedEpochTimeMilli > localImage.lastUpdatedEpochTimeMilli) {
                mergedImages[path] = cloudImage
            }
        }

        return UserImages(
            userId = local.userId,
            images = mergedImages,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }
}

sealed class CompleteSyncState {
    object Idle : CompleteSyncState()
    object Syncing : CompleteSyncState()
    object Success : CompleteSyncState()
    data class Error(val message: String) : CompleteSyncState()
}
