package my.noveldokusha.tooling.local_server_sync.data

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.Book

// ─── Domain payloads (kept compatible with backend Models.kt) ─────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LibraryBook(
    val url: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val lastReadChapter: String? = null,
    val description: String = "",
    val coverImageUrl: String = "",
    val inLibrary: Boolean = false,
    val lastReadEpochTimeMilli: Long = 0L,
    val lastUpdatedEpochTimeMilli: Long = 0L,
    val chaptersCount: Int = 0,
    val chaptersReadCount: Int = 0
) {
    val asEntityBook: Book
        get() = Book(
            title = title,
            url = url,
            completed = completed,
            lastReadChapter = lastReadChapter ?: "",
            description = description,
            coverImageUrl = coverImageUrl,
            inLibrary = inLibrary,
            lastReadEpochTimeMilli = lastReadEpochTimeMilli
        )
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BookChapter(
    val url: String = "",
    val bookUrl: String = "",
    val title: String = "",
    val position: Float = 0f,
    val read: Boolean = false,
    val lastReadOffset: Int = 0,
    val lastReadEpochTimeMilli: Long = 0L
)

// ─── Auth ─────────────────────────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AuthResponse(
    val success: Boolean = false,
    val message: String = "",
    /** Legacy: same as [accessToken]. */
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessExpiresAt: Long = 0L,
    val refreshExpiresAt: Long = 0L,
    val user: UserInfo? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserInfo(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val lastSyncAt: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class RefreshTokenResponse(
    val success: Boolean = false,
    val message: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessExpiresAt: Long = 0L,
    val refreshExpiresAt: Long = 0L
)

// ─── Sync state probe ──────────────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncStateResponse(
    val serverTime: Long = 0L,
    val libraryLastUpdatedAt: Long = 0L,
    val chaptersLastUpdatedAt: Long = 0L,
    val chapterBodiesLastUpdatedAt: Long = 0L,
    val imagesLastUpdatedAt: Long = 0L,
    val libraryCount: Long = 0L,
    val chapterCount: Long = 0L,
    val chapterBodyCount: Long = 0L,
    val imageCount: Long = 0L
)

// ─── Library incremental sync ──────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LibraryBookEntry(
    val bookUrl: String,
    val payload: LibraryBook? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LibraryChangesRequest(
    val entries: List<LibraryBookEntry> = emptyList()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LibraryChangesResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val applied: List<LibraryBookEntry> = emptyList()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LibraryPullResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val entries: List<LibraryBookEntry> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long = 0L
)

// ─── Chapter incremental sync ─────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BookChapterEntry(
    val chapterUrl: String,
    val payload: BookChapter? = null,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterChangesRequest(
    val entries: List<BookChapterEntry> = emptyList()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterChangesResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val applied: List<BookChapterEntry> = emptyList()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterPullResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val entries: List<BookChapterEntry> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long = 0L
)

// ─── Chapter body manifest (gzip endpoints use raw bytes, no JSON) ────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterBodyManifestEntry(
    val chapterUrl: String,
    val sha256: String = "",
    val size: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterBodyManifestResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val entries: List<ChapterBodyManifestEntry> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long = 0L
)

// ─── Misc generic responses ────────────────────────────────────────────────

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SimpleSuccessResponse(
    val success: Boolean = false,
    val message: String = ""
)
