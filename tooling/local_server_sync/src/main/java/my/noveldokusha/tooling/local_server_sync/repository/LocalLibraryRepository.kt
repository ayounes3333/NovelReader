package my.noveldokusha.tooling.local_server_sync.repository

import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter

interface LocalLibraryRepository {
    suspend fun getAllBooks(): List<Book>
    suspend fun updateOrInsertBook(book: Book)
    suspend fun getChapters(bookUrl: String): List<Chapter>
    suspend fun getChaptersCount(bookUrl: String): Int
    suspend fun getReadChaptersCount(bookUrl: String): Int
    suspend fun updateChapter(chapter: Chapter)
    suspend fun getChapterBody(chapterUrl: String): String?
    suspend fun saveChapterBody(chapterUrl: String, body: String)
}
