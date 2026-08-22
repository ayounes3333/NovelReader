package my.noveldokusha.tooling.quick_setup.transfer.dto

import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.ChapterBody

@Serializable
data class ChapterBodyDto(
    val url: String,
    val body: String,
    val createdEpochTimeMilli: Long,
    val lastUpdatedEpochTimeMilli: Long,
) {
    fun toChapterBody() = ChapterBody(
        url = url,
        body = body,
        createdEpochTimeMilli = createdEpochTimeMilli,
        lastUpdatedEpochTimeMilli = lastUpdatedEpochTimeMilli,
    )

    companion object {
        fun fromChapterBody(chapterBody: ChapterBody) = ChapterBodyDto(
            url = chapterBody.url,
            body = chapterBody.body,
            createdEpochTimeMilli = chapterBody.createdEpochTimeMilli,
            lastUpdatedEpochTimeMilli = chapterBody.lastUpdatedEpochTimeMilli,
        )
    }
}
