package my.noveldokusha.feature.local_database.tables.localexplorer

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import my.noveldokusha.core.utils.spToPx
import my.noveldokusha.feature.local_database.BitmapUtils
import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo.Companion.TABLE_NAME

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

    @Ignore
    var cover: () -> Cover = { Cover(BitmapUtils.textAsBitmap(title, 14.spToPx.toFloat()), title) }

    companion object {
        const val TABLE_NAME = "NovelFileInfo"
    }
}