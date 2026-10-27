package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import my.noveldokusha.feature.local_database.tables.ChapterAiRevision

@Dao
interface ChapterAiRevisionDao {

    @Query(
        """
        SELECT * FROM ChapterAiRevision
        WHERE chapterUrl = :chapterUrl AND taskType = :taskType AND meta = :meta AND active = 1
        LIMIT 1
        """
    )
    suspend fun getActive(chapterUrl: String, taskType: String, meta: String): ChapterAiRevision?

    @Query(
        """
        SELECT COUNT(*) FROM ChapterAiRevision
        WHERE chapterUrl = :chapterUrl AND taskType = :taskType AND meta = :meta AND active = 1
        """
    )
    suspend fun countActive(chapterUrl: String, taskType: String, meta: String): Int

    @Query(
        """
        SELECT chapterUrl FROM ChapterAiRevision
        WHERE taskType = :taskType AND meta = :meta AND active = 1
        AND chapterUrl IN (:chaptersUrl)
        """
    )
    suspend fun getChaptersWithActive(
        chaptersUrl: List<String>,
        taskType: String,
        meta: String
    ): List<String>

    @Query(
        """
        SELECT DISTINCT chapterUrl FROM ChapterAiRevision
        WHERE active = 1 AND chapterUrl IN (:chaptersUrl)
        """
    )
    suspend fun getActiveChapterUrls(chaptersUrl: List<String>): List<String>

    @Query(
        """
        SELECT * FROM ChapterAiRevision
        WHERE chapterUrl = :chapterUrl
        ORDER BY createdEpochTimeMilli DESC
        """
    )
    suspend fun getAllForChapter(chapterUrl: String): List<ChapterAiRevision>

    /**
     * Atomically replaces the active revision for (chapterUrl, taskType, meta):
     * deactivates the previous one and inserts the new revision as active.
     * Only one active revision per (chapter, task) kind is guaranteed because
     * all writes go through this transaction.
     */
    @Transaction
    suspend fun insertAsActive(revision: ChapterAiRevision): Long {
        deactivate(revision.chapterUrl, revision.taskType, revision.meta)
        return insert(revision.copy(active = true))
    }

    @Query("DELETE FROM ChapterAiRevision WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT * FROM ChapterAiRevision ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun getBatch(offset: Int, limit: Int): List<ChapterAiRevision>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertReplace(revisions: List<ChapterAiRevision>)

    @Query("DELETE FROM ChapterAiRevision WHERE chapterUrl IN (:chaptersUrl)")
    suspend fun removeChapterRows(chaptersUrl: List<String>)

    @Query("DELETE FROM ChapterAiRevision WHERE chapterUrl NOT IN (SELECT Chapter.url FROM Chapter)")
    suspend fun removeAllNonChapterRows()

    @Insert
    suspend fun insert(revision: ChapterAiRevision): Long

    @Query(
        """
        UPDATE ChapterAiRevision SET active = 0
        WHERE chapterUrl = :chapterUrl AND taskType = :taskType AND meta = :meta AND active = 1
        """
    )
    suspend fun deactivate(chapterUrl: String, taskType: String, meta: String)
}
