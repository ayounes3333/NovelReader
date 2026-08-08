package my.noveldokusha.tooling.local_server_sync.repository

import my.noveldokusha.tooling.local_server_sync.api.LocalServerApiService
import my.noveldokusha.tooling.local_server_sync.data.BookChapterEntry
import my.noveldokusha.tooling.local_server_sync.data.ChapterBodyManifestResponse
import my.noveldokusha.tooling.local_server_sync.data.ChapterChangesRequest
import my.noveldokusha.tooling.local_server_sync.data.ChapterPullResponse
import my.noveldokusha.tooling.local_server_sync.data.ImageManifestEntry
import my.noveldokusha.tooling.local_server_sync.data.ImageManifestResponse
import my.noveldokusha.tooling.local_server_sync.data.ImageReferencesRequest
import my.noveldokusha.tooling.local_server_sync.data.ImageReferencesResponse
import my.noveldokusha.tooling.local_server_sync.data.LibraryBookEntry
import my.noveldokusha.tooling.local_server_sync.data.LibraryChangesRequest
import my.noveldokusha.tooling.local_server_sync.data.LibraryPullResponse
import my.noveldokusha.tooling.local_server_sync.data.SyncStateResponse
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin transport layer wrapping [LocalServerApiService] with `Result<T>`
 * semantics so callers (workers, manager) don't have to deal with thrown
 * exceptions or null returns directly.
 */
@Singleton
class LocalServerSyncRepository @Inject constructor(
    private val apiService: LocalServerApiService
) {

    suspend fun getSyncState(): Result<SyncStateResponse> = runCatching { apiService.getSyncState() }

    // ── Library ─────────────────────────────────────────────────────────

    suspend fun pushLibrary(entries: List<LibraryBookEntry>): Result<List<LibraryBookEntry>> =
        runCatching {
            val response = apiService.pushLibraryChanges(LibraryChangesRequest(entries))
            if (!response.success) error(response.message.ifBlank { "library push failed" })
            response.applied
        }

    suspend fun pullLibrary(since: Long, limit: Int = 500): Result<LibraryPullResponse> =
        runCatching { apiService.pullLibraryChanges(since, limit) }

    // ── Chapters ────────────────────────────────────────────────────────

    suspend fun pushChapters(entries: List<BookChapterEntry>): Result<List<BookChapterEntry>> =
        runCatching {
            val response = apiService.pushChapterChanges(ChapterChangesRequest(entries))
            if (!response.success) error(response.message.ifBlank { "chapters push failed" })
            response.applied
        }

    suspend fun pullChapters(since: Long, limit: Int = 1000): Result<ChapterPullResponse> =
        runCatching { apiService.pullChapterChanges(since, limit) }

    // ── Chapter bodies ──────────────────────────────────────────────────

    suspend fun pullChapterBodyManifest(since: Long, limit: Int = 500): Result<ChapterBodyManifestResponse> =
        runCatching { apiService.pullChapterBodyManifest(since, limit) }

    suspend fun putChapterBody(chapterUrl: String, body: String): Result<Unit> = runCatching {
        if (!apiService.putChapterBody(chapterUrl, body)) error("chapter body upload failed")
    }

    suspend fun getChapterBody(chapterUrl: String): Result<Pair<String, String>?> =
        runCatching { apiService.getChapterBody(chapterUrl) }

    suspend fun deleteChapterBody(chapterUrl: String): Result<Unit> = runCatching {
        if (!apiService.deleteChapterBody(chapterUrl)) error("chapter body delete failed")
    }

    // ── Images ──────────────────────────────────────────────────────────

    suspend fun pullImageManifest(since: Long, limit: Int = 500): Result<ImageManifestResponse> =
        runCatching { apiService.pullImageManifest(since, limit) }

    suspend fun pushImageReferences(entries: List<ImageManifestEntry>): Result<ImageReferencesResponse> =
        runCatching { apiService.postImageReferences(ImageReferencesRequest(entries)) }

    suspend fun imageBlobExists(sha256: String): Boolean = try {
        apiService.headImageBlob(sha256)
    } catch (_: Exception) {
        false
    }

    suspend fun putImageBlob(sha256: String, mimeType: String, bytes: ByteArray): Result<Unit> = runCatching {
        if (!apiService.putImageBlob(sha256, mimeType, bytes)) error("image upload failed")
    }

    suspend fun getImageBlob(sha256: String): Result<ByteArray?> =
        runCatching { apiService.getImageBlob(sha256) }

    // ── Discovery ───────────────────────────────────────────────────────

    suspend fun pingServer(serverUrl: String? = null): Boolean = try {
        apiService.pingServer(serverUrl)
    } catch (e: Exception) {
        Timber.w(e, "ping failed")
        false
    }
}
