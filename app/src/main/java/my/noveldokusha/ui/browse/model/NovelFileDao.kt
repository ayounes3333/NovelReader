package my.noveldokusha.ui.browse.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo

@Dao
interface NovelFileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(novelFileInfo: NovelFileInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(novelFileInfo: NovelFileInfo)

    @Query("SELECT * FROM " + NovelFileInfo.TABLE_NAME)
    suspend fun getAll(): List<NovelFileInfo>

    @Query("SELECT * FROM " + NovelFileInfo.TABLE_NAME + " WHERE directory = :directory")
    suspend fun getForDirectory(directory: String): List<NovelFileInfo>

    @Query("SELECT * FROM " + NovelFileInfo.TABLE_NAME + " WHERE id = :id")
    suspend fun getNovelFileInfo(id: String): NovelFileInfo?

    @Query("DELETE FROM " + NovelFileInfo.TABLE_NAME + " WHERE id = :id")
    suspend fun reset(id: String)

    @Query("DELETE FROM " + NovelFileInfo.TABLE_NAME)
    suspend fun deleteAll()
}