package my.noveldokusha.tooling.local_server_sync.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.data.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenStorage @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "local_server_auth",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_INFO = "user_info"
        private const val KEY_SERVER_URL = "server_url"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply()
    }

    fun saveUserInfo(userInfo: UserInfo) {
        val userInfoJson = json.encodeToString(userInfo)
        prefs.edit().putString(KEY_USER_INFO, userInfoJson).apply()
    }

    fun getUserInfo(): UserInfo? {
        val userInfoJson = prefs.getString(KEY_USER_INFO, null)
        return if (userInfoJson != null) {
            try {
                json.decodeFromString<UserInfo>(userInfoJson)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearUserInfo() {
        prefs.edit().remove(KEY_USER_INFO).apply()
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(): String? {
        return prefs.getString(KEY_SERVER_URL, null)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
