package my.noveldokusha.tooling.local_server_sync.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.data.AuthResponse
import my.noveldokusha.tooling.local_server_sync.data.ChapterBodyCheckRequest
import my.noveldokusha.tooling.local_server_sync.data.ChapterBodyCheckResponse
import my.noveldokusha.tooling.local_server_sync.data.ChapterBodyManifestResponse
import my.noveldokusha.tooling.local_server_sync.data.ChapterChangesRequest
import my.noveldokusha.tooling.local_server_sync.data.ChapterChangesResponse
import my.noveldokusha.tooling.local_server_sync.data.ChapterPullResponse
import my.noveldokusha.tooling.local_server_sync.data.ImageManifestResponse
import my.noveldokusha.tooling.local_server_sync.data.ImageReferencesRequest
import my.noveldokusha.tooling.local_server_sync.data.ImageReferencesResponse
import my.noveldokusha.tooling.local_server_sync.data.LibraryChangesRequest
import my.noveldokusha.tooling.local_server_sync.data.LibraryChangesResponse
import my.noveldokusha.tooling.local_server_sync.data.LibraryPullResponse
import my.noveldokusha.tooling.local_server_sync.data.LoginRequest
import my.noveldokusha.tooling.local_server_sync.data.RefreshTokenRequest
import my.noveldokusha.tooling.local_server_sync.data.RefreshTokenResponse
import my.noveldokusha.tooling.local_server_sync.data.RegisterRequest
import my.noveldokusha.tooling.local_server_sync.data.SimpleSuccessResponse
import my.noveldokusha.tooling.local_server_sync.data.SyncStateResponse
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the NovelReader-Backend Phase-1 API.
 *
 *  - JSON endpoints use kotlinx-serialization.
 *  - Image blobs are streamed as raw binary (no base64).
 *  - Chapter bodies are gzipped both ways.
 *  - On HTTP 401 the access token is refreshed once and the call retried.
 */
@Singleton
class LocalServerApiService @Inject constructor(
    private val tokenStorage: AuthTokenStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    /** Image blobs / chapter bodies can be large — generous timeouts. */
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor { msg -> Timber.tag("LocalServerAPI").d(msg) }
            .apply { level = HttpLoggingInterceptor.Level.HEADERS })
        .build()

    private val refreshMutex = Mutex()

    private fun baseUrl(): String =
        tokenStorage.getServerUrl()?.trimEnd('/') ?: "http://10.0.2.2:8080"

    private fun urlOf(path: String): String = baseUrl() + path

    // ── Public: ping ──────────────────────────────────────────────────────

    suspend fun pingServer(serverUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = (serverUrl ?: baseUrl()).trimEnd('/')
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url("$url/api/ping").get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    // ── Public: auth ──────────────────────────────────────────────────────

    suspend fun register(req: RegisterRequest): AuthResponse =
        postJsonUnauthenticated("/api/register", json.encodeToString(RegisterRequest.serializer(), req))

    suspend fun login(req: LoginRequest): AuthResponse =
        postJsonUnauthenticated("/api/login", json.encodeToString(LoginRequest.serializer(), req))

    suspend fun refresh(refreshToken: String): RefreshTokenResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(refreshToken))
        val request = Request.Builder()
            .url(urlOf("/api/auth/refresh"))
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Timber.w("refresh failed: ${response.code} $raw")
                return@withContext RefreshTokenResponse(success = false, message = "HTTP ${response.code}")
            }
            try {
                json.decodeFromString(RefreshTokenResponse.serializer(), raw)
            } catch (e: Exception) {
                Timber.e(e, "refresh: bad json")
                RefreshTokenResponse(success = false, message = e.message ?: "bad json")
            }
        }
    }

    suspend fun logout(refreshToken: String): SimpleSuccessResponse =
        sendAuthenticatedJson(
            path = "/api/auth/logout",
            method = "POST",
            body = json.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(refreshToken)),
            deserialize = { json.decodeFromString(SimpleSuccessResponse.serializer(), it) }
        )

    // ── Public: sync state ────────────────────────────────────────────────

    suspend fun getSyncState(): SyncStateResponse = sendAuthenticatedJson(
        path = "/api/sync/state",
        method = "GET",
        body = null,
        deserialize = { json.decodeFromString(SyncStateResponse.serializer(), it) }
    )

    // ── Public: library ──────────────────────────────────────────────────

    suspend fun pushLibraryChanges(req: LibraryChangesRequest): LibraryChangesResponse =
        sendAuthenticatedJson(
            path = "/api/sync/library/changes",
            method = "POST",
            body = json.encodeToString(LibraryChangesRequest.serializer(), req),
            deserialize = { json.decodeFromString(LibraryChangesResponse.serializer(), it) }
        )

    suspend fun pullLibraryChanges(since: Long, limit: Int = 500): LibraryPullResponse =
        sendAuthenticatedJson(
            path = "/api/sync/library/changes?since=$since&limit=$limit",
            method = "GET",
            body = null,
            deserialize = { json.decodeFromString(LibraryPullResponse.serializer(), it) }
        )

    // ── Public: chapters ─────────────────────────────────────────────────

    suspend fun pushChapterChanges(req: ChapterChangesRequest): ChapterChangesResponse =
        sendAuthenticatedJson(
            path = "/api/sync/chapters/changes",
            method = "POST",
            body = json.encodeToString(ChapterChangesRequest.serializer(), req),
            deserialize = { json.decodeFromString(ChapterChangesResponse.serializer(), it) }
        )

    suspend fun pullChapterChanges(since: Long, limit: Int = 1000): ChapterPullResponse =
        sendAuthenticatedJson(
            path = "/api/sync/chapters/changes?since=$since&limit=$limit",
            method = "GET",
            body = null,
            deserialize = { json.decodeFromString(ChapterPullResponse.serializer(), it) }
        )

    // ── Public: chapter body batch SHA-256 check ─────────────────────────

    suspend fun checkChapterBodies(req: ChapterBodyCheckRequest): ChapterBodyCheckResponse =
        sendAuthenticatedJson(
            path = "/api/sync/chapter-bodies/check",
            method = "POST",
            body = json.encodeToString(ChapterBodyCheckRequest.serializer(), req),
            deserialize = { json.decodeFromString(ChapterBodyCheckResponse.serializer(), it) }
        )

    // ── Public: chapter bodies (gzip) ────────────────────────────────────

    suspend fun pullChapterBodyManifest(since: Long, limit: Int = 500): ChapterBodyManifestResponse =
        sendAuthenticatedJson(
            path = "/api/sync/chapter-bodies/manifest?since=$since&limit=$limit",
            method = "GET",
            body = null,
            deserialize = { json.decodeFromString(ChapterBodyManifestResponse.serializer(), it) }
        )

    /**
     * Uploads a single chapter body as gzipped UTF-8 text.
     * Returns `true` on success.
     */
    suspend fun putChapterBody(chapterUrl: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val gz = ByteArrayOutputStream(body.length / 2)
        GZIPOutputStream(gz).use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val encoded = URLEncoder.encode(chapterUrl, "UTF-8")
        val request = authedRequestBuilder("/api/sync/chapter-bodies?chapterUrl=$encoded")
            .put(gz.toByteArray().toRequestBody("text/plain".toMediaType()))
            .addHeader("Content-Encoding", "gzip")
            .build()
        executeWithRefresh(request).use { it.isSuccessful }
    }

    /**
     * Downloads a single chapter body. Returns the UTF-8 text and the SHA-256
     * the server reports in the `X-Body-Sha256` header. Returns `null` if 404.
     */
    suspend fun getChapterBody(chapterUrl: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(chapterUrl, "UTF-8")
        val request = authedRequestBuilder("/api/sync/chapter-bodies?chapterUrl=$encoded")
            .get()
            // Let OkHttp transparently decompress gzip responses
            .addHeader("Accept-Encoding", "gzip")
            .build()
        executeWithRefresh(request).use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw HttpStatusException(response.code, response.message)
            val text = response.body?.string().orEmpty()
            val sha = response.header("X-Body-Sha256").orEmpty()
            text to sha
        }
    }

    suspend fun deleteChapterBody(chapterUrl: String): Boolean = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(chapterUrl, "UTF-8")
        val request = authedRequestBuilder("/api/sync/chapter-bodies?chapterUrl=$encoded")
            .delete()
            .build()
        executeWithRefresh(request).use { it.isSuccessful }
    }

    // ── Public: images (manifest + binary) ───────────────────────────────

    suspend fun pullImageManifest(since: Long, limit: Int = 500): ImageManifestResponse =
        sendAuthenticatedJson(
            path = "/api/sync/images/manifest?since=$since&limit=$limit",
            method = "GET",
            body = null,
            deserialize = { json.decodeFromString(ImageManifestResponse.serializer(), it) }
        )

    suspend fun postImageReferences(req: ImageReferencesRequest): ImageReferencesResponse =
        sendAuthenticatedJson(
            path = "/api/sync/images/references",
            method = "POST",
            body = json.encodeToString(ImageReferencesRequest.serializer(), req),
            deserialize = { json.decodeFromString(ImageReferencesResponse.serializer(), it) }
        )

    suspend fun headImageBlob(sha256: String): Boolean = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/api/sync/images/${sha256.lowercase()}")
            .head()
            .build()
        executeWithRefresh(request).use { it.isSuccessful }
    }

    /**
     * Uploads a raw image blob. The server verifies the SHA-256 of the body
     * against the URL path.
     */
    suspend fun putImageBlob(sha256: String, mimeType: String, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val type = mimeType.ifBlank { "application/octet-stream" }.toMediaType()
            val request = authedRequestBuilder("/api/sync/images/${sha256.lowercase()}")
                .put(bytes.toRequestBody(type))
                .build()
            executeWithRefresh(request).use { it.isSuccessful }
        }

    /** Downloads a raw image blob, or returns null on 404. */
    suspend fun getImageBlob(sha256: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = authedRequestBuilder("/api/sync/images/${sha256.lowercase()}")
            .get()
            .build()
        executeWithRefresh(request).use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw HttpStatusException(response.code, response.message)
            response.body?.bytes()
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun authedRequestBuilder(path: String): Request.Builder {
        val token = tokenStorage.getAccessToken()
        val builder = Request.Builder().url(urlOf(path))
        if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
        return builder
    }

    private suspend fun postJsonUnauthenticated(path: String, body: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(urlOf(path))
                .post(body.toRequestBody(jsonMediaType))
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Timber.w("$path failed: ${response.code} $raw")
                        // Best-effort: parse what the server returned (it usually
                        // includes success=false, message=...).
                        return@withContext runCatching {
                            json.decodeFromString(AuthResponse.serializer(), raw)
                        }.getOrElse {
                            AuthResponse(success = false, message = "HTTP ${response.code}: ${response.message}")
                        }
                    }
                    json.decodeFromString(AuthResponse.serializer(), raw)
                }
            } catch (e: Exception) {
                Timber.e(e, "$path failed (network)")
                AuthResponse(success = false, message = "Network error: ${e.message}")
            }
        }

    /** JSON request/response helper with auto refresh on 401. */
    private suspend fun <T> sendAuthenticatedJson(
        path: String,
        method: String,
        body: String?,
        deserialize: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val builder = authedRequestBuilder(path)
        val rb: RequestBody? = body?.toRequestBody(jsonMediaType)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "DELETE" -> if (rb != null) builder.delete(rb) else builder.delete()
            "POST" -> builder.post(rb ?: ByteArray(0).toRequestBody(jsonMediaType))
            "PUT" -> builder.put(rb ?: ByteArray(0).toRequestBody(jsonMediaType))
            else -> error("Unsupported method $method")
        }
        executeWithRefresh(builder.build()).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "$path -> HTTP ${response.code}: $raw")
            }
            deserialize(raw)
        }
    }

    /**
     * Executes [request]; if the server responds 401, tries to refresh the
     * access token and replays the request once with the new token.
     */
    private suspend fun executeWithRefresh(request: Request): Response {
        val first = httpClient.newCall(request).execute()
        if (first.code != 401) return first
        first.close()

        val refreshed = tryRefreshAccessToken()
        if (!refreshed) {
            // Build a synthetic 401 response so callers see the same status.
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("".toRequestBody("text/plain".toMediaType()).let {
                    okhttp3.ResponseBody.create("text/plain".toMediaType(), "")
                })
                .build()
        }

        val newToken = tokenStorage.getAccessToken().orEmpty()
        val retried = request.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $newToken")
            .build()
        return httpClient.newCall(retried).execute()
    }

    private suspend fun tryRefreshAccessToken(): Boolean = refreshMutex.withLock {
        // Another caller may have refreshed already.
        if (tokenStorage.isAccessTokenValid()) return@withLock true

        val refreshToken = tokenStorage.getRefreshToken() ?: return@withLock false
        val response = refresh(refreshToken)
        val newAccess = response.accessToken
        if (!response.success || newAccess.isNullOrBlank()) {
            Timber.w("Refresh token rejected; clearing local tokens")
            tokenStorage.clearTokens()
            return@withLock false
        }
        tokenStorage.saveTokens(
            accessToken = newAccess,
            accessExpiresAt = response.accessExpiresAt,
            refreshToken = response.refreshToken,
            refreshExpiresAt = response.refreshExpiresAt
        )
        true
    }

    class HttpStatusException(val statusCode: Int, message: String) : RuntimeException(message)

    // Build URL helper (for callers that need to construct a full URL)
    fun absoluteUrl(path: String): String? = urlOf(path).toHttpUrlOrNull()?.toString()
}

/** Java-side accessor for the helper. */
private fun absoluteUrl(base: String, path: String) = "$base$path"
