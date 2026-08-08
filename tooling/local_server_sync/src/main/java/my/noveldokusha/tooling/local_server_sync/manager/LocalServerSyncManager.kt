package my.noveldokusha.tooling.local_server_sync.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.data.BookChapter
import my.noveldokusha.tooling.local_server_sync.data.BookChapterEntry
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

/**
 * Streamed state exposed for UI / foreground notification.
 */
sealed class SyncProgress {
    object Idle : SyncProgress()
    data class Running(val stage: String, val current: Int = 0, val total: Int = 0) : SyncProgress()
    data class Error(val message: String) : SyncProgress()
    data class Done(val timestamp: Long = System.currentTimeMillis()) : SyncProgress()
}

/**
 * Orchestrates two sync modes:
 *  - [performQuickProgressSync]: lightweight library + chapter row sync. Cheap
 *    enough to run on app start / when the screen comes to foreground.
 *  - [performBulkSync]: full sync including chapter bodies and image blobs.
 *    Should be run when on home WiFi (unmetered) only.
 */
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

    // ── Public entry points ───────────────────────────────────────────────

    /** Quick progress sync. Pulls + pushes library and chapter rows only. */
    suspend fun performQuickProgressSync(): Result<Unit> = guard("quick-progress") {
        pullAndApplyLibrary()
        pushLocalLibrary()
        pullAndApplyChapters()
        pushLocalChapters()
    }

    /** Full sync: rows + chapter bodies + image blobs. WiFi only. */
    suspend fun performBulkSync(): Result<Unit> = guard("bulk") {
        pullAndApplyLibrary()
        pushLocalLibrary()
        pullAndApplyChapters()
        pushLocalChapters()
        syncChapterBodies()
        syncImagesInternal()
    }

    // ── Legacy aliases kept for the existing UI ───────────────────────────

    /** Legacy name retained for the settings UI. Performs a quick sync. */
    suspend fun syncLibrary(): Result<Unit> = performQuickProgressSync()

    /** Legacy name. Performs a full bulk sync. */
    suspend fun performCompleteSync(): Result<Unit> = performBulkSync()

    /** Legacy name. Performs image sync only (manifest + missing blobs). */
    suspend fun syncImages(): Result<Unit> = guard("images") {
        syncImagesInternal()
    }

    // ── Internal: library ─────────────────────────────────────────────────

    private suspend fun pullAndApplyLibrary() {
        emit(SyncProgress.Running(stage = "Library: pulling…"))
        var cursor = tokenStorage.getCursor(SyncCursor.LIBRARY)
        while (true) {
            val response = syncRepository.pullLibrary(cursor).getOrThrow()
            if (response.entries.isEmpty()) break
            for (entry in response.entries) {
                if (entry.deleted) {
                    // Soft-delete locally: drop the inLibrary flag rather than
                    // wiping the row, to preserve local read progress.
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
        // Server timestamps the rows; we just record the highest known cursor.
        val applied = syncRepository.pushLibrary(entries).getOrThrow()
        val maxApplied = applied.maxOfOrNull { it.updatedAt } ?: 0L
        if (maxApplied > 0L) tokenStorage.setCursor(SyncCursor.LIBRARY, maxApplied)
    }

    // ── Internal: chapters ────────────────────────────────────────────────

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
        // Naive approach for first cut: push chapters of every in-library book.
        // The backend's `applyChapterChanges` is idempotent so duplicate pushes
        // are safe, but they do bump updatedAt. A future iteration should
        // track per-row local change timestamps.
        val entries = mutableListOf<BookChapterEntry>()
        for (book in books) {
            if (!book.inLibrary) continue
            val chapters = localLibraryRepository.getChapters(book.url)
            for (chapter in chapters) {
                // Only push chapters with progress to avoid spamming server.
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
        // Chunk to keep request bodies reasonable.
        entries.chunked(500).forEach { batch ->
            val applied = syncRepository.pushChapters(batch).getOrThrow()
            val maxApplied = applied.maxOfOrNull { it.updatedAt } ?: 0L
            if (maxApplied > 0L) tokenStorage.setCursor(SyncCursor.CHAPTERS, maxApplied)
        }
    }

    // ── Internal: chapter bodies ──────────────────────────────────────────

    private suspend fun syncChapterBodies() {
        emit(SyncProgress.Running(stage = "Bodies: pulling manifest…"))
        // Pull side first: download missing bodies one at a time (gzip).
        var cursor = tokenStorage.getCursor(SyncCursor.CHAPTER_BODIES)
        while (true) {
            val response = syncRepository.pullChapterBodyManifest(cursor).getOrThrow()
            if (response.entries.isEmpty()) break
            for ((idx, entry) in response.entries.withIndex()) {
                emit(SyncProgress.Running(stage = "Bodies: pulling", current = idx, total = response.entries.size))
                if (entry.deleted) continue
                val existing = localLibraryRepository.getChapterBody(entry.chapterUrl)
                if (existing != null) {
                    val sha = imageService.sha256Hex(existing.toByteArray(Charsets.UTF_8))
                    if (sha.equals(entry.sha256, ignoreCase = true)) continue
                }
                val downloaded = syncRepository.getChapterBody(entry.chapterUrl).getOrNull() ?: continue
                localLibraryRepository.saveChapterBody(entry.chapterUrl, downloaded.first)
            }
            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.CHAPTER_BODIES, cursor)
            if (!response.hasMore) break
        }

        // Push side: upload local bodies the server doesn't have / are stale.
        emit(SyncProgress.Running(stage = "Bodies: pushing…"))
        val books = localLibraryRepository.getAllBooks().filter { it.inLibrary }
        var pushed = 0
        for (book in books) {
            val chapters = localLibraryRepository.getChapters(book.url)
            for (chapter in chapters) {
                val body = localLibraryRepository.getChapterBody(chapter.url) ?: continue
                if (body.isBlank()) continue
                // Best-effort: just upload. Server tolerates duplicate uploads.
                runCatching { syncRepository.putChapterBody(chapter.url, body).getOrThrow() }
                    .onSuccess { pushed++ }
                    .onFailure { Timber.w(it, "putChapterBody failed: ${chapter.url}") }
            }
        }
        Timber.d("Bulk sync: pushed $pushed chapter bodies")
    }

    // ── Internal: images ──────────────────────────────────────────────────

    private suspend fun syncImagesInternal() {
        emit(SyncProgress.Running(stage = "Images: pulling manifest…"))
        // Pull: download any images this device is missing.
        var cursor = tokenStorage.getCursor(SyncCursor.IMAGES)
        while (true) {
            val response = syncRepository.pullImageManifest(cursor).getOrThrow()
            if (response.entries.isEmpty()) break
            for ((idx, entry) in response.entries.withIndex()) {
                emit(SyncProgress.Running(stage = "Images: pulling", current = idx, total = response.entries.size))
                if (entry.deleted) {
                    imageService.deleteForEntry(entry)
                    continue
                }
                if (entry.sha256.isBlank()) continue
                // Skip if the local file exists and matches the sha already.
                val localBytes = imageService.readBytesForEntry(entry)
                if (localBytes != null) {
                    val sha = imageService.sha256Hex(localBytes)
                    if (sha.equals(entry.sha256, ignoreCase = true)) continue
                }
                val downloaded = syncRepository.getImageBlob(entry.sha256).getOrNull() ?: continue
                imageService.writeBytesForEntry(entry, downloaded)
            }
            cursor = response.nextCursor
            tokenStorage.setCursor(SyncCursor.IMAGES, cursor)
            if (!response.hasMore) break
        }

        // Push: enumerate local images, ask server which blobs it needs.
        emit(SyncProgress.Running(stage = "Images: enumerating local…"))
        val local = imageService.discoverAllImages()
        if (local.isEmpty()) return

        local.chunked(500).forEachIndexed { i, batch ->
            emit(SyncProgress.Running(stage = "Images: announcing", current = i + 1, total = (local.size + 499) / 500))
            val refsResult = syncRepository.pushImageReferences(batch).getOrNull() ?: return@forEachIndexed
            val missing = refsResult.missingBlobs.toSet()
            if (missing.isEmpty()) return@forEachIndexed
            // Upload each missing blob's bytes.
            for ((j, entry) in batch.withIndex()) {
                if (entry.sha256.lowercase() !in missing) continue
                emit(
                    SyncProgress.Running(
                        stage = "Images: uploading",
                        current = j + 1,
                        total = batch.size
                    )
                )
                val bytes = imageService.readBytesForEntry(entry) ?: continue
                runCatching {
                    syncRepository.putImageBlob(
                        sha256 = entry.sha256,
                        mimeType = imageService.guessMimeType(entry.fileName),
                        bytes = bytes
                    ).getOrThrow()
                }.onFailure { Timber.w(it, "image upload failed: ${entry.relativePath}") }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

// ── Local entity ↔ wire conversions ──────────────────────────────────────

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
