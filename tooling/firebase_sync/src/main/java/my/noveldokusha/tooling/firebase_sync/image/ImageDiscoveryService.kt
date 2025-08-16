package my.noveldokusha.tooling.firebase_sync.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.tooling.firebase_sync.data.ImagePriority
import my.noveldokusha.tooling.firebase_sync.data.ImageSync
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appFileResolver: AppFileResolver
) {
    companion object {
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private const val COVER_IMAGE_NAME = "__cover_image"
    }

    suspend fun discoverAllImages(): List<ImageSync> = withContext(Dispatchers.IO) {
        val discoveredImages = mutableListOf<ImageSync>()

        try {
            val booksDirectory = appFileResolver.folderBooks

            if (!booksDirectory.exists() || !booksDirectory.isDirectory) {
                Timber.w("Books directory does not exist: ${booksDirectory.absolutePath}")
                return@withContext emptyList()
            }

            // Scan each book folder
            booksDirectory.listFiles()?.forEach { bookFolder ->
                if (bookFolder.isDirectory) {
                    val bookUrl = decodeBookFolderName(bookFolder.name)
                    val bookImages = scanBookDirectory(bookFolder, bookUrl)
                    discoveredImages.addAll(bookImages)
                }
            }

            Timber.d("Discovered ${discoveredImages.size} images across ${booksDirectory.listFiles()?.size ?: 0} books")
            discoveredImages
        } catch (e: Exception) {
            Timber.e(e, "Error discovering images")
            emptyList()
        }
    }

    suspend fun discoverImagesForBook(bookUrl: String): List<ImageSync> = withContext(Dispatchers.IO) {
        try {
            val bookFolderName = encodeBookFolderName(bookUrl)
            val bookFolder = File(appFileResolver.folderBooks, bookFolderName)

            if (!bookFolder.exists() || !bookFolder.isDirectory) {
                Timber.d("Book folder does not exist for: $bookUrl")
                return@withContext emptyList()
            }

            val bookImages = scanBookDirectory(bookFolder, bookUrl)
            Timber.d("Discovered ${bookImages.size} images for book: $bookUrl")
            bookImages
        } catch (e: Exception) {
            Timber.e(e, "Error discovering images for book: $bookUrl")
            emptyList()
        }
    }

    private fun scanBookDirectory(bookFolder: File, bookUrl: String): List<ImageSync> {
        val images = mutableListOf<ImageSync>()

        try {
            scanDirectoryRecursively(bookFolder, bookUrl, "", images)
        } catch (e: Exception) {
            Timber.w(e, "Error scanning book directory: ${bookFolder.absolutePath}")
        }

        return images
    }

    private fun scanDirectoryRecursively(
        directory: File,
        bookUrl: String,
        relativePath: String,
        images: MutableList<ImageSync>
    ) {
        directory.listFiles()?.forEach { file ->
            val currentRelativePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            when {
                file.isDirectory -> {
                    // Recursively scan subdirectories
                    scanDirectoryRecursively(file, bookUrl, currentRelativePath, images)
                }
                isImageFile(file) -> {
                    val imageSync = createImageSync(file, bookUrl, currentRelativePath)
                    images.add(imageSync)
                }
            }
        }
    }

    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in SUPPORTED_IMAGE_EXTENSIONS
    }

    private fun createImageSync(file: File, bookUrl: String, relativePath: String): ImageSync {
        val priority = determineImagePriority(relativePath)
        val checksum = calculateChecksum(file)

        return ImageSync(
            path = file.absolutePath,
            bookUrl = bookUrl,
            checksum = checksum,
            size = file.length(),
            lastUpdatedEpochTimeMilli = file.lastModified(),
            priority = priority
        )
    }

    private fun determineImagePriority(relativePath: String): ImagePriority {
        return when {
            relativePath == COVER_IMAGE_NAME -> ImagePriority.HIGH
            relativePath.contains("cover", ignoreCase = true) -> ImagePriority.HIGH
            relativePath.contains("chapter", ignoreCase = true) -> ImagePriority.MEDIUM
            relativePath.lowercase().matches(Regex(".*\\d+\\.(jpg|jpeg|png|gif|webp|bmp)")) -> ImagePriority.MEDIUM
            else -> ImagePriority.LOW
        }
    }

    private fun encodeBookFolderName(bookUrl: String): String {
        return Base64.getEncoder().encodeToString(bookUrl.encodeToByteArray())
    }

    private fun decodeBookFolderName(folderName: String): String {
        return try {
            String(Base64.getDecoder().decode(folderName))
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode book folder name: $folderName")
            folderName // Fallback to folder name itself
        }
    }

    private fun calculateChecksum(file: File): String {
        return try {
            val bytes = file.readBytes()
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate checksum for: ${file.absolutePath}")
            ""
        }
    }

    suspend fun cleanupObsoleteImages(validBookUrls: Set<String>) = withContext(Dispatchers.IO) {
        try {
            val booksDirectory = appFileResolver.folderBooks

            if (!booksDirectory.exists()) return@withContext

            val validFolderNames = validBookUrls.map { encodeBookFolderName(it) }.toSet()

            booksDirectory.listFiles()?.forEach { bookFolder ->
                if (bookFolder.isDirectory && bookFolder.name !in validFolderNames) {
                    // This book folder is no longer needed
                    val deletedSize = getFolderSize(bookFolder)
                    bookFolder.deleteRecursively()
                    Timber.d("Cleaned up obsolete book folder: ${bookFolder.name} (${deletedSize / 1024 / 1024} MB)")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    suspend fun getImageStorageStats(): ImageStorageStats = withContext(Dispatchers.IO) {
        val allImages = discoverAllImages()
        val booksDirectory = appFileResolver.folderBooks

        val totalSize = allImages.sumOf { it.size }
        val highPriorityImages = allImages.filter { it.priority == ImagePriority.HIGH }
        val mediumPriorityImages = allImages.filter { it.priority == ImagePriority.MEDIUM }
        val lowPriorityImages = allImages.filter { it.priority == ImagePriority.LOW }

        val bookFolders = booksDirectory.listFiles()?.filter { it.isDirectory }?.size ?: 0

        ImageStorageStats(
            totalImages = allImages.size,
            totalSizeBytes = totalSize,
            totalBooks = bookFolders,
            highPriorityImages = highPriorityImages.size,
            highPrioritySizeBytes = highPriorityImages.sumOf { it.size },
            mediumPriorityImages = mediumPriorityImages.size,
            mediumPrioritySizeBytes = mediumPriorityImages.sumOf { it.size },
            lowPriorityImages = lowPriorityImages.size,
            lowPrioritySizeBytes = lowPriorityImages.sumOf { it.size }
        )
    }

    suspend fun getBookImageStats(bookUrl: String): BookImageStats = withContext(Dispatchers.IO) {
        val bookImages = discoverImagesForBook(bookUrl)
        val totalSize = bookImages.sumOf { it.size }
        val coverImages = bookImages.filter { it.priority == ImagePriority.HIGH }
        val chapterImages = bookImages.filter { it.priority == ImagePriority.MEDIUM }
        val otherImages = bookImages.filter { it.priority == ImagePriority.LOW }

        BookImageStats(
            bookUrl = bookUrl,
            totalImages = bookImages.size,
            totalSizeBytes = totalSize,
            coverImages = coverImages.size,
            coverSizeBytes = coverImages.sumOf { it.size },
            chapterImages = chapterImages.size,
            chapterSizeBytes = chapterImages.sumOf { it.size },
            otherImages = otherImages.size,
            otherSizeBytes = otherImages.sumOf { it.size }
        )
    }

    private fun getFolderSize(folder: File): Long {
        return try {
            folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun createDirectoryStructureForBook(bookUrl: String): File = withContext(Dispatchers.IO) {
        val bookFolderName = encodeBookFolderName(bookUrl)
        val bookFolder = File(appFileResolver.folderBooks, bookFolderName)
        bookFolder.mkdirs()
        bookFolder
    }

    suspend fun saveImageForBook(bookUrl: String, relativePath: String, imageData: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val bookFolder = createDirectoryStructureForBook(bookUrl)
            val imageFile = File(bookFolder, relativePath)

            // Create parent directories if they don't exist
            imageFile.parentFile?.mkdirs()

            imageFile.writeBytes(imageData)
            imageFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to save image for book: $bookUrl, path: $relativePath")
            null
        }
    }
}

data class ImageStorageStats(
    val totalImages: Int,
    val totalSizeBytes: Long,
    val totalBooks: Int,
    val highPriorityImages: Int,
    val highPrioritySizeBytes: Long,
    val mediumPriorityImages: Int,
    val mediumPrioritySizeBytes: Long,
    val lowPriorityImages: Int,
    val lowPrioritySizeBytes: Long
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
    val highPrioritySizeMB: Double get() = highPrioritySizeBytes / (1024.0 * 1024.0)
    val mediumPrioritySizeMB: Double get() = mediumPrioritySizeBytes / (1024.0 * 1024.0)
    val lowPrioritySizeMB: Double get() = lowPrioritySizeBytes / (1024.0 * 1024.0)
    val averageImagesPerBook: Double get() = if (totalBooks > 0) totalImages.toDouble() / totalBooks else 0.0
    val averageSizePerBook: Double get() = if (totalBooks > 0) totalSizeMB / totalBooks else 0.0
}

data class BookImageStats(
    val bookUrl: String,
    val totalImages: Int,
    val totalSizeBytes: Long,
    val coverImages: Int,
    val coverSizeBytes: Long,
    val chapterImages: Int,
    val chapterSizeBytes: Long,
    val otherImages: Int,
    val otherSizeBytes: Long
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
    val coverSizeMB: Double get() = coverSizeBytes / (1024.0 * 1024.0)
    val chapterSizeMB: Double get() = chapterSizeBytes / (1024.0 * 1024.0)
    val otherSizeMB: Double get() = otherSizeBytes / (1024.0 * 1024.0)
}
