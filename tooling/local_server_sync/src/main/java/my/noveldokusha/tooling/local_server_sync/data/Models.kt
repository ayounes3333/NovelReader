package my.noveldokusha.tooling.local_server_sync.data

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.Book

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
data class UserLibrary(
    val userId: String = "",
    val books: Map<String, LibraryBook> = emptyMap(),
    val lastSyncTimestamp: Long = 0L
)

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

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterBody(
    val chapterUrl: String = "",
    val body: String = ""
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncRequest(
    val library: UserLibrary? = null,
    val chapters: Map<String, List<BookChapter>>? = null,
    val chapterBodies: Map<String, ChapterBody>? = null,
    val lastSyncTimestamp: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncResponse(
    val library: UserLibrary? = null,
    val chapters: Map<String, List<BookChapter>>? = null,
    val chapterBodies: Map<String, ChapterBody>? = null,
    val success: Boolean = true,
    val message: String = "",
    val serverTimestamp: Long = System.currentTimeMillis()
)

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
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserInfo? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserInfo(
    val id: String,
    val username: String,
    val email: String,
    val lastSyncAt: Long
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncMetadata(
    val lastSyncTimestamp: Long = 0L,
    val deviceId: String = "",
    val syncVersion: Int = 1
)
