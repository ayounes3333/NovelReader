package my.noveldokusha.tooling.local_server_sync.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.data.UserInfo
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class AuthTokenStorage @Inject constructor(
    context: Context
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
        private const val KEY_SERVER_URLS = "server_urls"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }

    fun saveToken(token: String) {
        prefs.edit { putString(KEY_AUTH_TOKEN, token) }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit { remove(KEY_AUTH_TOKEN) }
    }

    fun saveUserInfo(userInfo: UserInfo) {
        val userInfoJson = json.encodeToString(userInfo)
        prefs.edit { putString(KEY_USER_INFO, userInfoJson) }
    }

    fun getUserInfo(): UserInfo? {
        val userInfoJson = prefs.getString(KEY_USER_INFO, null)
        return if (userInfoJson != null) {
            try {
                json.decodeFromString<UserInfo>(userInfoJson)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearUserInfo() {
        prefs.edit { remove(KEY_USER_INFO) }
    }

    fun saveServerUrl(url: String) {
        prefs.edit { putString(KEY_SERVER_URL, url) }
    }

    fun getServerUrl(): String? {
        return prefs.getString(KEY_SERVER_URL, null)
    }

    fun clearAll() {
        prefs.edit { clear() }
    }

    fun saveServerUrls(urls: List<String>) {
        prefs.edit { putStringSet(KEY_SERVER_URLS, urls.toSet()) }
    }

    fun getServerUrls(): List<String> {
        val urls = prefs.getStringSet(KEY_SERVER_URLS, null)
        if (urls != null) return urls.toList()
        // Migrate from single URL if present
        val single = getServerUrl()
        return if (single != null) listOf(single) else emptyList()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SYNC_ENABLED, enabled) }
    }

    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }
}
