package my.noveldokusha.tooling.local_server_sync.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.tooling.local_server_sync.data.ImageBackupItem
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalServerImageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appFileResolver: AppFileResolver
) {
    companion object {
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private const val COVER_IMAGE_NAME = "__cover_image"
        private const val MAX_IMAGE_SIZE_MB = 5 // Reduced from 10MB to 5MB
        private const val MAX_IMAGE_SIZE_BYTES = MAX_IMAGE_SIZE_MB * 1024 * 1024
        private const val MAX_BATCH_SIZE = 10 // Reduced from 50 to 10 for memory safety
        private const val MEMORY_THRESHOLD_MB = 50 // Minimum free memory required
    }

    suspend fun discoverAllImages(): List<ImageBackupItem> = withContext(Dispatchers.IO) {
        val discoveredImages = mutableListOf<ImageBackupItem>()

        try {
            logMemoryStatus("Start of discoverAllImages")

            val booksDirectory = appFileResolver.folderBooks

            if (!booksDirectory.exists() || !booksDirectory.isDirectory) {
                Timber.w("Books directory does not exist: ${booksDirectory.absolutePath}")
                return@withContext emptyList()
            }

            val bookFolders = booksDirectory.listFiles() ?: emptyArray()
            Timber.d("Found ${bookFolders.size} book folders to scan")

            // Scan each book folder recursively with batch processing
            bookFolders.forEachIndexed { index, bookFolder ->
                if (bookFolder.isDirectory) {
                    logMemoryStatus("Before scanning book folder ${index + 1}/${bookFolders.size}: ${bookFolder.name}")

                    val bookUrl = decodeBookFolderName(bookFolder.name)
                    val bookImages = scanBookDirectoryRecursively(bookFolder, bookUrl, "")
                    discoveredImages.addAll(bookImages)

                    logMemoryStatus("After scanning book folder ${index + 1}/${bookFolders.size}, total images: ${discoveredImages.size}")

                    // Force garbage collection if we have too many images in memory
                    if (discoveredImages.size > MAX_BATCH_SIZE * 2) {
                        Timber.d("Triggering GC at ${discoveredImages.size} images")
                        System.gc()
                        logMemoryStatus("After GC at ${discoveredImages.size} images")
                    }
                }
            }

            logMemoryStatus("End of discoverAllImages")
            Timber.d("Discovered ${discoveredImages.size} images across ${bookFolders.size} books")
            discoveredImages
        } catch (e: OutOfMemoryError) {
            logMemoryStatus("OutOfMemoryError in discoverAllImages")
            Timber.e("Out of memory while discovering images. Processed ${discoveredImages.size} images before failure")
            // Return what we've processed so far
            discoveredImages
        } catch (e: Exception) {
            logMemoryStatus("Exception in discoverAllImages")
            Timber.e(e, "Error discovering images")
            emptyList()
        }
    }

    private suspend fun scanBookDirectoryRecursively(
        directory: File,
        bookUrl: String,
        relativePath: String
    ): List<ImageBackupItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageBackupItem>()

        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // Recursively scan subdirectories
                    val subPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                    val subImages = scanBookDirectoryRecursively(file, bookUrl, subPath)
                    images.addAll(subImages)
                } else if (file.isFile && isImageFile(file)) {
                    // Create image backup item
                    val filePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                    val imageItem = createImageBackupItem(file, bookUrl, filePath)
                    if (imageItem != null) {
                        images.add(imageItem)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning directory: ${directory.absolutePath}")
        }

        images
    }

    private fun createImageBackupItem(file: File, bookUrl: String, relativePath: String): ImageBackupItem? {
        return try {
            logMemoryStatus("Before processing image: ${file.name} (${file.length() / 1024}KB)")

            // Check file size first to avoid loading large files
            if (file.length() > MAX_IMAGE_SIZE_BYTES) {
                Timber.w("Skipping large image file (${file.length() / 1024 / 1024}MB): ${file.absolutePath}")
                return null
            }

            // Read file in chunks to be more memory efficient
            logMemoryStatus("Before reading file bytes: ${file.name}")
            val content = file.readBytes()
            logMemoryStatus("After reading file bytes: ${file.name} (${content.size / 1024}KB)")

            logMemoryStatus("Before generating hash: ${file.name}")
            val hash = generateFileHash(content)
            logMemoryStatus("After generating hash: ${file.name}")

            // Use streaming Base64 encoding for large files
            logMemoryStatus("Before Base64 encoding: ${file.name}")
            val encodedContent = try {
                val encoded = Base64.getEncoder().encodeToString(content)
                logMemoryStatus("After Base64 encoding: ${file.name} (encoded size: ${encoded.length / 1024}KB)")
                encoded
            } catch (e: OutOfMemoryError) {
                logMemoryStatus("OutOfMemoryError during Base64 encoding: ${file.name}")
                Timber.w("Out of memory encoding image, skipping: ${file.absolutePath}")
                return null
            }

            logMemoryStatus("Before creating ImageBackupItem: ${file.name}")
            val imageItem = ImageBackupItem(
                bookUrl = bookUrl,
                relativePath = relativePath,
                fileName = file.name,
                content = encodedContent,
                hash = hash,
                size = content.size.toLong(),
                lastModified = file.lastModified(),
                isCoverImage = relativePath.contains(COVER_IMAGE_NAME)
            )
            logMemoryStatus("After creating ImageBackupItem: ${file.name}")

            imageItem
        } catch (e: OutOfMemoryError) {
            logMemoryStatus("OutOfMemoryError in createImageBackupItem: ${file.name}")
            Timber.w("Out of memory processing image, skipping: ${file.absolutePath}")
            null
        } catch (e: Exception) {
            logMemoryStatus("Exception in createImageBackupItem: ${file.name}")
            Timber.e(e, "Error creating image backup item for: ${file.absolutePath}")
            null
        }
    }

    suspend fun restoreImages(images: List<ImageBackupItem>): Result<Int> = withContext(Dispatchers.IO) {
        var restoredCount = 0

        try {
            val booksDirectory = appFileResolver.folderBooks
            if (!booksDirectory.exists()) {
                booksDirectory.mkdirs()
            }

            images.forEach { imageItem ->
                try {
                    val success = restoreImage(imageItem, booksDirectory)
                    if (success) {
                        restoredCount++
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error restoring image: ${imageItem.relativePath}")
                }
            }

            Timber.d("Successfully restored $restoredCount/${images.size} images")
            Result.success(restoredCount)
        } catch (e: Exception) {
            Timber.e(e, "Error during image restoration")
            Result.failure(e)
        }
    }

    private fun restoreImage(imageItem: ImageBackupItem, booksDirectory: File): Boolean {
        return try {
            // Decode book folder name and create book directory
            val bookFolderName = encodeBookFolderName(imageItem.bookUrl)
            val bookDirectory = File(booksDirectory, bookFolderName)

            // Create the full path including subdirectories
            val imageFile = File(bookDirectory, imageItem.relativePath)
            val parentDir = imageFile.parentFile

            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Decode and write the image content
            val content = Base64.getDecoder().decode(imageItem.content)
            imageFile.writeBytes(content)

            // Set last modified time
            imageFile.setLastModified(imageItem.lastModified)

            Timber.d("Restored image: ${imageFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore image: ${imageItem.relativePath}")
            false
        }
    }

    // Make these methods accessible to the sync manager
    fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in SUPPORTED_IMAGE_EXTENSIONS
    }

    fun createImageBackupItemSafe(file: File, bookUrl: String, relativePath: String): ImageBackupItem? {
        return createImageBackupItem(file, bookUrl, relativePath)
    }

    fun decodeBookFolderName(folderName: String): String {
        return try {
            String(Base64.getUrlDecoder().decode(folderName))
        } catch (e: Exception) {
            Timber.w("Failed to decode folder name: $folderName")
            folderName
        }
    }

    // Make appFileResolver accessible
    val folderBooks: File get() = this.appFileResolver.folderBooks

    private fun generateFileHash(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content)
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun encodeBookFolderName(bookUrl: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bookUrl.toByteArray())
    }

    private fun logMemoryStatus(context: String) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
        val totalMemory = runtime.totalMemory() / 1024 / 1024 // MB
        val freeMemory = runtime.freeMemory() / 1024 / 1024 // MB
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxMemory - usedMemory

        Timber.d("[$context] Memory - Free: ${freeMemory}MB, Used: ${usedMemory}MB, Available: ${availableMemory}MB, Max: ${maxMemory}MB")
    }
}
