package my.noveldokusha.tooling.local_server_sync.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ImageCacheEntry(
    val sha256: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Persists SHA-256 hashes keyed by (bookUrl, relativePath) so that repeated
 * syncs don't re-hash every image file on disk. The cache is stored as a JSON
 * file in the app's internal files directory.
 */
@Singleton
class ImageIndexCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheFile: File
        get() = File(context.filesDir, "image_index_cache.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private suspend fun loadCache(): Map<String, Map<String, ImageCacheEntry>> =
        withContext(Dispatchers.IO) {
            if (!cacheFile.isFile) return@withContext emptyMap()
            try {
                val raw = cacheFile.readText()
                json.decodeFromString<Map<String, Map<String, ImageCacheEntry>>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

    private suspend fun saveCache(cache: Map<String, Map<String, ImageCacheEntry>>) =
        withContext(Dispatchers.IO) {
            try {
                cacheFile.writeText(json.encodeToString(cache))
            } catch (_: Exception) {
                // Best-effort: ignore write failures
            }
        }

    /**
     * Returns the cached [ImageCacheEntry] for the given book + path, or null
     * if it's not cached.
     */
    suspend fun get(bookUrl: String, relativePath: String): ImageCacheEntry? {
        val cache = loadCache()
        return cache[bookUrl]?.get(relativePath)
    }

    /**
     * Stores the SHA-256 entry for the given image.
     */
    suspend fun put(bookUrl: String, relativePath: String, entry: ImageCacheEntry) {
        val cache = loadCache().toMutableMap()
        val bookMap = cache.getOrPut(bookUrl) { mutableMapOf() }.toMutableMap()
        bookMap[relativePath] = entry
        cache[bookUrl] = bookMap
        saveCache(cache)
    }

    /**
     * Bulk-insert entries for a single book. More efficient than calling [put]
     * many times because it only writes the file once.
     */
    suspend fun putAll(bookUrl: String, entries: List<Pair<String, ImageCacheEntry>>) {
        if (entries.isEmpty()) return
        val cache = loadCache().toMutableMap()
        val bookMap = cache.getOrPut(bookUrl) { mutableMapOf() }.toMutableMap()
        entries.forEach { (path, entry) ->
            bookMap[path] = entry
        }
        cache[bookUrl] = bookMap
        saveCache(cache)
    }

    /**
     * Validates an image file against the cache. Returns true if the cached
     * entry matches the file's current lastModified time and size.
     */
    fun isValid(file: File, entry: ImageCacheEntry): Boolean =
        file.isFile && file.lastModified() == entry.lastModified && file.length() == entry.size

    /**
     * Removes entries for a specific book (e.g. after deletion).
     */
    suspend fun removeBook(bookUrl: String) {
        val cache = loadCache().toMutableMap()
        cache.remove(bookUrl)
        saveCache(cache)
    }

    /**
     * Removes all cached entries.
     */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            cacheFile.delete()
        }
    }
}
