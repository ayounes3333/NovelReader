package my.noveldokusha.tooling.local_server_sync.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.tooling.local_server_sync.data.ImageManifestEntry
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walks `folderBooks` to enumerate the user's image files and converts them
 * into [ImageManifestEntry] objects. Computes content-addressed SHA-256 hex
 * hashes the same way the backend does (lowercase hex of the raw bytes).
 *
 * Restore writes raw bytes downloaded from the server back to disk using the
 * same `<bookFolder>/<relativePath>` layout.
 */
@Singleton
class LocalServerImageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appFileResolver: AppFileResolver
) {
    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private const val COVER_IMAGE_NAME = "__cover_image"
        private const val MAX_IMAGE_SIZE_BYTES = 10L * 1024L * 1024L // 10 MiB
    }

    val folderBooks: File get() = appFileResolver.folderBooks

    // ── Discovery ────────────────────────────────────────────────────────

    /**
     * Scans the entire books directory and returns one [ImageManifestEntry]
     * per local image file, with SHA-256 hex computed by streaming the file.
     * Large files (>10 MiB) are skipped.
     */
    suspend fun discoverAllImages(): List<ImageManifestEntry> = withContext(Dispatchers.IO) {
        val booksDir = folderBooks
        if (!booksDir.exists() || !booksDir.isDirectory) {
            Timber.w("Books directory does not exist: ${booksDir.absolutePath}")
            return@withContext emptyList()
        }

        val out = mutableListOf<ImageManifestEntry>()
        booksDir.listFiles()?.forEach { bookFolder ->
            if (!bookFolder.isDirectory) return@forEach
            val bookUrl = decodeBookFolderName(bookFolder.name)
            scanInto(bookFolder, bookUrl, "", out)
        }
        Timber.d("Discovered ${out.size} images across all books")
        out
    }

    /** Same as [discoverAllImages] but limited to a single book URL. */
    suspend fun discoverImagesForBook(bookUrl: String): List<ImageManifestEntry> =
        withContext(Dispatchers.IO) {
            val folder = File(folderBooks, encodeBookFolderName(bookUrl))
            if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()
            val out = mutableListOf<ImageManifestEntry>()
            scanInto(folder, bookUrl, "", out)
            out
        }

    private fun scanInto(
        directory: File,
        bookUrl: String,
        relativePath: String,
        out: MutableList<ImageManifestEntry>
    ) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> {
                    val subPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                    scanInto(file, bookUrl, subPath, out)
                }
                file.isFile && isImageFile(file) -> {
                    if (file.length() > MAX_IMAGE_SIZE_BYTES) {
                        Timber.w("Skipping large image: ${file.absolutePath} (${file.length() / 1024 / 1024} MiB)")
                        return@forEach
                    }
                    val rel = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                    val sha = sha256HexStreaming(file) ?: return@forEach
                    out += ImageManifestEntry(
                        bookUrl = bookUrl,
                        relativePath = rel,
                        fileName = file.name,
                        sha256 = sha,
                        size = file.length(),
                        isCoverImage = rel.contains(COVER_IMAGE_NAME),
                        updatedAt = file.lastModified(),
                        deleted = false
                    )
                }
            }
        }
    }

    // ── Read / write helpers ─────────────────────────────────────────────

    fun isImageFile(file: File): Boolean =
        file.extension.lowercase() in SUPPORTED_EXTENSIONS

    fun guessMimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }

    /**
     * Locates a file in the books folder for [entry] and returns its raw bytes
     * along with its SHA-256, or null if it no longer exists / can't be read.
     */
    fun readBytesForEntry(entry: ImageManifestEntry): ByteArray? = try {
        val folder = File(folderBooks, encodeBookFolderName(entry.bookUrl))
        val file = File(folder, entry.relativePath)
        if (!file.isFile) null
        else if (file.length() > MAX_IMAGE_SIZE_BYTES) null
        else file.readBytes()
    } catch (e: Exception) {
        Timber.w(e, "readBytesForEntry failed for ${entry.relativePath}")
        null
    }

    /**
     * Writes [bytes] downloaded from the server to the on-disk location
     * implied by [entry]. Returns true on success.
     */
    fun writeBytesForEntry(entry: ImageManifestEntry, bytes: ByteArray): Boolean {
        return try {
            val booksDir = folderBooks
            if (!booksDir.exists()) booksDir.mkdirs()
            val bookDir = File(booksDir, encodeBookFolderName(entry.bookUrl))
            val file = File(bookDir, entry.relativePath)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            if (entry.updatedAt > 0L) file.setLastModified(entry.updatedAt)
            true
        } catch (e: Exception) {
            Timber.e(e, "writeBytesForEntry failed: ${entry.relativePath}")
            false
        }
    }

    fun deleteForEntry(entry: ImageManifestEntry): Boolean {
        return try {
            val bookDir = File(folderBooks, encodeBookFolderName(entry.bookUrl))
            File(bookDir, entry.relativePath).delete()
        } catch (_: Exception) {
            false
        }
    }

    // ── Hashing / folder name encoding ───────────────────────────────────

    /**
     * SHA-256 hex (lowercase) over the file's raw bytes, computed in 64 KiB
     * chunks so we never hold the whole file in memory.
     */
    fun sha256HexStreaming(file: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        md.digest().toHexLower()
    } catch (e: Exception) {
        Timber.w(e, "sha256 failed for ${file.absolutePath}")
        null
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexLower()

    fun encodeBookFolderName(bookUrl: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bookUrl.toByteArray())

    fun decodeBookFolderName(folderName: String): String = try {
        String(Base64.getUrlDecoder().decode(folderName))
    } catch (_: Exception) {
        Timber.w("Failed to decode folder name: $folderName")
        folderName
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        val hex = "0123456789abcdef".toCharArray()
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(hex[v ushr 4]).append(hex[v and 0x0F])
        }
        return sb.toString()
    }
}
