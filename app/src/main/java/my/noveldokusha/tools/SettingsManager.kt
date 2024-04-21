package my.noveldokusha.tools

import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import my.noveldokusha.App

object SettingsManager {

    private const val KEY_FIREBASE_TOKEN = "KEY_FIREBASE_TOKEN"
    private const val KEY_LANGUAGE = "KEY_LANGUAGE"
    private const val KEY_FIRST_USE = "KEY_FIRST_USE"
    private const val KEY_CURRENT_DIR = "KEY_CURRENT_DIR"
    private const val KEY_TEXT_REPLACEMENTS = "KEY_TEXT_REPLACEMENTS"

    private const val DEFAULT_LANGUAGE = "en"

    private val gson = Gson()

    private val preferences = PreferenceManager
        .getDefaultSharedPreferences(App.instance)

    var isFirstUse: Boolean
        get() = preferences.getBoolean(KEY_FIRST_USE, true)
        set(value) = preferences.edit().putBoolean(KEY_FIRST_USE, value).apply()

    var firebaseToken: String
        get() = preferences.getString(KEY_FIREBASE_TOKEN, "") ?: ""
        set(value) = preferences.edit().putString(KEY_FIREBASE_TOKEN, value).apply()

    var currentDirectory: String
        get() = preferences.getString(KEY_CURRENT_DIR, "") ?: ""
        set(value) = preferences.edit().putString(KEY_CURRENT_DIR, value).apply()

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value).apply()

    var textReplacements: Map<String, String>
        get() {
            val json: String = preferences.getString(KEY_TEXT_REPLACEMENTS, "") ?: ""
            return if (json.isEmpty())
                mapOf(
                    "◇◆◇" to "",
                    "◇◇◇" to "",
                    "◆◆◆" to "",
                    "◆◇◆" to "",
                    "<" to "",
                    ">" to "",
                    "(" to "",
                    ")" to "",
                    "..." to "",
                )
            else
                gson.fromJson(json, mapOf<String, String>()::class.java)
        }
        set(value) = preferences.edit().putString(KEY_TEXT_REPLACEMENTS, gson.toJson(value)).apply()
}
