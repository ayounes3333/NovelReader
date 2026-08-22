package my.noveldokusha.tooling.quick_setup.transfer.dto

import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.Book

@Serializable
data class BookDto(
    val title: String,
    val url: String,
    val completed: Boolean,
    val lastReadChapter: String?,
    val inLibrary: Boolean,
    val coverImageUrl: String,
    val description: String,
    val lastReadEpochTimeMilli: Long,
) {
    fun toBook(): Book = Book(
        title = title,
        url = url,
        completed = completed,
        lastReadChapter = lastReadChapter,
        inLibrary = inLibrary,
        coverImageUrl = coverImageUrl,
        description = description,
        lastReadEpochTimeMilli = lastReadEpochTimeMilli,
    )

    companion object {
        fun fromBook(book: Book): BookDto = BookDto(
            title = book.title,
            url = book.url,
            completed = book.completed,
            lastReadChapter = book.lastReadChapter,
            inLibrary = book.inLibrary,
            coverImageUrl = book.coverImageUrl,
            description = book.description,
            lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
        )
    }
}
