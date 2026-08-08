package my.noveldokusha.tooling.local_server_sync.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.data.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists everything the local-server sync feature needs across process
 * restarts: tokens, server URLs, per-data-type cursors, and home WiFi
 * BSSIDs for triggering bulk syncs.
 */
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
        // Auth
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
        /** Legacy key from previous app versions. Used only as a migration source. */
        private const val KEY_LEGACY_AUTH_TOKEN = "auth_token"

        // Account
        private const val KEY_USER_INFO = "user_info"

        // Servers
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_URLS = "server_urls"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"

        // Home network
        private const val KEY_HOME_BSSIDS = "home_bssids"

        // Per-data-type incremental sync cursors
        private const val KEY_CURSOR_LIBRARY = "cursor_library"
        private const val KEY_CURSOR_CHAPTERS = "cursor_chapters"
        private const val KEY_CURSOR_BODIES = "cursor_chapter_bodies"
        private const val KEY_CURSOR_IMAGES = "cursor_images"
    }

    // ── Tokens ───────────────────────────────────────────────────────────

    fun saveTokens(
        accessToken: String,
        accessExpiresAt: Long,
        refreshToken: String?,
        refreshExpiresAt: Long
    ) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putLong(KEY_ACCESS_EXPIRES_AT, accessExpiresAt)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt)
            }
            // Clean up legacy key once we've migrated to the new pair.
            remove(KEY_LEGACY_AUTH_TOKEN)
        }
    }

    fun getAccessToken(): String? =
        prefs.getString(KEY_ACCESS_TOKEN, null)
            ?: prefs.getString(KEY_LEGACY_AUTH_TOKEN, null)

    fun getAccessExpiresAt(): Long = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getRefreshExpiresAt(): Long = prefs.getLong(KEY_REFRESH_EXPIRES_AT, 0L)

    /** True if we have an access token that is still valid (60s safety margin). */
    fun isAccessTokenValid(now: Long = System.currentTimeMillis()): Boolean {
        val token = getAccessToken() ?: return false
        if (token.isBlank()) return false
        val exp = getAccessExpiresAt()
        // If we never recorded an expiry (legacy path), treat as valid; the
        // server will tell us if it isn't and we'll refresh on 401.
        if (exp == 0L) return true
        return exp - 60_000L > now
    }

    fun clearTokens() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_ACCESS_EXPIRES_AT)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_REFRESH_EXPIRES_AT)
            remove(KEY_LEGACY_AUTH_TOKEN)
        }
    }

    // Legacy aliases retained for ABI compatibility with older callers.
    @Deprecated("Use saveTokens()", ReplaceWith("saveTokens(...)"))
    fun saveToken(token: String) {
        prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
    }

    @Deprecated("Use getAccessToken()", ReplaceWith("getAccessToken()"))
    fun getToken(): String? = getAccessToken()

    @Deprecated("Use clearTokens()", ReplaceWith("clearTokens()"))
    fun clearToken() = clearTokens()

    // ── User info ────────────────────────────────────────────────────────

    fun saveUserInfo(userInfo: UserInfo) {
        prefs.edit { putString(KEY_USER_INFO, json.encodeToString(userInfo)) }
    }

    fun getUserInfo(): UserInfo? {
        val raw = prefs.getString(KEY_USER_INFO, null) ?: return null
        return try {
            json.decodeFromString<UserInfo>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clearUserInfo() {
        prefs.edit { remove(KEY_USER_INFO) }
    }

    // ── Servers ──────────────────────────────────────────────────────────

    fun saveServerUrl(url: String) {
        prefs.edit { putString(KEY_SERVER_URL, url) }
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun saveServerUrls(urls: List<String>) {
        prefs.edit { putStringSet(KEY_SERVER_URLS, urls.toSet()) }
    }

    fun getServerUrls(): List<String> {
        val urls = prefs.getStringSet(KEY_SERVER_URLS, null)
        if (urls != null) return urls.toList()
        val single = getServerUrl()
        return if (single != null) listOf(single) else emptyList()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SYNC_ENABLED, enabled) }
    }

    fun isAutoSyncEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)

    // ── Home WiFi BSSIDs ──────────────────────────────────────────────────

    fun saveHomeBssids(bssids: List<String>) {
        prefs.edit {
            putStringSet(
                KEY_HOME_BSSIDS,
                bssids.map { it.lowercase() }.toSet()
            )
        }
    }

    fun getHomeBssids(): List<String> =
        prefs.getStringSet(KEY_HOME_BSSIDS, null)?.toList() ?: emptyList()

    fun addHomeBssid(bssid: String) {
        val current = getHomeBssids().toMutableSet()
        current.add(bssid.lowercase())
        saveHomeBssids(current.toList())
    }

    fun removeHomeBssid(bssid: String) {
        val current = getHomeBssids().toMutableSet()
        current.remove(bssid.lowercase())
        saveHomeBssids(current.toList())
    }

    // ── Per-table cursors ────────────────────────────────────────────────

    enum class SyncCursor(val key: String) {
        LIBRARY(KEY_CURSOR_LIBRARY),
        CHAPTERS(KEY_CURSOR_CHAPTERS),
        CHAPTER_BODIES(KEY_CURSOR_BODIES),
        IMAGES(KEY_CURSOR_IMAGES)
    }

    fun getCursor(which: SyncCursor): Long = prefs.getLong(which.key, 0L)

    fun setCursor(which: SyncCursor, value: Long) {
        // Only advance forward to avoid resetting on a stale response.
        val current = prefs.getLong(which.key, 0L)
        if (value > current) prefs.edit { putLong(which.key, value) }
    }

    fun resetCursors() {
        prefs.edit {
            remove(KEY_CURSOR_LIBRARY)
            remove(KEY_CURSOR_CHAPTERS)
            remove(KEY_CURSOR_BODIES)
            remove(KEY_CURSOR_IMAGES)
        }
    }

    fun clearAll() {
        prefs.edit { clear() }
    }
}
