package my.noveldoksuha.data

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.fileImporter
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.epub_tooling.epubParser
import my.noveldokusha.epub_tooling.EpubBook
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubImporterRepository @Inject constructor(
    private val libraryBooks: LibraryBooksRepository,
    private val bookChapters: BookChaptersRepository,
    private val chapterBody: ChapterBodyRepository,
    private val appFileResolver: AppFileResolver,
    @ApplicationContext private val context: Context,
) {
    suspend fun importEpubFromContentUri(
        contentUri: String,
        bookTitle: String,
        addToLibrary: Boolean = false
    ) = tryAsResponse {
        val inputStream = context.contentResolver.openInputStream(contentUri.toUri())
            ?: return@tryAsResponse
        val epub = inputStream.use { epubParser(inputStream = inputStream) }
        epubImporter(
            storageFolderName = bookTitle,
            epub = epub,
            addToLibrary = addToLibrary
        )
    }

    suspend fun epubImporter(
        storageFolderName: String,
        epub: EpubBook,
        addToLibrary: Boolean
    ): String = withContext(Dispatchers.IO) {
        val localBookUrl = appFileResolver.getLocalBookPath(storageFolderName)

        // Preserve existing book progress before cleaning
        val existingBook = libraryBooks.get(localBookUrl)

        // Only remove chapter bodies for chapters that no longer exist in the new epub
        val newChapterUrls = epub.chapters
            .map { appFileResolver.getLocalBookChapterPath(storageFolderName, it.absPath) }
            .toSet()
        bookChapters.chapters(localBookUrl)
            .map { it.url }
            .filter { it !in newChapterUrls }
            .let { chapterBody.removeRows(it) }

        val coverImage = epub.coverImage
        if (coverImage != null) {
            fileImporter(
                targetFile = appFileResolver.getStorageBookCoverImageFile(storageFolderName),
                imageData = coverImage.image
            )
        }

        // Insert or update book, restoring preserved progress fields
        Book(
            title = storageFolderName,
            url = localBookUrl,
            coverImageUrl = appFileResolver.getLocalBookCoverPath(),
            inLibrary = existingBook?.inLibrary ?: addToLibrary,
            lastReadChapter = existingBook?.lastReadChapter,
            lastReadEpochTimeMilli = existingBook?.lastReadEpochTimeMilli ?: 0,
        ).let { libraryBooks.insertReplace(listOf(it)) }

        // Merge new chapters, preserving read progress for existing ones
        epub.chapters.mapIndexed { i, chapter ->
            Chapter(
                title = chapter.title,
                url = appFileResolver.getLocalBookChapterPath(storageFolderName, chapter.absPath),
                bookUrl = localBookUrl,
                position = i
            )
        }.let { bookChapters.merge(it, localBookUrl) }

        epub.chapters.map { chapter ->
            ChapterBody(
                url = appFileResolver.getLocalBookChapterPath(storageFolderName, chapter.absPath),
                body = chapter.body
            )
        }.let { chapterBody.insertReplace(it) }

        epub.images.map {
            async {
                fileImporter(
                    targetFile = appFileResolver.getStorageBookImageFile(
                        storageFolderName,
                        it.absPath
                    ),
                    imageData = it.image
                )
            }
        }.awaitAll()
        return@withContext localBookUrl
    }
}