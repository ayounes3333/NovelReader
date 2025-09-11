package my.noveldokusha.tooling.local_server_sync.data

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageBackupItem(
    val bookUrl: String,
    val relativePath: String,
    val fileName: String,
    val content: String, // Base64 encoded image content
    val hash: String,
    val size: Long,
    val lastModified: Long,
    val isCoverImage: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageSyncRequest(
    val images: List<ImageBackupItem>,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ImageSyncResponse(
    val images: List<ImageBackupItem>? = null,
    val uploadedCount: Int = 0,
    val success: Boolean = true,
    val message: String = "",
    val serverTimestamp: Long = System.currentTimeMillis()
)
