package my.noveldokusha.tooling.local_server_sync.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.noveldokusha.tooling.local_server_sync.api.LocalServerApiService
import my.noveldokusha.tooling.local_server_sync.data.AuthResponse
import my.noveldokusha.tooling.local_server_sync.data.LoginRequest
import my.noveldokusha.tooling.local_server_sync.data.RegisterRequest
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    object NotAuthenticated : AuthState()
    data class Authenticated(val userId: String, val username: String, val email: String) : AuthState()
    object Loading : AuthState()
}

@Singleton
class LocalServerAuthService @Inject constructor(
    private val apiService: LocalServerApiService,
    private val tokenStorage: AuthTokenStorage
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotAuthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Check if we have a stored token and validate it
        checkStoredAuthentication()
    }

    private fun checkStoredAuthentication() {
        val token = tokenStorage.getToken()
        if (token != null) {
            // TODO: Validate token with server
            // For now, we'll assume it's valid if it exists
            val userInfo = tokenStorage.getUserInfo()
            if (userInfo != null) {
                _authState.value = AuthState.Authenticated(
                    userId = userInfo.id,
                    username = userInfo.username,
                    email = userInfo.email
                )
            }
        }
    }

    suspend fun signInWithUsernameAndPassword(username: String, password: String): Result<String> {
        return try {
            _authState.value = AuthState.Loading

            val loginRequest = LoginRequest(username = username, password = password)
            val response = apiService.login(loginRequest)

            if (response.success && response.token != null && response.user != null) {
                // Store the token and user info
                tokenStorage.saveToken(response.token)
                tokenStorage.saveUserInfo(response.user)

                _authState.value = AuthState.Authenticated(
                    userId = response.user.id,
                    username = response.user.username,
                    email = response.user.email
                )

                Timber.d("User signed in successfully: ${response.user.id}")
                Result.success(response.user.id)
            } else {
                _authState.value = AuthState.NotAuthenticated
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
            Timber.e(e, "Sign in failed")
            Result.failure(e)
        }
    }

    suspend fun createUserWithUsernameEmailAndPassword(
        username: String,
        email: String,
        password: String
    ): Result<String> {
        return try {
            _authState.value = AuthState.Loading

            val registerRequest = RegisterRequest(
                username = username,
                email = email,
                password = password
            )
            val response = apiService.register(registerRequest)

            if (response.success && response.token != null && response.user != null) {
                // Store the token and user info
                tokenStorage.saveToken(response.token)
                tokenStorage.saveUserInfo(response.user)

                _authState.value = AuthState.Authenticated(
                    userId = response.user.id,
                    username = response.user.username,
                    email = response.user.email
                )

                Timber.d("User registered successfully: ${response.user.id}")
                Result.success(response.user.id)
            } else {
                _authState.value = AuthState.NotAuthenticated
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
            Timber.e(e, "Registration failed")
            Result.failure(e)
        }
    }

    fun signOut() {
        tokenStorage.clearToken()
        tokenStorage.clearUserInfo()
        _authState.value = AuthState.NotAuthenticated
        Timber.d("User signed out")
    }

    fun getCurrentUserId(): String? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.userId
            else -> null
        }
    }

    fun getCurrentUsername(): String? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.username
            else -> null
        }
    }

    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    fun getAuthToken(): String? {
        return tokenStorage.getToken()
    }
}
