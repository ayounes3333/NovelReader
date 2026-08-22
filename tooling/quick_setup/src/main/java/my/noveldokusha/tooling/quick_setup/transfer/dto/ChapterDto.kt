package my.noveldokusha.tooling.quick_setup.transfer.dto

import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.Chapter

@Serializable
data class ChapterDto(
    val title: String,
    val url: String,
    val bookUrl: String,
    val position: Int,
    val read: Boolean,
    val lastReadPosition: Int,
    val lastReadOffset: Int,
    val createdEpochTimeMilli: Long,
    val lastUpdatedEpochTimeMilli: Long,
) {
    fun toChapter(): Chapter = Chapter(
        title = title,
        url = url,
        bookUrl = bookUrl,
        position = position,
        read = read,
        lastReadPosition = lastReadPosition,
        lastReadOffset = lastReadOffset,
        createdEpochTimeMilli = createdEpochTimeMilli,
        lastUpdatedEpochTimeMilli = lastUpdatedEpochTimeMilli,
    )

    companion object {
        fun fromChapter(chapter: Chapter): ChapterDto = ChapterDto(
            title = chapter.title,
            url = chapter.url,
            bookUrl = chapter.bookUrl,
            position = chapter.position,
            read = chapter.read,
            lastReadPosition = chapter.lastReadPosition,
            lastReadOffset = chapter.lastReadOffset,
            createdEpochTimeMilli = chapter.createdEpochTimeMilli,
            lastUpdatedEpochTimeMilli = chapter.lastUpdatedEpochTimeMilli,
        )
    }
}
