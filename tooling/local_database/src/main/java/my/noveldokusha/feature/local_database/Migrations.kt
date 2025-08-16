package my.noveldokusha.feature.local_database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import my.noveldokusha.feature.local_database.migrations.MigrationsList
import my.noveldokusha.feature.local_database.migrations._1stKissNovelDomainChange_1_org
import my.noveldokusha.feature.local_database.migrations.readLightNovelDomainChange_1_today
import my.noveldokusha.feature.local_database.migrations.readLightNovelDomainChange_2_meme

internal fun databaseMigrations() = arrayOf(
    migration(1) {
        it.execSQL("ALTER TABLE Chapter ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
    },
    migration(2) {
        it.execSQL("ALTER TABLE Book ADD COLUMN inLibrary INTEGER NOT NULL DEFAULT 0")
        it.execSQL("UPDATE Book SET inLibrary = 1")
    },
    migration(3) {
        it.execSQL("ALTER TABLE Book ADD COLUMN coverImageUrl TEXT NOT NULL DEFAULT ''")
        it.execSQL("ALTER TABLE Book ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    },
    migration(4) {
        it.execSQL("ALTER TABLE Book ADD COLUMN lastReadEpochTimeMilli INTEGER NOT NULL DEFAULT 0")
    },
    migration(5, MigrationsList::readLightNovelDomainChange_1_today),
    migration(6, MigrationsList::readLightNovelDomainChange_2_meme),
    migration(7, MigrationsList::_1stKissNovelDomainChange_1_org),
    migration(8) {
        // Add timestamp tracking to Chapter table
        it.execSQL("ALTER TABLE Chapter ADD COLUMN createdEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
        it.execSQL("ALTER TABLE Chapter ADD COLUMN lastUpdatedEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

        // Add timestamp tracking to ChapterBody table
        it.execSQL("ALTER TABLE ChapterBody ADD COLUMN createdEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
        it.execSQL("ALTER TABLE ChapterBody ADD COLUMN lastUpdatedEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
    },
    migration(6, 9) {
        MigrationsList.readLightNovelDomainChange_2_meme(it)
        MigrationsList._1stKissNovelDomainChange_1_org(it)
        // Add timestamp tracking to Chapter table
        it.execSQL("ALTER TABLE Chapter ADD COLUMN createdEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
        it.execSQL("ALTER TABLE Chapter ADD COLUMN lastUpdatedEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

        // Add timestamp tracking to ChapterBody table
        it.execSQL("ALTER TABLE ChapterBody ADD COLUMN createdEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
        it.execSQL("ALTER TABLE ChapterBody ADD COLUMN lastUpdatedEpochTimeMilli INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
    }
)

internal fun migration(vi: Int, migrate: (SupportSQLiteDatabase) -> Unit) =
    object : Migration(vi, vi + 1) {
        override fun migrate(db: SupportSQLiteDatabase) = migrate(db)
    }

internal fun migration(start: Int, end: Int, migrate: (SupportSQLiteDatabase) -> Unit) =
    object : Migration(start, end + 1) {
        override fun migrate(db: SupportSQLiteDatabase) = migrate(db)
    }