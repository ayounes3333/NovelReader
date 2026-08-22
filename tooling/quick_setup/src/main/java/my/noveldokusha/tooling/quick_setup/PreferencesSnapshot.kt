package my.noveldokusha.tooling.quick_setup

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

object PreferencesSnapshot {

    @Serializable
    data class Snapshot(
        val entries: Map<String, String>
    )

    private val BOOLEAN_KEYS = setOf(
        "THEME_FOLLOW_SYSTEM",
        "READER_SELECTABLE_TEXT",
        "READER_KEEP_SCREEN_ON",
        "READER_FULL_SCREEN",
        "LIBRARY_GROUP_SERIES",
        "GLOBAL_TRANSLATION_ENABLED",
        "GLOBAL_APP_UPDATER_CHECKER_ENABLED",
        "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED",
    )

    private val FLOAT_KEYS = setOf(
        "READER_FONT_SIZE",
        "READER_TEXT_TO_SPEECH_VOICE_SPEED",
        "READER_TEXT_TO_SPEECH_VOICE_PITCH",
    )

    private val INT_KEYS = setOf(
        "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS",
    )

    private val STRING_SET_KEYS = setOf(
        "SOURCES_LANGUAGES",
        "FINDER_SOURCES_PINNED",
    )

    private val STRING_KEYS = setOf(
        "THEME_ID",
        "READER_FONT_FAMILY",
        "READER_TEXT_TO_SPEECH_VOICE_ID",
        "CHAPTERS_SORT_ASCENDING",
        "LIBRARY_FILTER_READ",
        "LIBRARY_SORT_LAST_READ",
        "BOOKS_LIST_LAYOUT_MODE",
        "GLOBAL_TRANSLATIOR_PREFERRED_SOURCE",
        "GLOBAL_TRANSLATION_PREFERRED_TARGET",
    )

    private val ALL_ALLOWED_KEYS =
        BOOLEAN_KEYS + FLOAT_KEYS + INT_KEYS + STRING_SET_KEYS + STRING_KEYS

    fun export(preferences: SharedPreferences): String {
        val entries = mutableMapOf<String, String>()
        for (key in ALL_ALLOWED_KEYS) {
            when (key) {
                in STRING_SET_KEYS -> {
                    val value = preferences.getStringSet(key, null)
                    if (value != null) {
                        entries[key] = Json.encodeToString(value)
                    }
                }
                else -> {
                    val value = preferences.all[key]
                    if (value != null) {
                        entries[key] = value.toString()
                    }
                }
            }
        }
        return Json.encodeToString(Snapshot(entries))
    }

    fun import(json: String, preferences: SharedPreferences) {
        val snapshot = Json.decodeFromString<Snapshot>(json)
        val editor = preferences.edit()
        for ((key, value) in snapshot.entries) {
            when (key) {
                in BOOLEAN_KEYS -> {
                    value.toBooleanStrictOrNull()?.let { editor.putBoolean(key, it) }
                }
                in FLOAT_KEYS -> {
                    value.toFloatOrNull()?.let { editor.putFloat(key, it) }
                }
                in INT_KEYS -> {
                    value.toIntOrNull()?.let { editor.putInt(key, it) }
                }
                in STRING_SET_KEYS -> {
                    try {
                        val set = Json.decodeFromString<Set<String>>(value)
                        editor.putStringSet(key, set)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse string set for key $key")
                    }
                }
                in STRING_KEYS -> {
                    editor.putString(key, value)
                }
            }
        }
        editor.apply()
    }
}
