package my.noveldokusha.ui.browse.extractor.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import my.noveldokusha.ui.browse.extractor.data.Cover
import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo.Companion.TABLE_NAME
import my.noveldokusha.ui.browse.extractor.utils.BitmapUtils
import my.noveldokusha.ui.browse.extractor.utils.sizeFormatted
import my.noveldokusha.utils.spToPx
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = TABLE_NAME)
class NovelFileInfo(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val path: String,
    val directory: String,
    val author: String,
    val date: Long,
    val tags: List<String>,
    val title: String,
    val description: String,
    val chapters: List<ChapterInfo>,
    val progress: Int = 0,
    val currentChapter: Int = 0,
    val chapterProgress: Int = 0
) {
    var isFavorite: Boolean = false
    companion object {
        const val TABLE_NAME = "NovelFileInfo"
    }

    @Ignore
    var cover: Cover = Cover(BitmapUtils.textAsBitmap(title, 14.spToPx.toFloat()), title)

    @Ignore
    var fileSize: String = ""
        get() = File(path).sizeFormatted

    @Ignore
    var dateFormatted: String = ""
        get() = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date))
}