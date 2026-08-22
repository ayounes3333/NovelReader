package my.noveldokusha.tooling.local_server_sync.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.data.BookChapter
import my.noveldokusha.tooling.local_server_sync.data.BookChapterEntry
import my.noveldokusha.tooling.local_server_sync.data.ChapterBodyCheckEntry
import my.noveldokusha.tooling.local_server_sync.data.ImageManifestEntry
import my.noveldokusha.tooling.local_server_sync.data.LibraryBook
import my.noveldokusha.tooling.local_server_sync.data.LibraryBookEntry
import my.noveldokusha.tooling.local_server_sync.image.LocalServerImageService
import my.noveldokusha.tooling.local_server_sync.repository.LocalLibraryRepository
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage.SyncCursor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncProgress {
    object Idle : SyncProgress()
    data class Running(val stage: String, val current: Int = 0, val total: Int = 0) : SyncProgress()
    data class Error(val message: String) : SyncProgress()
    data class Done(val timestamp: Long = System.currentTimeMillis()) : SyncProgress()
}

private const val PARALLEL_BODY_DOWNLOADS = 8
private const val PARALLEL_BODY_UPLOADS = 8
private const val PARALLEL_IMAGE_DOWNLOADS = 8
private const val PARALLEL_IMAGE_UPLOADS = 4

@Singleton
class LocalServerSyncManager @Inject constructor(
    private val authService: LocalServerAuthService,
    private val syncRepository: LocalServerSyncRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val imageService: LocalServerImageService,
    private val tokenStorage: AuthTokenStorage
) {
    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    fun isUserAuthenticated(): Boolean = authService.isAuthenticated()
    fun getCurrentUserId(): String? = authService.getCurrentUserId()

    suspend fun performQuickProgressSync(): Result<Unit> = guard("quick-progress") {
        pullAndApplyLibrary()
        pushLocalLibrary()
        pullAndApplyChapters()
        pushLocalChapters()
    }

    suspend fun performBulkSync(): Result<Unit> = guard("bulk") {
        pullAndApplyLibrary()
        pushLocalLibrary()
        pullAndApplyChapters()
        pushLocalChapters()
        syncChapterBodies()
        syncImagesInternal()
    }

    suspend fun syncLibrary(): Result<Unit> = performQuickProgressSync()
    suspend fun performCompleteSync(): Result<Unit> = performBulkSync()
    suspend fun syncImages(): Result<Unit> = guard("images") { syncImagesInternal() }

    private suspend fun pullAndApplyLibrary() {
        emit(SyncProgress.Running(stage = "Library: pulling…"))
        var cursor = tokenStorage.getCursor(SyncCursor.LIBRARY)
        while (true) {
            val response = syncRepository.pullLibrary(cursor).getOrThrow()
            if (response.entries.isEmpty()) break
            for (entry in response.entries) {
                if (entry.deleted) {
                    Timber.d("library pull: tombstone ${entry.bookUrl}")
                } else entry.payload?.let { payload ->
                    localLibraryRepository.updateOrInsertBook(payload.asEntityBook)
                }
            }
            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.LIBRARY, cursor)
            if (!response.hasMore) break
        }
    }

    private suspend fun pushLocalLibrary() {
        emit(SyncProgress.Running(stage = "Library: pushing…"))
        val books = localLibraryRepository.getAllBooks()
        if (books.isEmpty()) return
        val entries = books.map { book ->
            LibraryBookEntry(
                bookUrl = book.url,
                payload = book.toLibraryBook(
                    chaptersCount = safe { localLibraryRepository.getChaptersCount(book.url) } ?: 0,
                    chaptersReadCount = safe { localLibraryRepository.getReadChaptersCount(book.url) } ?: 0
                ),
                updatedAt = book.lastReadEpochTimeMilli.coerceAtLeast(0L),
                deleted = false
            )
        }
        val applied = syncRepository.pushLibrary(entries).getOrThrow()
        val maxApplied = applied.maxOfOrNull { it.updatedAt } ?: 0L
        if (maxApplied > 0L) tokenStorage.setCursor(SyncCursor.LIBRARY, maxApplied)
    }

    private suspend fun pullAndApplyChapters() {
        emit(SyncProgress.Running(stage = "Chapters: pulling…"))
        var cursor = tokenStorage.getCursor(SyncCursor.CHAPTERS)
        while (true) {
            val response = syncRepository.pullChapters(cursor).getOrThrow()
            if (response.entries.isEmpty()) break
            for (entry in response.entries) {
                if (entry.deleted) continue
                val payload = entry.payload ?: continue
                val chapter = payload.toEntityChapter()
                runCatching { localLibraryRepository.updateChapter(chapter) }
                    .onFailure { Timber.w(it, "updateChapter failed for ${payload.url}") }
            }
            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.CHAPTERS, cursor)
            if (!response.hasMore) break
        }
    }

    private suspend fun pushLocalChapters() {
        emit(SyncProgress.Running(stage = "Chapters: pushing…"))
        val books = localLibraryRepository.getAllBooks()
        val entries = mutableListOf<BookChapterEntry>()
        for (book in books) {
            if (!book.inLibrary) continue
            val chapters = localLibraryRepository.getChapters(book.url)
            for (chapter in chapters) {
                if (!chapter.read && chapter.lastReadOffset == 0 && chapter.lastReadPosition == 0) continue
                entries += BookChapterEntry(
                    chapterUrl = chapter.url,
                    payload = chapter.toBookChapter(),
                    updatedAt = chapter.lastUpdatedEpochTimeMilli,
                    deleted = false
                )
            }
        }
        if (entries.isEmpty()) return
        entries.chunked(500).forEach { batch ->
            val applied = syncRepository.pushChapters(batch).getOrThrow()
            val maxApplied = applied.maxOfOrNull { it.updatedAt } ?: 0L
            if (maxApplied > 0L) tokenStorage.setCursor(SyncCursor.CHAPTERS, maxApplied)
        }
    }

    private suspend fun syncChapterBodies() {
        emit(SyncProgress.Running(stage = "Bodies: pulling manifest…"))
        var cursor = tokenStorage.getCursor(SyncCursor.CHAPTER_BODIES)
        while (true) {
            val response = syncRepository.pullChapterBodyManifest(cursor).getOrThrow()
            if (response.entries.isEmpty()) break

            val toDownload = response.entries
                .filter { !it.deleted }
                .filter { entry ->
                    val existing = localLibraryRepository.getChapterBody(entry.chapterUrl)
                    if (existing != null) {
                        val sha = imageService.sha256Hex(existing.toByteArray(Charsets.UTF_8))
                        !sha.equals(entry.sha256, ignoreCase = true)
                    } else true
                }

            if (toDownload.isNotEmpty()) {
                val semaphore = Semaphore(PARALLEL_BODY_DOWNLOADS)
                var completed = 0
                val total = toDownload.size
                coroutineScope {
                    toDownload.map { entry ->
                        async {
                            semaphore.withPermit {
                                val downloaded = syncRepository.getChapterBody(entry.chapterUrl).getOrNull()
                                if (downloaded != null) {
                                    localLibraryRepository.saveChapterBody(entry.chapterUrl, downloaded.first)
                                    completed++
                                    emit(
                                        SyncProgress.Running(
                                            stage = "Bodies: pulling",
                                            current = completed,
                                            total = total
                                        )
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
            }

            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.CHAPTER_BODIES, cursor)
            if (!response.hasMore) break
        }

        emit(SyncProgress.Running(stage = "Bodies: preparing push…"))
        val books = localLibraryRepository.getAllBooks().filter { it.inLibrary }

        val bodyEntries = mutableListOf<Pair<String, String>>()
        for (book in books) {
            val chapters = localLibraryRepository.getChapters(book.url)
            for (chapter in chapters) {
                val body = localLibraryRepository.getChapterBody(chapter.url) ?: continue
                if (body.isBlank()) continue
                bodyEntries.add(chapter.url to body)
            }
        }

        if (bodyEntries.isEmpty()) return

        val checkEntries = bodyEntries.map { (url, body) ->
            val sha = imageService.sha256Hex(body.toByteArray(Charsets.UTF_8))
            ChapterBodyCheckEntry(chapterUrl = url, sha256 = sha)
        }

        val missingUrls = mutableSetOf<String>()
        checkEntries.chunked(500).forEach { batch ->
            val checkResult = syncRepository.checkChapterBodies(batch).getOrNull()
            if (checkResult != null) {
                missingUrls += checkResult.missingUrls
            }
        }

        val toUpload = bodyEntries.filter { (url, _) -> url in missingUrls }
        if (toUpload.isEmpty()) {
            Timber.d("Bodies: all up-to-date (${bodyEntries.size - missingUrls.size}/${bodyEntries.size} skipped)")
            return
        }

        Timber.d("Bodies: uploading ${toUpload.size}/${bodyEntries.size} (${bodyEntries.size - toUpload.size} skipped via SHA-256 check)")

        val uploadSemaphore = Semaphore(PARALLEL_BODY_UPLOADS)
        var uploaded = 0
        coroutineScope {
            toUpload.map { (url, body) ->
                async {
                    uploadSemaphore.withPermit {
                        runCatching { syncRepository.putChapterBody(url, body).getOrThrow() }
                            .onSuccess {
                                uploaded++
                                emit(
                                    SyncProgress.Running(
                                        stage = "Bodies: pushing",
                                        current = uploaded,
                                        total = toUpload.size
                                    )
                                )
                            }
                            .onFailure { Timber.w(it, "putChapterBody failed: $url") }
                    }
                }
            }.awaitAll()
        }
        Timber.d("Bulk sync: pushed $uploaded chapter bodies")
    }

    private suspend fun syncImagesInternal() {
        emit(SyncProgress.Running(stage = "Images: pulling manifest…"))
        var cursor = tokenStorage.getCursor(SyncCursor.IMAGES)
        while (true) {
            val response = syncRepository.pullImageManifest(cursor).getOrThrow()
            if (response.entries.isEmpty()) break

            val toPull = response.entries.filter { !it.deleted && it.sha256.isNotBlank() }
            if (toPull.isNotEmpty()) {
                val semaphore = Semaphore(PARALLEL_IMAGE_DOWNLOADS)
                var completed = 0
                val total = toPull.size
                coroutineScope {
                    toPull.map { entry ->
                        async {
                            semaphore.withPermit {
                                val localBytes = imageService.readBytesForEntry(entry)
                                if (localBytes != null) {
                                    val sha = imageService.sha256Hex(localBytes)
                                    if (sha.equals(entry.sha256, ignoreCase = true)) {
                                        completed++
                                        emit(
                                            SyncProgress.Running(
                                                stage = "Images: pulling",
                                                current = completed,
                                                total = total
                                            )
                                        )
                                        return@async
                                    }
                                }
                                val downloaded = syncRepository.getImageBlob(entry.sha256).getOrNull()
                                if (downloaded != null) {
                                    imageService.writeBytesForEntry(entry, downloaded)
                                }
                                completed++
                                emit(
                                    SyncProgress.Running(
                                        stage = "Images: pulling",
                                        current = completed,
                                        total = total
                                    )
                                )
                            }
                        }
                    }.awaitAll()
                }
            }

            response.entries.filter { it.deleted }.forEach { entry ->
                imageService.deleteForEntry(entry)
            }

            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.IMAGES, cursor)
            if (!response.hasMore) break
        }

        emit(SyncProgress.Running(stage = "Images: enumerating local…"))
        val local = imageService.discoverAllImages()
        if (local.isEmpty()) return

        local.chunked(500).forEachIndexed { i, batch ->
            emit(
                SyncProgress.Running(
                    stage = "Images: announcing",
                    current = i + 1,
                    total = (local.size + 499) / 500
                )
            )
            val refsResult = syncRepository.pushImageReferences(batch).getOrNull() ?: return@forEachIndexed
            val missing = refsResult.missingBlobs.toSet()
            if (missing.isEmpty()) return@forEachIndexed

            val toUpload = batch.filter { it.sha256.lowercase() in missing }
            val uploadSemaphore = Semaphore(PARALLEL_IMAGE_UPLOADS)
            var uploaded = 0
            val total = toUpload.size
            coroutineScope {
                toUpload.map { entry ->
                    async {
                        uploadSemaphore.withPermit {
                            val bytes = imageService.readBytesForEntry(entry)
                            if (bytes != null) {
                                runCatching {
                                    syncRepository.putImageBlob(
                                        sha256 = entry.sha256,
                                        mimeType = imageService.guessMimeType(entry.fileName),
                                        bytes = bytes
                                    ).getOrThrow()
                                }.onFailure {
                                    Timber.w(it, "image upload failed: ${entry.relativePath}")
                                }
                            }
                            uploaded++
                            emit(
                                SyncProgress.Running(
                                    stage = "Images: uploading",
                                    current = uploaded,
                                    total = total
                                )
                            )
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun guard(label: String, block: suspend () -> Unit): Result<Unit> {
        if (!authService.isAuthenticated()) {
            return Result.failure(IllegalStateException("Not authenticated"))
        }
        return try {
            block()
            _progress.value = SyncProgress.Done()
            Timber.d("Sync ($label) completed")
            Result.success(Unit)
        } catch (e: Exception) {
            _progress.value = SyncProgress.Error(e.message ?: e::class.simpleName.orEmpty())
            Timber.e(e, "Sync ($label) failed")
            Result.failure(e)
        }
    }

    private fun emit(p: SyncProgress) { _progress.value = p }

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}

private fun Book.toLibraryBook(chaptersCount: Int, chaptersReadCount: Int): LibraryBook = LibraryBook(
    url = url,
    title = title,
    completed = completed,
    lastReadChapter = lastReadChapter?.takeIf { it.isNotBlank() },
    description = description,
    coverImageUrl = coverImageUrl,
    inLibrary = inLibrary,
    lastReadEpochTimeMilli = lastReadEpochTimeMilli,
    lastUpdatedEpochTimeMilli = System.currentTimeMillis(),
    chaptersCount = chaptersCount,
    chaptersReadCount = chaptersReadCount
)

private fun Chapter.toBookChapter(): BookChapter = BookChapter(
    url = url,
    bookUrl = bookUrl,
    title = title,
    position = position.toFloat(),
    read = read,
    lastReadOffset = lastReadOffset,
    lastReadEpochTimeMilli = 0L
)

private fun BookChapter.toEntityChapter(): Chapter = Chapter(
    title = title,
    url = url,
    bookUrl = bookUrl,
    position = position.toInt(),
    read = read,
    lastReadPosition = 0,
    lastReadOffset = lastReadOffset
)
