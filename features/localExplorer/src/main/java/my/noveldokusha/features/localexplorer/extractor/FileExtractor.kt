package my.noveldokusha.features.localexplorer.extractor

import my.noveldokusha.core.utils.spToPx
import my.noveldokusha.feature.local_database.tables.localexplorer.ChapterInfo
import my.noveldokusha.feature.local_database.tables.localexplorer.Cover
import my.noveldokusha.feature.local_database.BitmapUtils.textAsBitmap
import java.io.File

class FileExtractor(private val file: File) : BaseExtractor(file) {
    override fun getNovelCover(): Cover {
        return Cover(
            bitmap = textAsBitmap(getNovelTitle(), 14.spToPx.toFloat()),
            text = getNovelTitle()
        )
    }

    override fun getNovelChapters(): List<ChapterInfo> {
        return emptyList()
    }

    override fun getNovelTitle(): String {
        return file.name
    }

    override fun getNovelId(): String {
        return file.absolutePath + file.name
    }

    override fun getNovelAuthor(): String {
        return file.name
    }

    override fun getNovelDate(): Long {
        return file.lastModified()
    }

    override fun getNovelTags(): List<String> {
        return emptyList()
    }

    override fun getNovelDescription(): String {
        return getNovelTitle()
    }

}