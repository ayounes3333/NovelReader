package my.noveldokusha.tooling.quick_setup.server

import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupExporter
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class QuickSetupServer(
    port: Int,
    private val exporter: QuickSetupExporter,
    private val sessionToken: String,
    private val onClientConnected: (() -> Unit)? = null,
    private val onTransferProgress: ((String) -> Unit)? = null,
    private val onCancelRequested: (() -> Unit)? = null,
    private val onIdleTimeout: (() -> Unit)? = null,
) : NanoHTTPD(port) {

    companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        const val TRANSFER_IDLE_TIMEOUT_MS = 10 * 60 * 1000L
    }

    @Volatile
    var isTransferActive = false
        private set

    @Volatile
    var isCancelled = false
        private set

    private val lastRequestTime = AtomicLong(System.currentTimeMillis())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var idleCheckFuture: ScheduledFuture<*>? = null

    // Cache for sha256 -> relativePath lookups
    @Volatile
    private var imageSha256Cache: Map<String, String>? = null

    fun startWithIdleTimeout() {
        start()
        startIdleTimeoutCheck()
    }

    private fun startIdleTimeoutCheck() {
        idleCheckFuture = scheduler.scheduleAtFixedRate({
            val elapsed = System.currentTimeMillis() - lastRequestTime.get()
            val timeout = if (isTransferActive) TRANSFER_IDLE_TIMEOUT_MS else IDLE_TIMEOUT_MS
            if (elapsed > timeout) {
                Timber.w("Quick Setup server idle timeout (${elapsed}ms), shutting down")
                onIdleTimeout?.invoke()
                stop()
            }
        }, 30, 30, TimeUnit.SECONDS)
    }

    override fun serve(session: IHTTPSession): Response {
        lastRequestTime.set(System.currentTimeMillis())

        val uri = session.uri
        val method = session.method

        val authHeader = session.headers["authorization"] ?: ""
        if (!constantTimeEquals(authHeader, "Bearer $sessionToken")) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "Unauthorized"
            )
        }

        if (isCancelled) {
            return newFixedLengthResponse(
                Response.Status.CONFLICT,
                MIME_PLAINTEXT,
                "Transfer cancelled"
            )
        }

        // Consent gate: no data is served until the source-device user accepts.
        if (uri != "/qs/health" && uri != "/qs/cancel") {
            if (QuickSetupAcceptGate.isRejected) {
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN,
                    MIME_PLAINTEXT,
                    "Connection rejected by user"
                )
            }
            if (!QuickSetupAcceptGate.isAccepted) {
                if (uri == "/qs/manifest") {
                    val clientName = session.headers["x-device-name"] ?: "Unknown device"
                    QuickSetupAcceptGate.requestAccept(clientName)
                    onClientConnected?.invoke()
                }
                return newFixedLengthResponse(
                    Response.Status.ACCEPTED,
                    MIME_PLAINTEXT,
                    "Waiting for user acceptance"
                )
            }
        }

        return try {
            when {
                uri == "/qs/manifest" && method == Method.GET -> handleManifest()
                uri.startsWith("/qs/books/") && uri.endsWith("/page") && method == Method.GET ->
                    handleBookPage(uri)
                uri.startsWith("/qs/chapters/") && uri.endsWith("/page") && method == Method.GET ->
                    handleChapterPage(uri)
                uri.startsWith("/qs/chapter-bodies/") && uri.endsWith("/page") && method == Method.GET ->
                    handleChapterBodyPage(uri)
                uri == "/qs/images/manifest" && method == Method.GET -> handleImageManifest()
                uri.startsWith("/qs/images/") && method == Method.GET -> handleImageBlob(uri)
                uri == "/qs/preferences" && method == Method.GET -> handlePreferences()
                uri == "/qs/complete" && method == Method.POST -> handleComplete()
                uri == "/qs/cancel" && method == Method.POST -> handleCancel()
                uri == "/qs/health" && method == Method.GET -> handleHealth()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not found: $uri"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling request: $uri")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal error: ${e.message}"
            )
        }
    }

    private fun handleManifest(): Response {
        isTransferActive = true
        val manifest = kotlinx.coroutines.runBlocking { exporter.createManifest() }
        val json = Json.encodeToString(manifest)
        onTransferProgress?.invoke("Sending manifest")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json
        )
    }

    private fun handleBookPage(uri: String): Response {
        val page = extractPageParam(uri)
        val books = kotlinx.coroutines.runBlocking { exporter.getBooksPage(page) }
        val json = Json.encodeToString(books)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleChapterPage(uri: String): Response {
        val page = extractPageParam(uri)
        val chapters = kotlinx.coroutines.runBlocking { exporter.getChaptersPage(page) }
        val json = Json.encodeToString(chapters)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleChapterBodyPage(uri: String): Response {
        val page = extractPageParam(uri)
        val bodies = kotlinx.coroutines.runBlocking { exporter.getChapterBodiesPage(page) }
        val json = Json.encodeToString(bodies)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleImageManifest(): Response {
        val manifest = kotlinx.coroutines.runBlocking { exporter.getImageManifest() }
        // Cache sha256 -> relativePath for fast lookups
        imageSha256Cache = manifest.associate { it.sha256 to it.relativePath }
        val json = Json.encodeToString(manifest)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleImageBlob(uri: String): Response {
        val sha256 = uri.removePrefix("/qs/images/")

        // Try cache first, then fall back to manifest scan
        val relativePath = imageSha256Cache?.get(sha256)
            ?: run {
                val manifest = kotlinx.coroutines.runBlocking { exporter.getImageManifest() }
                imageSha256Cache = manifest.associate { it.sha256 to it.relativePath }
                manifest.firstOrNull { it.sha256 == sha256 }?.relativePath
            }

        if (relativePath == null) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Image not found"
            )
        }

        val imageData = kotlinx.coroutines.runBlocking { exporter.getImageBlob(relativePath) }
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Image data not found"
            )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/octet-stream",
            ByteArrayInputStream(imageData),
            imageData.size.toLong()
        )
    }

    private fun handlePreferences(): Response {
        val json = kotlinx.coroutines.runBlocking { exporter.getPreferencesJson() }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleComplete(): Response {
        isTransferActive = false
        onTransferProgress?.invoke("Transfer complete")
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
    }

    private fun handleCancel(): Response {
        isCancelled = true
        isTransferActive = false
        onCancelRequested?.invoke()
        onTransferProgress?.invoke("Transfer cancelled by client")
        Timber.i("Transfer cancelled by client request")
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Cancelled")
    }

    private fun handleHealth(): Response {
        val health = mapOf(
            "status" to if (isCancelled) "cancelled" else if (isTransferActive) "active" else "idle",
        )
        return newFixedLengthResponse(Response.Status.OK, "application/json", Json.encodeToString(health))
    }

    // Routes are of the form /qs/{category}/{page}/page
    private fun extractPageParam(uri: String): Int {
        return uri.trim('/').split('/').getOrNull(2)?.toIntOrNull() ?: 0
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return java.security.MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8),
        )
    }

    override fun stop() {
        idleCheckFuture?.cancel(false)
        scheduler.shutdownNow()
        super.stop()
    }
}
