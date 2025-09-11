package my.noveldokusha.tooling.local_server_sync.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.local_server_sync.auth.AuthState
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocalServerAuthViewModel @Inject constructor(
    private val authService: LocalServerAuthService,
    private val tokenStorage: AuthTokenStorage
) : ViewModel() {

    val authState: StateFlow<AuthState> = authService.authState

    private var _username by mutableStateOf("")
    val username: String get() = _username

    private var _email by mutableStateOf("")
    val email: String get() = _email

    private var _password by mutableStateOf("")
    val password: String get() = _password

    private var _serverUrl by mutableStateOf("http://10.0.2.2:8080")
    val serverUrl: String get() = _serverUrl

    private var _errorMessage by mutableStateOf("")
    val errorMessage: String get() = _errorMessage

    init {
        // Load saved server URL if available
        val savedServerUrl = tokenStorage.getServerUrl()
        if (!savedServerUrl.isNullOrBlank()) {
            _serverUrl = savedServerUrl
        }
    }

    fun setUsername(newUsername: String) {
        _username = newUsername
        clearError()
    }

    fun setEmail(newEmail: String) {
        _email = newEmail
        clearError()
    }

    fun setPassword(newPassword: String) {
        _password = newPassword
        clearError()
    }

    fun setServerUrl(newUrl: String) {
        _serverUrl = newUrl
        tokenStorage.saveServerUrl(newUrl)
        clearError()
    }

    fun clearError() {
        _errorMessage = ""
    }

    fun login() {
        viewModelScope.launch {
            try {
                val result = authService.signInWithUsernameAndPassword(username, password)
                result.fold(
                    onSuccess = {
                        Timber.d("Login successful")
                        clearForm()
                    },
                    onFailure = { exception ->
                        _errorMessage = exception.message ?: "Login failed"
                        Timber.e(exception, "Login failed")
                    }
                )
            } catch (e: Exception) {
                _errorMessage = e.message ?: "An unexpected error occurred"
                Timber.e(e, "Login error")
            }
        }
    }

    fun register() {
        viewModelScope.launch {
            try {
                val result = authService.createUserWithUsernameEmailAndPassword(username, email, password)
                result.fold(
                    onSuccess = {
                        Timber.d("Registration successful")
                        clearForm()
                    },
                    onFailure = { exception ->
                        _errorMessage = exception.message ?: "Registration failed"
                        Timber.e(exception, "Registration failed")
                    }
                )
            } catch (e: Exception) {
                _errorMessage = e.message ?: "An unexpected error occurred"
                Timber.e(e, "Registration error")
            }
        }
    }

    fun signOut() {
        authService.signOut()
        clearForm()
    }

    private fun clearForm() {
        _username = ""
        _email = ""
        _password = ""
        _errorMessage = ""
    }
}
