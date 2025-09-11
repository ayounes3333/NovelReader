package my.noveldokusha.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.settings.sections.SyncProvider
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import javax.inject.Inject

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val authTokenStorage: AuthTokenStorage
) : ViewModel() {

    private val _selectedSyncProvider = MutableStateFlow(SyncProvider.NONE)
    val selectedSyncProvider: StateFlow<SyncProvider> = _selectedSyncProvider.asStateFlow()

    init {
        loadSavedSyncProvider()
    }

    private fun loadSavedSyncProvider() {
        viewModelScope.launch {
            // Load the saved sync provider preference
            val savedProvider = authTokenStorage.getServerUrl()?.let {
                if (it.isNotBlank()) SyncProvider.LOCAL_SERVER else SyncProvider.NONE
            } ?: SyncProvider.NONE

            _selectedSyncProvider.value = savedProvider
        }
    }

    fun setSyncProvider(provider: SyncProvider) {
        _selectedSyncProvider.value = provider

        // Save the preference and handle provider switching
        viewModelScope.launch {
            when (provider) {
                SyncProvider.LOCAL_SERVER -> {
                    // Keep existing server URL if set, otherwise will be set when user configures
                }
                SyncProvider.FIREBASE -> {
                    // Clear local server settings when switching to Firebase
                    authTokenStorage.clearAll()
                }
                SyncProvider.NONE -> {
                    // Clear all sync settings when disabling sync
                    authTokenStorage.clearAll()
                }
            }
        }
    }

    fun isFirebaseAvailable(): Boolean {
        return try {
            Class.forName("my.noveldokusha.tooling.firebase_sync.manager.SyncManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
