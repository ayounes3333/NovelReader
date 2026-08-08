package my.noveldokusha.tooling.local_server_sync.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.noveldokusha.tooling.local_server_sync.api.LocalServerApiService
import my.noveldokusha.tooling.local_server_sync.data.AuthResponse
import my.noveldokusha.tooling.local_server_sync.data.LoginRequest
import my.noveldokusha.tooling.local_server_sync.data.RegisterRequest
import my.noveldokusha.tooling.local_server_sync.data.UserInfo
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
        checkStoredAuthentication()
    }

    private fun checkStoredAuthentication() {
        val token = tokenStorage.getAccessToken() ?: return
        if (token.isBlank()) return
        val userInfo = tokenStorage.getUserInfo() ?: return
        _authState.value = AuthState.Authenticated(
            userId = userInfo.id,
            username = userInfo.username,
            email = userInfo.email
        )
    }

    suspend fun signInWithUsernameAndPassword(username: String, password: String): Result<String> {
        _authState.value = AuthState.Loading
        return try {
            val response = apiService.login(LoginRequest(username = username, password = password))
            persistOrFail(response)
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
        _authState.value = AuthState.Loading
        return try {
            val response = apiService.register(
                RegisterRequest(username = username, email = email, password = password)
            )
            persistOrFail(response)
        } catch (e: Exception) {
            _authState.value = AuthState.NotAuthenticated
            Timber.e(e, "Registration failed")
            Result.failure(e)
        }
    }

    private fun persistOrFail(response: AuthResponse): Result<String> {
        val access = response.accessToken ?: response.token
        val user = response.user
        if (!response.success || access.isNullOrBlank()) {
            _authState.value = AuthState.NotAuthenticated
            return Result.failure(Exception(response.message.ifBlank { "Authentication failed" }))
        }
        // user info may be missing on legacy servers; synthesize what we can.
        val resolved = user ?: UserInfo(id = "", username = "", email = "")
        tokenStorage.saveTokens(
            accessToken = access,
            accessExpiresAt = response.accessExpiresAt,
            refreshToken = response.refreshToken,
            refreshExpiresAt = response.refreshExpiresAt
        )
        tokenStorage.saveUserInfo(resolved)
        _authState.value = AuthState.Authenticated(
            userId = resolved.id,
            username = resolved.username,
            email = resolved.email
        )
        Timber.d("User authenticated: ${resolved.id}")
        return Result.success(resolved.id)
    }

    fun signOut() {
        val refresh = tokenStorage.getRefreshToken()
        tokenStorage.clearTokens()
        tokenStorage.clearUserInfo()
        tokenStorage.resetCursors()
        _authState.value = AuthState.NotAuthenticated
        Timber.d("User signed out")
        // Best-effort server revoke — ignore failures.
        if (!refresh.isNullOrBlank()) {
            try {
                kotlinx.coroutines.runBlocking { apiService.logout(refresh) }
            } catch (_: Exception) { /* fire and forget */ }
        }
    }

    fun getCurrentUserId(): String? = when (val s = _authState.value) {
        is AuthState.Authenticated -> s.userId
        else -> null
    }

    fun getCurrentUsername(): String? = when (val s = _authState.value) {
        is AuthState.Authenticated -> s.username
        else -> null
    }

    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated

    fun getAuthToken(): String? = tokenStorage.getAccessToken()
}
