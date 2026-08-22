package my.noveldokusha.tooling.quick_setup.transfer

import kotlinx.serialization.Serializable

@Serializable
data class TransferManifest(
    val schemaVersion: Int,
    val booksCount: Int,
    val chaptersCount: Int,
    val chapterBodiesCount: Int,
    val imagesCount: Int,
    val totalImageBytes: Long,
    val hasPreferences: Boolean,
    val deviceName: String,
)

@Serializable
data class ImageManifestEntry(
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
data class TransferProgress(
    val phase: Phase,
    val itemsTransferred: Int,
    val totalItems: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    enum class Phase {
        CONNECTING,
        TRANSFERRING_DATABASE,
        TRANSFERRING_IMAGES,
        TRANSFERRING_PREFERENCES,
        COMPLETED,
        ERROR,
    }

    val percentComplete: Float
        get() = if (totalItems > 0) itemsTransferred.toFloat() / totalItems else 0f
}
