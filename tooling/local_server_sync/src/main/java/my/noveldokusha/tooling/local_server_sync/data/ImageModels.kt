package my.noveldokusha.tooling.local_server_sync.data

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

/**
 * Reference from a user to an image blob, matching backend `UserImages` rows.
 * The blob itself is content-addressed by [sha256] and uploaded separately
 * (binary PUT to `/api/sync/images/{sha256}`).
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageManifestEntry(
    val bookUrl: String,
    val relativePath: String,
    val fileName: String,
    val sha256: String,
    val size: Long = 0L,
    val isCoverImage: Boolean = false,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageManifestResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    val entries: List<ImageManifestEntry> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long = 0L
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageReferencesRequest(
    val entries: List<ImageManifestEntry> = emptyList()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageReferencesResponse(
    val success: Boolean = true,
    val message: String = "",
    val serverTime: Long = 0L,
    /** SHA-256s the server has no blob for. Client must PUT them as binary. */
    val missingBlobs: List<String> = emptyList()
)
