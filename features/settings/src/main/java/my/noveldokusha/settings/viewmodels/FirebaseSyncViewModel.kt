package my.noveldokusha.settings.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import my.noveldokusha.tooling.firebase_sync.manager.SyncManager
import javax.inject.Inject

@HiltViewModel
class FirebaseSyncViewModel @Inject constructor(
    val syncManager: SyncManager
) : ViewModel()
