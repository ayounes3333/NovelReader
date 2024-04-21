package my.noveldokusha.ui.browse.extractor

import my.noveldokusha.ui.browse.extractor.data.ChapterInfo
import my.noveldokusha.ui.browse.extractor.data.Cover
import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo
import java.io.File

abstract class BaseExtractor(private val file: File? = null) {
    val path: String
        get() = file?.path ?: ""
    val novelInfo: NovelFileInfo
        get() = NovelFileInfo (
            path = file?.absolutePath ?: "",
            directory = file?.parentFile?.takeIf { it.isDirectory }?.absolutePath ?: "",
            id = getNovelId(),
            title = getNovelTitle(),
            description = getNovelDescription(),
            chapters = getNovelChapters(),
            author = getNovelAuthor(),
            date = getNovelDate(),
            tags = getNovelTags()
        ).apply { cover = getNovelCover() }
    abstract fun getNovelCover(): Cover
    abstract fun getNovelChapters(): List<ChapterInfo>
    abstract fun getNovelTitle(): String
    abstract fun getNovelId(): String
    abstract fun getNovelAuthor(): String
    abstract fun getNovelDate(): Long
    abstract fun getNovelTags(): List<String>
    abstract fun getNovelDescription(): String
}
