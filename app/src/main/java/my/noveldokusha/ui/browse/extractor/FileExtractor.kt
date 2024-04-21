package my.noveldokusha.ui.browse.extractor

import my.noveldokusha.ui.browse.extractor.data.ChapterInfo
import my.noveldokusha.ui.browse.extractor.data.Cover
import my.noveldokusha.ui.browse.extractor.utils.BitmapUtils.textAsBitmap
import my.noveldokusha.utils.spToPx
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