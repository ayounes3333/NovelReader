package my.noveldoksuha.data

import my.noveldokusha.feature.local_database.DAOs.AiPromptDao
import my.noveldokusha.feature.local_database.DAOs.ChapterAiRevisionDao
import my.noveldokusha.feature.local_database.tables.AiPrompt
import my.noveldokusha.feature.local_database.tables.ChapterAiRevision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch access helpers for `ChapterAiRevision`, used by backup/restore.
 */
@Singleton
class ChapterAiRevisionRepository @Inject constructor(
    private val dao: ChapterAiRevisionDao,
) {
    suspend fun getBatch(offset: Int, limit: Int): List<ChapterAiRevision> =
        dao.getBatch(offset, limit)

    suspend fun insertReplace(revisions: List<ChapterAiRevision>) =
        dao.insertReplace(revisions)
}

/**
 * Batch access helpers for `AiPrompt`, used by backup/restore.
 */
@Singleton
class AiPromptsDaoRepository @Inject constructor(
    private val dao: AiPromptDao,
) {
    suspend fun getBatch(offset: Int, limit: Int): List<AiPrompt> =
        dao.getBatch(offset, limit)

    suspend fun insertReplace(prompts: List<AiPrompt>) =
        dao.insertReplace(prompts)
}
