package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import my.noveldokusha.feature.local_database.tables.AiPrompt

@Dao
interface AiPromptDao {

    @Query("SELECT * FROM AiPrompt ORDER BY isBuiltIn DESC, name")
    suspend fun getAll(): List<AiPrompt>

    @Query("SELECT * FROM AiPrompt WHERE id = :id")
    suspend fun get(id: Long): AiPrompt?

    @Query(
        """
        SELECT * FROM AiPrompt
        WHERE taskType = :taskType AND meta = :meta
        ORDER BY isBuiltIn ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun getForTask(taskType: String, meta: String): AiPrompt?

    @Query("SELECT COUNT(*) FROM AiPrompt WHERE isBuiltIn = 1")
    suspend fun countBuiltIn(): Int

    @Insert
    suspend fun insert(prompt: AiPrompt): Long

    @Update
    suspend fun update(prompt: AiPrompt)

    @Query("DELETE FROM AiPrompt WHERE id = :id AND isBuiltIn = 0")
    suspend fun removeUserPrompt(id: Long)

    @Query("DELETE FROM AiPrompt WHERE isBuiltIn = 1")
    suspend fun removeBuiltIns()

    @Insert
    suspend fun insertAll(prompts: List<AiPrompt>)

    @Query("SELECT * FROM AiPrompt ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun getBatch(offset: Int, limit: Int): List<AiPrompt>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertReplace(prompts: List<AiPrompt>)
}
