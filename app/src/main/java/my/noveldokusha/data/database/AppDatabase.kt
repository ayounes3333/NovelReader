package my.noveldokusha.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import my.noveldokusha.data.database.DAOs.ChapterBodyDao
import my.noveldokusha.data.database.DAOs.ChapterDao
import my.noveldokusha.data.database.DAOs.LibraryDao
import my.noveldokusha.data.database.tables.Book
import my.noveldokusha.data.database.tables.Chapter
import my.noveldokusha.data.database.tables.ChapterBody
import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo
import my.noveldokusha.ui.browse.model.NovelFileDao
import java.io.InputStream


interface AppDatabaseOperations {
    /**
     * Execute the whole database calls as an atomic operation
     */
    suspend fun <T> transaction(block: suspend () -> T): T
}

@Database(
    entities = [
        Book::class,
        Chapter::class,
        ChapterBody::class,
        NovelFileInfo::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase(), AppDatabaseOperations {
    abstract fun libraryDao(): LibraryDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterBodyDao(): ChapterBodyDao
    abstract fun novelFilesDao(): NovelFileDao

    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)

    companion object {
        fun createRoom(ctx: Context, name: String) = Room
            .databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*databaseMigrations())
            .build()

        fun createRoomFromStream(ctx: Context, name: String, inputStream: InputStream) = Room
            .databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*databaseMigrations())
            .createFromInputStream { inputStream }
            .build()
    }
}
