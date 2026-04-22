package my.noveldokusha.tooling.local_server_sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.local_server_sync.auth.AuthState
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.manager.LocalServerSyncManager
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import my.noveldokusha.tooling.local_server_sync.sync.WiFiSyncScheduler
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val operation: String) : SyncState()
    data class Success(val timestamp: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class LocalServerSyncViewModel @Inject constructor(
    private val authService: LocalServerAuthService,
    private val syncManager: LocalServerSyncManager,
    private val tokenStorage: AuthTokenStorage,
    private val wifiSyncScheduler: WiFiSyncScheduler
) : ViewModel() {

    val authState: StateFlow<AuthState> = authService.authState

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(tokenStorage.isAutoSyncEnabled())
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _serverUrls = MutableStateFlow(tokenStorage.getServerUrls())
    val serverUrls: StateFlow<List<String>> = _serverUrls.asStateFlow()

    private val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    init {
        if (_autoSyncEnabled.value) {
            wifiSyncScheduler.register()
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        tokenStorage.setAutoSyncEnabled(enabled)
        _autoSyncEnabled.value = enabled
        if (enabled) {
            wifiSyncScheduler.register()
        } else {
            wifiSyncScheduler.unregister()
        }
    }

    fun addServerUrl(url: String) {
        val current = _serverUrls.value.toMutableList()
        val trimmed = url.trimEnd('/')
        if (trimmed.isNotBlank() && trimmed !in current) {
            current.add(trimmed)
            tokenStorage.saveServerUrls(current)
            _serverUrls.value = current
        }
    }

    fun removeServerUrl(url: String) {
        val current = _serverUrls.value.toMutableList()
        current.remove(url)
        tokenStorage.saveServerUrls(current)
        _serverUrls.value = current
    }

    fun syncLibrary() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing("Syncing library...")

                val result = syncManager.syncLibrary()
                result.fold(
                    onSuccess = {
                        _syncState.value = SyncState.Success(
                            timestamp = dateFormatter.format(Date())
                        )
                        Timber.d("Library sync completed successfully")
                    },
                    onFailure = { exception ->
                        _syncState.value = SyncState.Error(
                            message = exception.message ?: "Sync failed"
                        )
                        Timber.e(exception, "Library sync failed")
                    }
                )
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(
                    message = e.message ?: "An unexpected error occurred"
                )
                Timber.e(e, "Sync error")
            }
        }
    }

    fun syncComplete() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing("Complete sync with images...")

                val result = syncManager.performCompleteSync()
                result.fold(
                    onSuccess = {
                        _syncState.value = SyncState.Success(
                            timestamp = dateFormatter.format(Date())
                        )
                        Timber.d("Complete sync with images completed successfully")
                    },
                    onFailure = { exception ->
                        _syncState.value = SyncState.Error(
                            message = exception.message ?: "Complete sync failed"
                        )
                        Timber.e(exception, "Complete sync with images failed")
                    }
                )
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(
                    message = e.message ?: "An unexpected error occurred"
                )
                Timber.e(e, "Complete sync error")
            }
        }
    }

    fun syncImages() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing("Syncing images...")

                val result = syncManager.syncImages()
                result.fold(
                    onSuccess = {
                        _syncState.value = SyncState.Success(
                            timestamp = dateFormatter.format(Date())
                        )
                        Timber.d("Image sync completed successfully")
                    },
                    onFailure = { exception ->
                        _syncState.value = SyncState.Error(
                            message = exception.message ?: "Image sync failed"
                        )
                        Timber.e(exception, "Image sync failed")
                    }
                )
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(
                    message = e.message ?: "An unexpected error occurred"
                )
                Timber.e(e, "Image sync error")
            }
        }
    }

    fun signOut() {
        authService.signOut()
        _syncState.value = SyncState.Idle
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }
}
