package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.AiChatMessage

@Dao
interface AiChatMessageDao {

    @Query("SELECT * FROM AiChatMessage WHERE bookUrl = :bookUrl ORDER BY id")
    fun getForBookFlow(bookUrl: String): Flow<List<AiChatMessage>>

    @Query("SELECT * FROM AiChatMessage WHERE bookUrl = :bookUrl ORDER BY id")
    suspend fun getForBook(bookUrl: String): List<AiChatMessage>

    @Insert
    suspend fun insert(messages: List<AiChatMessage>)

    @Query("DELETE FROM AiChatMessage WHERE bookUrl = :bookUrl")
    suspend fun removeForBook(bookUrl: String)

    @Query("DELETE FROM AiChatMessage WHERE bookUrl NOT IN (SELECT Book.url FROM Book WHERE Book.inLibrary = 1)")
    suspend fun removeAllNonLibraryRows()
}
