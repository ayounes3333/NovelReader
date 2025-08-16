package my.noveldokusha.tooling.firebase_sync.repository

import my.noveldokusha.tooling.firebase_sync.data.*
import java.io.File

interface LocalLibraryRepository {
    // Existing library operations
    suspend fun getAllBooks(): List<LibraryBook>
    suspend fun getBooksSince(timestamp: Long): List<LibraryBook>
    suspend fun updateBooks(books: List<LibraryBook>)
    suspend fun getUserLibrary(userId: String): UserLibrary

    // Chapter operations
    suspend fun getAllChapters(): List<ChapterSync>
    suspend fun getChaptersSince(timestamp: Long): List<ChapterSync>
    suspend fun getChaptersForBook(bookUrl: String): List<ChapterSync>
    suspend fun updateChapters(chapters: List<ChapterSync>)
    suspend fun getUserChapters(userId: String): UserChapters

    // ChapterBody operations
    suspend fun getAllChapterBodies(): List<ChapterBodySync>
    suspend fun getChapterBodiesSince(timestamp: Long): List<ChapterBodySync>
    suspend fun getChapterBodiesForBook(bookUrl: String): List<ChapterBodySync>
    suspend fun updateChapterBodies(chapterBodies: List<ChapterBodySync>)
    suspend fun getUserChapterBodies(userId: String): UserChapterBodies

    // Image operations
    suspend fun getAllImages(): List<ImageSync>
    suspend fun getImagesSince(timestamp: Long): List<ImageSync>
    suspend fun getImagesForBook(bookUrl: String): List<ImageSync>
    suspend fun updateImages(images: List<ImageSync>)
    suspend fun getUserImages(userId: String): UserImages
    suspend fun getImageFile(imagePath: String): File?
    suspend fun saveImageFile(imagePath: String, data: ByteArray): Boolean
    suspend fun calculateImageChecksum(file: File): String
    suspend fun getImagesByPriority(priority: ImagePriority): List<ImageSync>
}
