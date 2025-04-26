package my.noveldokusha.feature.local_database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterDao
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.NovelFileDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo
import java.io.File
import java.io.InputStream


interface AppDatabase {
    fun libraryDao(): LibraryDao
    fun chapterDao(): ChapterDao
    fun chapterBodyDao(): ChapterBodyDao
    fun novelFilesDao(): NovelFileDao
    val name: String

    fun closeDatabase()
    fun clearDatabase()
    suspend fun vacuum()

    /**
     * Execute the whole database calls as an atomic operation
     */
    suspend fun <T> transaction(block: suspend () -> T): T

    companion object {
        fun createRoom(ctx: Context, name: String): AppDatabase = Room
            .databaseBuilder(ctx, AppRoomDatabase::class.java, name)
            .addMigrations(*databaseMigrations())
            .build()
            .also { it.name = name }

        fun createRoomFromStream(
            ctx: Context,
            name: String,
            inputStream: InputStream
        ): AppDatabase = Room
            .databaseBuilder(ctx, AppRoomDatabase::class.java, name)
            .addMigrations(*databaseMigrations())
            .createFromInputStream { inputStream }
            .build()
            .also { it.name = name }
        fun createRoomFromFile(context: Context, dbName: String, sourceFile: File): AppDatabase {
            val target = context.getDatabasePath(dbName)

            // Clear any existing database files
            deleteDatabaseFiles(context, dbName)

            // Copy the backup database file
            sourceFile.copyTo(target, overwrite = true)

            // Open the database connection
            return Room.databaseBuilder(
                context,
                AppRoomDatabase::class.java,
                dbName
            ).build()
        }

        fun deleteDatabaseFiles(context: Context, dbName: String) {
            arrayOf(
                context.getDatabasePath(dbName),
                context.getDatabasePath("$dbName-wal"),
                context.getDatabasePath("$dbName-shm")
            ).forEach { file ->
                if (file.exists()) file.delete()
            }
        }
    }
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
internal abstract class AppRoomDatabase : RoomDatabase(), AppDatabase {
    abstract override fun libraryDao(): LibraryDao
    abstract override fun chapterDao(): ChapterDao
    abstract override fun chapterBodyDao(): ChapterBodyDao
    abstract override fun novelFilesDao(): NovelFileDao

    override lateinit var name: String

    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)

    override fun closeDatabase() {
        close()
    }

    override fun clearDatabase() {
        clearAllTables()
    }

    override suspend fun vacuum() {
        withContext(Dispatchers.IO) { openHelper.writableDatabase.execSQL("VACUUM") }
    }
}
