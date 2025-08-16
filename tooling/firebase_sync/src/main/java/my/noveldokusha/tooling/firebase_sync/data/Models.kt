package my.noveldokusha.tooling.firebase_sync.data

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
data class SyncMetadata(
    val lastSyncTimestamp: Long = 0L,
    val deviceId: String = "",
    val syncVersion: Int = 1
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterSync(
    val url: String = "",
    val title: String = "",
    val bookUrl: String = "",
    val position: Int = 0,
    val read: Boolean = false,
    val lastReadPosition: Int = 0,
    val lastReadOffset: Int = 0,
    val lastUpdatedEpochTimeMilli: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChapterBodySync(
    val url: String = "",
    val body: String = "",
    val lastUpdatedEpochTimeMilli: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageSync(
    val path: String = "",
    val bookUrl: String = "",
    val checksum: String = "", // MD5 checksum for integrity
    val size: Long = 0L,
    val lastUpdatedEpochTimeMilli: Long = 0L,
    val priority: ImagePriority = ImagePriority.LOW
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
enum class ImagePriority {
    HIGH, // Book covers
    MEDIUM, // Chapter images
    LOW // Other images
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserChapters(
    val userId: String = "",
    val chapters: Map<String, ChapterSync> = emptyMap(),
    val lastSyncTimestamp: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserChapterBodies(
    val userId: String = "",
    val chapterBodies: Map<String, ChapterBodySync> = emptyMap(),
    val lastSyncTimestamp: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserImages(
    val userId: String = "",
    val images: Map<String, ImageSync> = emptyMap(),
    val lastSyncTimestamp: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncProgress(
    val totalItems: Int = 0,
    val processedItems: Int = 0,
    val currentOperation: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class CompleteSyncData(
    val library: UserLibrary = UserLibrary(),
    val chapters: UserChapters = UserChapters(),
    val chapterBodies: UserChapterBodies = UserChapterBodies(),
    val images: UserImages = UserImages(),
    val lastCompleteSyncTimestamp: Long = 0L
)
