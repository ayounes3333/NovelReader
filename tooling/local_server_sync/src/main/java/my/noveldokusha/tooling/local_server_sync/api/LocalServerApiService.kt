package my.noveldokusha.tooling.local_server_sync.api

import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.data.*
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalServerApiService @Inject constructor(
    private val tokenStorage: AuthTokenStorage
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { message ->
            Timber.tag("LocalServerAPI").d(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private fun getBaseUrl(): String {
        return tokenStorage.getServerUrl() ?: "http://10.0.2.2:8080"
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        return makeRequest(
            endpoint = "/api/register",
            method = "POST",
            body = json.encodeToString(RegisterRequest.serializer(), request)
        )
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        return makeRequest(
            endpoint = "/api/login",
            method = "POST",
            body = json.encodeToString(LoginRequest.serializer(), request)
        )
    }

    suspend fun syncLibrary(library: UserLibrary, authToken: String): SyncResponse {
        return makeAuthenticatedRequest(
            endpoint = "/api/sync/library",
            method = "POST",
            body = json.encodeToString(UserLibrary.serializer(), library),
            authToken = authToken
        )
    }

    suspend fun getLibrary(authToken: String): SyncResponse {
        return makeAuthenticatedRequest(
            endpoint = "/api/sync/library",
            method = "GET",
            authToken = authToken
        )
    }

    suspend fun completeSync(syncRequest: SyncRequest, authToken: String): SyncResponse {
        return makeAuthenticatedRequest(
            endpoint = "/api/sync/complete",
            method = "POST",
            body = json.encodeToString(SyncRequest.serializer(), syncRequest),
            authToken = authToken
        )
    }

    suspend fun getUserInfo(authToken: String): UserInfo {
        return makeAuthenticatedRequest(
            endpoint = "/api/user/info",
            method = "GET",
            authToken = authToken
        )
    }

    suspend fun uploadImages(images: List<ImageBackupItem>, authToken: String): ImageSyncResponse {
        return makeAuthenticatedRequest(
            endpoint = "/api/sync/images/upload",
            method = "POST",
            body = json.encodeToString(ImageSyncRequest.serializer(), ImageSyncRequest(images)),
            authToken = authToken
        )
    }

    suspend fun downloadImages(authToken: String): ImageSyncResponse {
        return makeAuthenticatedRequest(
            endpoint = "/api/sync/images/download",
            method = "GET",
            authToken = authToken
        )
    }

    private suspend fun makeRequest(
        endpoint: String,
        method: String,
        body: String? = null
    ): AuthResponse = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("${getBaseUrl()}$endpoint")
                .addHeader("Content-Type", "application/json")

            when (method) {
                "POST" -> {
                    val requestBody = (body ?: "").toRequestBody("application/json".toMediaType())
                    requestBuilder.post(requestBody)
                }
                "GET" -> requestBuilder.get()
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                json.decodeFromString(AuthResponse.serializer(), responseBody)
            } else {
                AuthResponse(
                    success = false,
                    message = "HTTP ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "API request failed for $endpoint")
            AuthResponse(
                success = false,
                message = "Network error: ${e.message}"
            )
        }
    }

    private suspend inline fun <reified T> makeAuthenticatedRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        authToken: String
    ): T = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("${getBaseUrl()}$endpoint")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $authToken")

            when (method) {
                "POST" -> {
                    val requestBody = (body ?: "").toRequestBody("application/json".toMediaType())
                    requestBuilder.post(requestBody)
                }
                "GET" -> requestBuilder.get()
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                json.decodeFromString<T>(responseBody)
            } else {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Authenticated API request failed for $endpoint")
            throw e
        }
    }
}
