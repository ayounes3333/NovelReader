package my.noveldokusha.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import my.noveldokusha.ui.browse.extractor.data.ChapterInfo
import java.util.ArrayList

class Converters {

    //List of Strings
    @TypeConverter
    fun toStringsList(value: String): List<String> {
        return from(JsonParser.parseString(value).asJsonArray)
    }

    @TypeConverter
    fun fromStringsList(strings: List<String>): String {
        return Gson().toJson(strings)
    }

    //Novel Chapters
    @TypeConverter
    fun toChapterInfo(value: String): List<ChapterInfo> {
        return from(JsonParser.parseString(value).asJsonArray)
    }

    @TypeConverter
    fun fromChapterInfo(chapterInfo: List<ChapterInfo>): String {
        return Gson().toJson(chapterInfo)
    }

    inline fun <reified T : Any> from(array: JsonArray): ArrayList<T> {
        val gson = Gson()
        val events = ArrayList<T>()
        for (element: JsonElement in array) {
            events.add(gson.fromJson(element, T::class.java))
        }
        return events
    }
}