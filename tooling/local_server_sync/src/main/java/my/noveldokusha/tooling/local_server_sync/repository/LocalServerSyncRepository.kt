package my.noveldokusha.tooling.local_server_sync.repository

import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.api.LocalServerApiService
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.data.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalServerSyncRepository @Inject constructor(
    private val apiService: LocalServerApiService,
    private val authService: LocalServerAuthService,
    private val json: Json
) {

    suspend fun uploadLibrary(userLibrary: UserLibrary): Result<Unit> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User ID not available"))

            val libraryWithUserId = userLibrary.copy(
                userId = userId,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val response = apiService.syncLibrary(libraryWithUserId, authToken)

            if (response.success) {
                Timber.d("Library uploaded successfully")
                Result.success(Unit)
            } else {
                Timber.e("Failed to upload library: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading library")
            Result.failure(e)
        }
    }

    suspend fun downloadLibrary(): Result<UserLibrary> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val response = apiService.getLibrary(authToken)

            if (response.success && response.library != null) {
                Timber.d("Library downloaded successfully")
                Result.success(response.library)
            } else {
                val message = response.message.ifEmpty { "Failed to download library" }
                Timber.e("Failed to download library: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading library")
            Result.failure(e)
        }
    }

    suspend fun uploadCompleteSync(
        userLibrary: UserLibrary?,
        chapters: Map<String, List<BookChapter>>?,
        chapterBodies: Map<String, ChapterBody>?
    ): Result<SyncResponse> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User ID not available"))

            val syncRequest = SyncRequest(
                library = userLibrary?.copy(
                    userId = userId,
                    lastSyncTimestamp = System.currentTimeMillis()
                ),
                chapters = chapters,
                chapterBodies = chapterBodies,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val response = apiService.completeSync(syncRequest, authToken)

            if (response.success) {
                Timber.d("Complete sync uploaded successfully")
                Result.success(response)
            } else {
                Timber.e("Failed to upload complete sync: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading complete sync")
            Result.failure(e)
        }
    }

    suspend fun downloadCompleteSync(): Result<SyncResponse> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            // For now, we'll use the library endpoint and construct a complete response
            // In the future, we could add a dedicated complete sync download endpoint
            val libraryResponse = apiService.getLibrary(authToken)

            if (libraryResponse.success) {
                Timber.d("Complete sync downloaded successfully")
                Result.success(libraryResponse)
            } else {
                val message = libraryResponse.message.ifEmpty { "Failed to download complete sync" }
                Timber.e("Failed to download complete sync: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading complete sync")
            Result.failure(e)
        }
    }

    suspend fun uploadChapters(bookUrl: String, chapters: List<BookChapter>): Result<Unit> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val chaptersMap = mapOf(bookUrl to chapters)
            val syncRequest = SyncRequest(
                chapters = chaptersMap,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val response = apiService.completeSync(syncRequest, authToken)

            if (response.success) {
                Timber.d("Chapters uploaded successfully for book: $bookUrl")
                Result.success(Unit)
            } else {
                Timber.e("Failed to upload chapters: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading chapters")
            Result.failure(e)
        }
    }

    suspend fun uploadChapterBodies(chapterBodies: Map<String, ChapterBody>): Result<Unit> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val syncRequest = SyncRequest(
                chapterBodies = chapterBodies,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val response = apiService.completeSync(syncRequest, authToken)

            if (response.success) {
                Timber.d("Chapter bodies uploaded successfully")
                Result.success(Unit)
            } else {
                Timber.e("Failed to upload chapter bodies: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading chapter bodies")
            Result.failure(e)
        }
    }

    suspend fun downloadChapters(bookUrl: String): Result<List<BookChapter>> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val response = apiService.getLibrary(authToken)

            if (response.success && response.chapters != null) {
                val chapters = response.chapters[bookUrl] ?: emptyList()
                Timber.d("Chapters downloaded successfully for book: $bookUrl")
                Result.success(chapters)
            } else {
                val message = response.message.ifEmpty { "Failed to download chapters" }
                Timber.e("Failed to download chapters: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading chapters")
            Result.failure(e)
        }
    }

    suspend fun downloadChapterBody(chapterUrl: String): Result<ChapterBody?> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val response = apiService.getLibrary(authToken)

            if (response.success && response.chapterBodies != null) {
                val chapterBody = response.chapterBodies[chapterUrl]
                Timber.d("Chapter body downloaded successfully for: $chapterUrl")
                Result.success(chapterBody)
            } else {
                val message = response.message.ifEmpty { "Failed to download chapter body" }
                Timber.e("Failed to download chapter body: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading chapter body")
            Result.failure(e)
        }
    }

    suspend fun getUserInfo(): Result<UserInfo> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val userInfo = apiService.getUserInfo(authToken)
            Timber.d("User info retrieved successfully")
            Result.success(userInfo)
        } catch (e: Exception) {
            Timber.e(e, "Error getting user info")
            Result.failure(e)
        }
    }

    suspend fun uploadImages(images: List<ImageBackupItem>): Result<ImageSyncResponse> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val response = apiService.uploadImages(images, authToken)

            if (response.success) {
                Timber.d("Images uploaded successfully: ${response.uploadedCount}/${images.size}")
                Result.success(response)
            } else {
                Timber.e("Failed to upload images: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading images")
            Result.failure(e)
        }
    }

    suspend fun downloadImages(): Result<List<ImageBackupItem>> {
        return try {
            val authToken = authService.getAuthToken()
                ?: return Result.failure(Exception("User not authenticated"))

            val response = apiService.downloadImages(authToken)

            if (response.success && response.images != null) {
                Timber.d("Images downloaded successfully: ${response.images.size}")
                Result.success(response.images)
            } else {
                val message = response.message.ifEmpty { "Failed to download images" }
                Timber.e("Failed to download images: $message")
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error downloading images")
            Result.failure(e)
        }
    }
}
