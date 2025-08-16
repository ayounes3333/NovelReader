package my.noveldokusha.tooling.firebase_sync.manager

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import my.noveldokusha.tooling.firebase_sync.auth.AuthState
import my.noveldokusha.tooling.firebase_sync.auth.FirebaseAuthService
import my.noveldokusha.tooling.firebase_sync.sync.LibrarySyncService
import my.noveldokusha.tooling.firebase_sync.sync.SyncState
import my.noveldokusha.tooling.firebase_sync.worker.LibrarySyncWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val authService: FirebaseAuthService,
    private val syncService: LibrarySyncService
) {
    val authState: Flow<AuthState> = authService.authState
    val syncState: Flow<SyncState> = syncService.syncState

    val canSync: Flow<Boolean> = combine(authState, syncState) { auth, sync ->
        auth is AuthState.Authenticated && sync != SyncState.Syncing
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return authService.signInWithEmailAndPassword(email, password).map { }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        return authService.createUserWithEmailAndPassword(email, password).map { }
    }

    suspend fun signOut() {
        authService.signOut()
    }

    suspend fun performInitialSync(): Result<Unit> {
        return syncService.performInitialSync()
    }

    suspend fun performManualSync(): Result<Unit> {
        return syncService.performIncrementalSync()
    }

    fun enableAutomaticSync(context: Context) {
        LibrarySyncWorker.enqueuePeriodicSync(context)
    }

    fun disableAutomaticSync(context: Context) {
        LibrarySyncWorker.cancelPeriodicSync(context)
    }

    fun isSignedIn(): Boolean = authService.isSignedIn()
}
