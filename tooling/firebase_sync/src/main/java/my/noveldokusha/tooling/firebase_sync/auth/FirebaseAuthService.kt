package my.noveldokusha.tooling.firebase_sync.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotAuthenticated)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: Flow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            _currentUser.value = user
            _authState.value = if (user != null) {
                AuthState.Authenticated(user.uid, user.email ?: "")
            } else {
                AuthState.NotAuthenticated
            }
        }
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Timber.d("User signed in successfully: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: User is null"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Sign in failed")
            Result.failure(e)
        }
    }

    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Timber.d("User created successfully: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("User creation failed: User is null"))
            }
        } catch (e: Exception) {
            Timber.e(e, "User creation failed")
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            firebaseAuth.signOut()
            Timber.d("User signed out successfully")
        } catch (e: Exception) {
            Timber.e(e, "Sign out failed")
        }
    }

    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null

    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}

sealed class AuthState {
    object NotAuthenticated : AuthState()
    data class Authenticated(val userId: String, val email: String) : AuthState()
}
