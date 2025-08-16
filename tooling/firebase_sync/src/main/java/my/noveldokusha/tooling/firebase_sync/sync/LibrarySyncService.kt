package my.noveldokusha.tooling.firebase_sync.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.noveldokusha.tooling.firebase_sync.auth.FirebaseAuthService
import my.noveldokusha.tooling.firebase_sync.data.LibraryBook
import my.noveldokusha.tooling.firebase_sync.data.UserLibrary
import my.noveldokusha.tooling.firebase_sync.repository.FirebaseSyncRepository
import my.noveldokusha.tooling.firebase_sync.repository.LocalLibraryRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncService @Inject constructor(
    private val syncRepository: FirebaseSyncRepository,
    private val authService: FirebaseAuthService,
    private val localLibraryRepository: LocalLibraryRepository
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: Flow<SyncState> = _syncState.asStateFlow()

    suspend fun performInitialSync(): Result<Unit> {
        if (!authService.isSignedIn()) {
            return Result.failure(Exception("User not authenticated"))
        }

        _syncState.value = SyncState.Syncing

        return try {
            // Download cloud library
            val cloudLibraryResult = syncRepository.downloadLibrary()
            if (cloudLibraryResult.isFailure) {
                _syncState.value = SyncState.Error(cloudLibraryResult.exceptionOrNull()?.message ?: "Download failed")
                return cloudLibraryResult.map { }
            }

            val cloudLibrary = cloudLibraryResult.getOrThrow()

            // Get local library
            val localLibrary = getLocalLibrary()

            // Merge libraries (cloud takes precedence for initial sync)
            val mergedLibrary = mergeLibraries(localLibrary, cloudLibrary, preferCloud = true)

            // Update local database
            updateLocalDatabase(mergedLibrary)

            // Upload merged library to cloud
            val uploadResult = syncRepository.uploadLibrary(mergedLibrary)
            if (uploadResult.isFailure) {
                _syncState.value = SyncState.Error(uploadResult.exceptionOrNull()?.message ?: "Upload failed")
                return uploadResult
            }

            _syncState.value = SyncState.Success
            Timber.d("Initial sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            Timber.e(e, "Initial sync failed")
            Result.failure(e)
        }
    }

    suspend fun performIncrementalSync(): Result<Unit> {
        if (!authService.isSignedIn()) {
            return Result.failure(Exception("User not authenticated"))
        }

        _syncState.value = SyncState.Syncing

        return try {
            // Get last sync timestamp
            val lastSyncResult = syncRepository.getLastSyncTimestamp()
            if (lastSyncResult.isFailure) {
                _syncState.value = SyncState.Error("Failed to get last sync timestamp")
                return lastSyncResult.map { }
            }

            val lastSyncTimestamp = lastSyncResult.getOrThrow()

            // Get local changes since last sync
            val localLibrary = getLocalLibrary()
            val localChanges = getLocalChangesSince(lastSyncTimestamp)

            if (localChanges.isEmpty()) {
                // No local changes, just download cloud updates
                val cloudLibraryResult = syncRepository.downloadLibrary()
                if (cloudLibraryResult.isSuccess) {
                    val cloudLibrary = cloudLibraryResult.getOrThrow()
                    if (cloudLibrary.lastSyncTimestamp > lastSyncTimestamp) {
                        updateLocalDatabase(cloudLibrary)
                    }
                }
            } else {
                // Merge and upload changes
                val cloudLibraryResult = syncRepository.downloadLibrary()
                val cloudLibrary = cloudLibraryResult.getOrElse {
                    UserLibrary(userId = authService.getCurrentUserId() ?: "")
                }

                val mergedLibrary = mergeLibraries(localLibrary, cloudLibrary, preferCloud = false)
                updateLocalDatabase(mergedLibrary)
                syncRepository.uploadLibrary(mergedLibrary)
            }

            _syncState.value = SyncState.Success
            Timber.d("Incremental sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            Timber.e(e, "Incremental sync failed")
            Result.failure(e)
        }
    }

    private suspend fun getLocalLibrary(): UserLibrary {
        val userId = authService.getCurrentUserId() ?: ""
        return localLibraryRepository.getUserLibrary(userId)
    }

    private suspend fun getLocalChangesSince(timestamp: Long): List<LibraryBook> {
        return localLibraryRepository.getBooksSince(timestamp)
    }

    private fun mergeLibraries(
        local: UserLibrary,
        cloud: UserLibrary,
        preferCloud: Boolean
    ): UserLibrary {
        val mergedBooks = mutableMapOf<String, LibraryBook>()

        // Add all local books
        mergedBooks.putAll(local.books)

        // Merge cloud books
        cloud.books.forEach { (url, cloudBook) ->
            val localBook = mergedBooks[url]

            if (localBook == null) {
                // New book from cloud
                mergedBooks[url] = cloudBook
            } else {
                // Merge existing book - choose based on last updated time or preference
                val useCloud = if (preferCloud) {
                    true
                } else {
                    cloudBook.lastUpdatedEpochTimeMilli > localBook.lastUpdatedEpochTimeMilli
                }

                mergedBooks[url] = if (useCloud) cloudBook else localBook
            }
        }

        return UserLibrary(
            userId = local.userId,
            books = mergedBooks,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    private suspend fun updateLocalDatabase(userLibrary: UserLibrary) {
        localLibraryRepository.updateBooks(userLibrary.books.values.toList())
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}
