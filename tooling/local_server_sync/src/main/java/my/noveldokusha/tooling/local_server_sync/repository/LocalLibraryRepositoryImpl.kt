package my.noveldokusha.tooling.local_server_sync.repository

import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : LocalLibraryRepository {

    override suspend fun getAllBooks(): List<Book> {
        return database.libraryDao().getAll()
    }

    override suspend fun updateOrInsertBook(book: Book) {
        database.libraryDao().insertReplace(listOf(book))
    }

    override suspend fun getChapters(bookUrl: String): List<Chapter> {
        return database.chapterDao().chapters(bookUrl)
    }

    override suspend fun getChaptersCount(bookUrl: String): Int {
        return database.chapterDao().chaptersCount(bookUrl)
    }

    override suspend fun getReadChaptersCount(bookUrl: String): Int {
        return database.chapterDao().chaptersReadCount(bookUrl)
    }

    override suspend fun updateChapter(chapter: Chapter) {
        database.chapterDao().update(chapter)
    }

    override suspend fun getChapterBody(chapterUrl: String): String? {
        return database.chapterBodyDao().get(chapterUrl)?.body
    }

    override suspend fun saveChapterBody(chapterUrl: String, body: String) {
        database.chapterBodyDao().insertReplace(
            my.noveldokusha.feature.local_database.tables.ChapterBody(
                url = chapterUrl,
                body = body
            )
        )
    }
}
