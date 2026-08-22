package my.noveldokusha.tooling.quick_setup.transfer.resilience

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.quick_setup.transfer.TransferManifest
import timber.log.Timber

/**
 * Persists transfer session state to enable resume after interruption.
 * Stores: manifest hash, completed pages per category, imported image hashes.
 */
class TransferSessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_setup_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SessionState(
        val sessionId: String,
        val manifestHash: String,
        val manifest: TransferManifest,
        val lastCompletedPhase: String,
        val completedBookPages: List<Int>,
        val completedChapterPages: List<Int>,
        val completedBodyPages: List<Int> = emptyList(),
        val importedImageHashes: List<String>,
        val importedPreferences: Boolean,
        val host: String? = null,
        val port: Int = 0,
        val startedAt: Long,
        val updatedAt: Long,
    )

    fun saveSession(state: SessionState) {
        try {
            val encoded = json.encodeToString(state)
            prefs.edit()
                .putString(KEY_SESSION, encoded)
                .apply()
            Timber.d("Transfer session saved: ${state.sessionId}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save transfer session")
        }
    }

    fun loadSession(): SessionState? {
        return try {
            val encoded = prefs.getString(KEY_SESSION, null) ?: return null
            json.decodeFromString<SessionState>(encoded)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load transfer session")
            null
        }
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
        Timber.d("Transfer session cleared")
    }

    fun hasActiveSession(): Boolean {
        return prefs.contains(KEY_SESSION)
    }

    companion object {
        private const val KEY_SESSION = "transfer_session"

        fun generateSessionId(): String {
            return "qs_${System.currentTimeMillis()}_${(0..9999).random()}"
        }

        fun manifestHash(manifest: TransferManifest): String {
            val content = "${manifest.schemaVersion}:${manifest.booksCount}:${manifest.chaptersCount}:" +
                    "${manifest.chapterBodiesCount}:${manifest.imagesCount}:${manifest.totalImageBytes}"
            return content.hashCode().toString(16)
        }
    }
}
