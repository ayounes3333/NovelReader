package my.noveldokusha.tooling.quick_setup.client

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.quick_setup.transfer.ImageManifestEntry
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupImporter
import my.noveldokusha.tooling.quick_setup.transfer.TransferManifest
import my.noveldokusha.tooling.quick_setup.transfer.TransferProgress
import my.noveldokusha.tooling.quick_setup.transfer.dto.BookDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterBodyDto
import my.noveldokusha.tooling.quick_setup.transfer.dto.ChapterDto
import my.noveldokusha.tooling.quick_setup.transfer.resilience.TransferSessionStore
import my.noveldokusha.tooling.quick_setup.transfer.resilience.TransferSummary
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class QuickSetupClient(
    private val host: String,
    private val port: Int,
    private val sessionToken: String,
    private val importer: QuickSetupImporter,
    private val sessionStore: TransferSessionStore? = null,
    private val onProgress: ((TransferProgress) -> Unit)? = null,
) {
    companion object {
        const val BOOKS_PAGE_SIZE = 100
        const val CHAPTERS_PAGE_SIZE = 200
        const val BODIES_PAGE_SIZE = 50
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 750L
        private const val ACCEPT_POLL_INTERVAL_MS = 2_000L
        private const val ACCEPT_WAIT_TIMEOUT_MS = 120_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://$host:$port"

    @Volatile
    var isCancelled = false
        private set

    private var startTime = 0L
    private var transferredBooks = 0
    private var transferredChapters = 0
    private var transferredBodies = 0
    private var transferredImages = 0
    private var transferredPreferences = false
    private val errors = mutableListOf<String>()

    // Resume state (populated in transferAll)
    private lateinit var manifest: TransferManifest
    private lateinit var sessionId: String
    private val completedBookPages = mutableSetOf<Int>()
    private val completedChapterPages = mutableSetOf<Int>()
    private val completedBodyPages = mutableSetOf<Int>()
    private val importedImageHashes = mutableSetOf<String>()
    private var importedPrefs = false

    fun cancel() {
        isCancelled = true
        // Notify the server asynchronously — cancel() may be called from the
        // main thread and a synchronous call would throw NetworkOnMainThreadException.
        try {
            httpClient.newCall(authenticatedPost("$baseUrl/qs/cancel"))
                .enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Timber.w(e, "Failed to notify server of cancellation")
                    }

                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        response.close()
                    }
                })
        } catch (_: Exception) {}
    }

    private fun authenticatedGet(url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $sessionToken")
            // OkHttp rejects non-ASCII header values; sanitize the device model
            .addHeader("X-Device-Name", deviceNameHeader)
            .get()
            .build()
    }

    private val deviceNameHeader: String by lazy {
        (Build.MODEL ?: "Android Device")
            .filter { it.code in 32..126 }
            .ifBlank { "Android Device" }
    }

    private fun authenticatedPost(url: String, body: String = ""): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * Execute a GET with retries and linear backoff.
     * Throws after [MAX_RETRIES] consecutive failures — partial transfers must
     * fail loudly (preserving the resume session) instead of skipping data.
     */
    private suspend fun getWithRetry(url: String): Response {
        var lastError: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            if (isCancelled) throw CancellationException("Transfer cancelled")
            try {
                val response = httpClient.newCall(authenticatedGet(url)).execute()
                if (response.isSuccessful) return response
                response.close()
                lastError = IOException("HTTP ${response.code} for $url")
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < MAX_RETRIES - 1) delay(RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: IOException("Request failed: $url")
    }

    suspend fun fetchManifest(): TransferManifest = withContext(Dispatchers.IO) {
        // The server answers 202 until the source-device user accepts the connection.
        val deadline = System.currentTimeMillis() + ACCEPT_WAIT_TIMEOUT_MS
        while (true) {
            if (isCancelled) throw CancellationException("Transfer cancelled")
            val response = httpClient.newCall(authenticatedGet("$baseUrl/qs/manifest")).execute()
            when {
                // Check 202 before isSuccessful — OkHttp counts 202 as successful
                response.code == 202 -> {
                    response.close()
                    if (System.currentTimeMillis() > deadline) {
                        throw IOException("Timed out waiting for the other device to accept")
                    }
                    delay(ACCEPT_POLL_INTERVAL_MS)
                }
                response.code == 200 -> {
                    val body = response.body?.string()
                        ?: throw IOException("Empty manifest response")
                    return@withContext Json.decodeFromString<TransferManifest>(body)
                }
                response.code == 403 -> {
                    response.close()
                    throw IOException("Connection rejected by the other device")
                }
                else -> {
                    val code = response.code
                    response.close()
                    throw IOException("Failed to fetch manifest: HTTP $code")
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    suspend fun transferAll(): TransferSummary = withContext(Dispatchers.IO) {
        startTime = System.currentTimeMillis()
        val existingSession = sessionStore?.loadSession()

        try {
            onProgress?.invoke(
                TransferProgress(
                    phase = TransferProgress.Phase.CONNECTING,
                    itemsTransferred = 0, totalItems = 1,
                    bytesTransferred = 0, totalBytes = 0,
                )
            )
            manifest = fetchManifest()
            Timber.i("Received manifest: $manifest")

            val wasResumed = existingSession != null &&
                    existingSession.manifestHash == TransferSessionStore.manifestHash(manifest)

            sessionId = if (wasResumed) existingSession!!.sessionId
            else TransferSessionStore.generateSessionId()

            if (wasResumed) {
                completedBookPages.addAll(existingSession!!.completedBookPages)
                completedChapterPages.addAll(existingSession.completedChapterPages)
                completedBodyPages.addAll(existingSession.completedBodyPages)
                importedImageHashes.addAll(existingSession.importedImageHashes)
                importedPrefs = existingSession.importedPreferences
                transferredBooks = completedBookPages.size * BOOKS_PAGE_SIZE
                transferredChapters = completedChapterPages.size * CHAPTERS_PAGE_SIZE
                transferredImages = importedImageHashes.size
            }

            // 1. Transfer database (books + chapters)
            transferDatabase()

            // 2. Transfer chapter bodies (downloaded/offline content)
            transferChapterBodies()

            // 3. Transfer images
            transferImages()

            // 4. Transfer preferences
            if (manifest.hasPreferences && !importedPrefs) {
                transferPreferences()
                importedPrefs = true
                saveSession(phase = "PREFERENCES")
            }

            if (isCancelled) throw CancellationException("Transfer cancelled")

            // 5. Signal completion
            try {
                httpClient.newCall(authenticatedPost("$baseUrl/qs/complete")).execute().close()
            } catch (e: IOException) {
                Timber.w(e, "Failed to notify sender of completion (data is fully transferred)")
            }

            val duration = System.currentTimeMillis() - startTime
            val summary = TransferSummary(
                sessionId = sessionId,
                manifest = manifest,
                booksTransferred = transferredBooks,
                chaptersTransferred = transferredChapters,
                imagesTransferred = transferredImages,
                preferencesTransferred = importedPrefs,
                durationMs = duration,
                bytesTransferred = 0,
                completedAt = System.currentTimeMillis(),
                wasResumed = wasResumed,
                errors = errors.toList(),
            )

            sessionStore?.clearSession()

            onProgress?.invoke(
                TransferProgress(
                    phase = TransferProgress.Phase.COMPLETED,
                    itemsTransferred = manifest.booksCount + manifest.chaptersCount,
                    totalItems = manifest.booksCount + manifest.chaptersCount,
                    bytesTransferred = 0, totalBytes = manifest.totalImageBytes,
                )
            )

            summary
        } catch (e: Exception) {
            Timber.e(e, "Transfer failed — session preserved for resume")
            onProgress?.invoke(
                TransferProgress(phase = TransferProgress.Phase.ERROR, 0, 0, 0, 0)
            )
            throw e
        }
    }

    private fun saveSession(phase: String) {
        if (!::manifest.isInitialized || !::sessionId.isInitialized) return
        sessionStore?.saveSession(
            TransferSessionStore.SessionState(
                sessionId = sessionId,
                manifestHash = TransferSessionStore.manifestHash(manifest),
                manifest = manifest,
                lastCompletedPhase = phase,
                completedBookPages = completedBookPages.toList(),
                completedChapterPages = completedChapterPages.toList(),
                completedBodyPages = completedBodyPages.toList(),
                importedImageHashes = importedImageHashes.toList(),
                importedPreferences = importedPrefs,
                host = host,
                port = port,
                startedAt = startTime,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun totalPages(count: Int, pageSize: Int): Int =
        (count + pageSize - 1) / pageSize

    private suspend fun transferDatabase() {
        val totalItems = manifest.booksCount + manifest.chaptersCount
        var transferred = transferredBooks + transferredChapters

        val bookPages = totalPages(manifest.booksCount, BOOKS_PAGE_SIZE)
        for (page in 0 until bookPages) {
            if (isCancelled) throw CancellationException("Transfer cancelled")
            if (page in completedBookPages) continue

            val response = getWithRetry("$baseUrl/qs/books/$page/page")
            val body = response.body?.string() ?: throw IOException("Empty book page $page")
            val books = Json.decodeFromString<List<BookDto>>(body)

            importer.importBooks(books.map { it.toBook() })
            completedBookPages.add(page)
            transferredBooks += books.size
            transferred += books.size
            saveSession(phase = "DATABASE")

            onProgress?.invoke(
                TransferProgress(
                    phase = TransferProgress.Phase.TRANSFERRING_DATABASE,
                    itemsTransferred = transferred, totalItems = totalItems,
                    bytesTransferred = 0, totalBytes = 0,
                )
            )
        }

        val chapterPages = totalPages(manifest.chaptersCount, CHAPTERS_PAGE_SIZE)
        for (page in 0 until chapterPages) {
            if (isCancelled) throw CancellationException("Transfer cancelled")
            if (page in completedChapterPages) continue

            val response = getWithRetry("$baseUrl/qs/chapters/$page/page")
            val body = response.body?.string() ?: throw IOException("Empty chapter page $page")
            val chapters = Json.decodeFromString<List<ChapterDto>>(body)

            importer.importChapters(chapters.map { it.toChapter() })
            completedChapterPages.add(page)
            transferredChapters += chapters.size
            transferred += chapters.size
            saveSession(phase = "DATABASE")

            onProgress?.invoke(
                TransferProgress(
                    phase = TransferProgress.Phase.TRANSFERRING_DATABASE,
                    itemsTransferred = transferred, totalItems = totalItems,
                    bytesTransferred = 0, totalBytes = 0,
                )
            )
        }
    }

    private suspend fun transferChapterBodies() {
        val bodyPages = totalPages(manifest.chapterBodiesCount, BODIES_PAGE_SIZE)
        for (page in 0 until bodyPages) {
            if (isCancelled) throw CancellationException("Transfer cancelled")
            if (page in completedBodyPages) continue

            val response = getWithRetry("$baseUrl/qs/chapter-bodies/$page/page")
            val body = response.body?.string() ?: throw IOException("Empty body page $page")
            val bodies = Json.decodeFromString<List<ChapterBodyDto>>(body)

            importer.importChapterBodies(bodies.map { it.toChapterBody() })
            completedBodyPages.add(page)
            transferredBodies += bodies.size
            saveSession(phase = "CHAPTER_BODIES")

            onProgress?.invoke(
                TransferProgress(
                    phase = TransferProgress.Phase.TRANSFERRING_DATABASE,
                    itemsTransferred = transferredBodies,
                    totalItems = manifest.chapterBodiesCount,
                    bytesTransferred = 0, totalBytes = 0,
                )
            )
        }
    }

    private suspend fun transferImages() {
        if (manifest.imagesCount == 0) return

        val manifestResponse = getWithRetry("$baseUrl/qs/images/manifest")
        val manifestJson = manifestResponse.body?.string()
            ?: throw IOException("Empty image manifest")
        val imageManifest = Json.decodeFromString<List<ImageManifestEntry>>(manifestJson)

        var totalBytesTransferred = 0L

        for (entry in imageManifest) {
            if (isCancelled) throw CancellationException("Transfer cancelled")
            if (entry.sha256 in importedImageHashes) continue
            try {
                val response = getWithRetry("$baseUrl/qs/images/${entry.sha256}")
                val imageData = response.body?.bytes()
                    ?: throw IOException("Empty image body for ${entry.relativePath}")
                importer.importImage(entry.relativePath, imageData, entry.sha256)
                importedImageHashes.add(entry.sha256)
                transferredImages++
                totalBytesTransferred += imageData.size

                if (transferredImages % 10 == 0) saveSession(phase = "IMAGES")

                onProgress?.invoke(
                    TransferProgress(
                        phase = TransferProgress.Phase.TRANSFERRING_IMAGES,
                        itemsTransferred = transferredImages,
                        totalItems = imageManifest.size,
                        bytesTransferred = totalBytesTransferred,
                        totalBytes = manifest.totalImageBytes,
                    )
                )
            } catch (e: SecurityException) {
                throw e // malicious path — abort the whole transfer
            } catch (e: Exception) {
                // A single missing/corrupt image should not block migration;
                // record it and surface it in the summary.
                errors.add("Image ${entry.relativePath}: ${e.message}")
                Timber.w(e, "Failed to transfer image: ${entry.relativePath}")
            }
        }
        saveSession(phase = "IMAGES")
    }

    private suspend fun transferPreferences() {
        onProgress?.invoke(
            TransferProgress(
                phase = TransferProgress.Phase.TRANSFERRING_PREFERENCES,
                itemsTransferred = 0, totalItems = 1,
                bytesTransferred = 0, totalBytes = 0,
            )
        )
        val response = getWithRetry("$baseUrl/qs/preferences")
        val json = response.body?.string() ?: throw IOException("Empty preferences response")
        importer.importPreferences(json)
        transferredPreferences = true
    }

    fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
