package my.noveldokusha.tooling.firebase_sync.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.firebase_sync.auth.FirebaseAuthService
import my.noveldokusha.tooling.firebase_sync.data.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseStorage: FirebaseStorage,
    private val authService: FirebaseAuthService,
    private val json: Json
) {
    companion object {
        private const val COLLECTION_USER_LIBRARIES = "user_libraries"
        private const val COLLECTION_USER_CHAPTERS = "user_chapters"
        private const val COLLECTION_USER_CHAPTER_BODIES = "user_chapter_bodies"
        private const val COLLECTION_USER_IMAGES = "user_images"

        private const val FIELD_BOOKS = "books"
        private const val FIELD_CHAPTERS = "chapters"
        private const val FIELD_CHAPTER_BODIES = "chapterBodies"
        private const val FIELD_IMAGES = "images"
        private const val FIELD_LAST_SYNC = "lastSyncTimestamp"

        private const val STORAGE_PATH_IMAGES = "user_images"
        private const val MAX_BATCH_SIZE = 500 // Firestore batch limit
        private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB limit for images
    }

    suspend fun uploadLibrary(userLibrary: UserLibrary): Result<Unit> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val libraryData = mapOf(
                FIELD_BOOKS to json.encodeToString(userLibrary.books),
                FIELD_LAST_SYNC to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_USER_LIBRARIES)
                .document(userId)
                .set(libraryData)
                .await()

            Timber.d("Library uploaded successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload library")
            Result.failure(e)
        }
    }

    suspend fun downloadLibrary(): Result<UserLibrary> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val document = firestore.collection(COLLECTION_USER_LIBRARIES)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                return Result.success(UserLibrary(userId = userId))
            }

            val booksJson = document.getString(FIELD_BOOKS) ?: "{}"
            val lastSync = document.getLong(FIELD_LAST_SYNC) ?: 0L
            val books = json.decodeFromString<Map<String, LibraryBook>>(booksJson)

            val userLibrary = UserLibrary(
                userId = userId,
                books = books,
                lastSyncTimestamp = lastSync
            )

            Timber.d("Library downloaded successfully for user: $userId")
            Result.success(userLibrary)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download library")
            Result.failure(e)
        }
    }

    suspend fun getLastSyncTimestamp(): Result<Long> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val document = firestore.collection(COLLECTION_USER_LIBRARIES)
                .document(userId)
                .get()
                .await()

            val lastSync = document.getLong(FIELD_LAST_SYNC) ?: 0L
            Result.success(lastSync)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get last sync timestamp")
            Result.failure(e)
        }
    }

    // Chapter sync operations
    suspend fun uploadChapters(userChapters: UserChapters): Result<Unit> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val chaptersData = mapOf(
                FIELD_CHAPTERS to json.encodeToString(userChapters.chapters),
                FIELD_LAST_SYNC to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_USER_CHAPTERS)
                .document(userId)
                .set(chaptersData)
                .await()

            Timber.d("Chapters uploaded successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload chapters")
            Result.failure(e)
        }
    }

    suspend fun downloadChapters(): Result<UserChapters> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val document = firestore.collection(COLLECTION_USER_CHAPTERS)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                return Result.success(UserChapters(userId = userId))
            }

            val chaptersJson = document.getString(FIELD_CHAPTERS) ?: "{}"
            val lastSync = document.getLong(FIELD_LAST_SYNC) ?: 0L
            val chapters = json.decodeFromString<Map<String, ChapterSync>>(chaptersJson)

            val userChapters = UserChapters(
                userId = userId,
                chapters = chapters,
                lastSyncTimestamp = lastSync
            )

            Timber.d("Chapters downloaded successfully for user: $userId")
            Result.success(userChapters)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download chapters")
            Result.failure(e)
        }
    }

    // ChapterBody sync operations
    suspend fun uploadChapterBodies(userChapterBodies: UserChapterBodies): Result<Unit> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Split into batches due to potential size limits
            val chapterBodiesList = userChapterBodies.chapterBodies.toList()
            val batches = chapterBodiesList.chunked(MAX_BATCH_SIZE)

            batches.forEachIndexed { batchIndex, batch ->
                val batchData = mapOf(
                    FIELD_CHAPTER_BODIES to json.encodeToString(batch.toMap()),
                    FIELD_LAST_SYNC to System.currentTimeMillis()
                )

                val documentId = if (batchIndex == 0) userId else "${userId}_batch_$batchIndex"
                firestore.collection(COLLECTION_USER_CHAPTER_BODIES)
                    .document(documentId)
                    .set(batchData)
                    .await()
            }

            Timber.d("Chapter bodies uploaded successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload chapter bodies")
            Result.failure(e)
        }
    }

    suspend fun downloadChapterBodies(): Result<UserChapterBodies> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Get all documents for this user (including batches)
            val documents = firestore.collection(COLLECTION_USER_CHAPTER_BODIES)
                .whereGreaterThanOrEqualTo("__name__", userId)
                .whereLessThan("__name__", userId + "\uf8ff")
                .get()
                .await()

            val allChapterBodies = mutableMapOf<String, ChapterBodySync>()
            var lastSync = 0L

            documents.forEach { document ->
                val chapterBodiesJson = document.getString(FIELD_CHAPTER_BODIES) ?: "{}"
                val docLastSync = document.getLong(FIELD_LAST_SYNC) ?: 0L
                lastSync = maxOf(lastSync, docLastSync)

                val chapterBodies = json.decodeFromString<Map<String, ChapterBodySync>>(chapterBodiesJson)
                allChapterBodies.putAll(chapterBodies)
            }

            val userChapterBodies = UserChapterBodies(
                userId = userId,
                chapterBodies = allChapterBodies,
                lastSyncTimestamp = lastSync
            )

            Timber.d("Chapter bodies downloaded successfully for user: $userId")
            Result.success(userChapterBodies)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download chapter bodies")
            Result.failure(e)
        }
    }

    // Image sync operations
    suspend fun uploadImages(userImages: UserImages): Result<Unit> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val imagesData = mapOf(
                FIELD_IMAGES to json.encodeToString(userImages.images),
                FIELD_LAST_SYNC to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_USER_IMAGES)
                .document(userId)
                .set(imagesData)
                .await()

            Timber.d("Image metadata uploaded successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload image metadata")
            Result.failure(e)
        }
    }

    suspend fun downloadImages(): Result<UserImages> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val document = firestore.collection(COLLECTION_USER_IMAGES)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                return Result.success(UserImages(userId = userId))
            }

            val imagesJson = document.getString(FIELD_IMAGES) ?: "{}"
            val lastSync = document.getLong(FIELD_LAST_SYNC) ?: 0L
            val images = json.decodeFromString<Map<String, ImageSync>>(imagesJson)

            val userImages = UserImages(
                userId = userId,
                images = images,
                lastSyncTimestamp = lastSync
            )

            Timber.d("Image metadata downloaded successfully for user: $userId")
            Result.success(userImages)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download image metadata")
            Result.failure(e)
        }
    }

    suspend fun uploadImageFile(imagePath: String, imageData: ByteArray): Result<String> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            if (imageData.size > MAX_IMAGE_SIZE) {
                return Result.failure(Exception("Image too large: ${imageData.size} bytes"))
            }

            val fileName = imagePath.substringAfterLast('/')
            val storageRef = firebaseStorage.reference
                .child(STORAGE_PATH_IMAGES)
                .child(userId)
                .child(fileName)

            val uploadTask = storageRef.putBytes(imageData).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

            Timber.d("Image uploaded successfully: $imagePath")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload image: $imagePath")
            Result.failure(e)
        }
    }

    suspend fun downloadImageFile(downloadUrl: String): Result<ByteArray> {
        return try {
            val storageRef = firebaseStorage.getReferenceFromUrl(downloadUrl)
            val bytes = storageRef.getBytes(MAX_IMAGE_SIZE.toLong()).await()

            Timber.d("Image downloaded successfully from: $downloadUrl")
            Result.success(bytes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download image from: $downloadUrl")
            Result.failure(e)
        }
    }

    suspend fun uploadCompleteSyncData(syncData: CompleteSyncData): Result<Unit> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Upload each component
            uploadLibrary(syncData.library)
            uploadChapters(syncData.chapters)
            uploadChapterBodies(syncData.chapterBodies)
            uploadImages(syncData.images)

            Timber.d("Complete sync data uploaded successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload complete sync data")
            Result.failure(e)
        }
    }

    suspend fun downloadCompleteSyncData(): Result<CompleteSyncData> {
        return try {
            val userId = authService.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val library = downloadLibrary().getOrThrow()
            val chapters = downloadChapters().getOrThrow()
            val chapterBodies = downloadChapterBodies().getOrThrow()
            val images = downloadImages().getOrThrow()

            val syncData = CompleteSyncData(
                library = library,
                chapters = chapters,
                chapterBodies = chapterBodies,
                images = images,
                lastCompleteSyncTimestamp = System.currentTimeMillis()
            )

            Timber.d("Complete sync data downloaded successfully for user: $userId")
            Result.success(syncData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download complete sync data")
            Result.failure(e)
        }
    }
}
