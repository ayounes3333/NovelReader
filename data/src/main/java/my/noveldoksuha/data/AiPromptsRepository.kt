package my.noveldoksuha.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.AiPromptDao
import my.noveldokusha.feature.local_database.tables.AiPrompt
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import my.noveldokusha.tooling.ai_engine.domain.DefaultPrompts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRUD for prompt templates. Built-in defaults are seeded idempotently on first
 * access so users can inspect, edit and reset them.
 */
@Singleton
class AiPromptsRepository @Inject constructor(
    private val aiPromptDao: AiPromptDao,
    private val appDatabase: AppDatabase,
) {
    private val seedingMutex = Mutex()

    suspend fun getAll(): List<AiPrompt> {
        ensureBuiltInsSeeded()
        return aiPromptDao.getAll()
    }

    suspend fun get(id: Long): AiPrompt? = aiPromptDao.get(id)

    /**
     * Returns the prompt configured for [task] (user override or built-in),
     * or null when the task has no template stored.
     */
    suspend fun getForTask(task: AiTask): AiPrompt? = aiPromptDao.getForTask(task.key, task.metaKey)

    suspend fun create(prompt: AiPrompt): Long = aiPromptDao.insert(prompt)

    suspend fun update(prompt: AiPrompt) = aiPromptDao.update(prompt)

    suspend fun removeUserPrompt(id: Long) = aiPromptDao.removeUserPrompt(id)

    /** Deletes built-in rows and re-seeds them from defaults. */
    suspend fun resetBuiltIns() = seedingMutex.withLock {
        appDatabase.transaction {
            aiPromptDao.removeBuiltIns()
            seedBuiltIns()
        }
    }

    suspend fun ensureBuiltInsSeeded() = seedingMutex.withLock {
        if (aiPromptDao.countBuiltIn() == 0) {
            appDatabase.transaction { seedBuiltIns() }
        }
    }

    private suspend fun seedBuiltIns() {
        val builtIns = listOf(
            AiTask.CreateTitle,
            AiTask.CreateSummary,
            AiTask.FixNarration,
            AiTask.FixGrammar,
        ).map { task ->
            AiPrompt(
                name = builtInName(task),
                systemPrompt = DefaultPrompts.systemPromptFor(task),
                taskType = task.key,
                meta = task.metaKey,
                isBuiltIn = true,
            )
        } + AiPrompt(
            name = builtInName(AiTask.Translate(targetLanguage = "")),
            systemPrompt = DefaultPrompts.systemPromptFor(
                AiTask.Translate(targetLanguage = my.noveldokusha.tooling.ai_engine.domain.PromptTemplate.VAR_LANGUAGE)
            ),
            taskType = AiTask.Translate("").key,
            meta = "",
            isBuiltIn = true,
        )
        aiPromptDao.insertAll(builtIns)
    }

    private fun builtInName(task: AiTask): String = when (task) {
        is AiTask.CreateTitle -> "Built-in: Create title"
        is AiTask.CreateSummary -> "Built-in: Create summary"
        is AiTask.Translate -> "Built-in: Translation"
        is AiTask.FixNarration -> "Built-in: Fix narration"
        is AiTask.FixGrammar -> "Built-in: Fix grammar"
        is AiTask.Custom -> "Built-in: Custom"
    }
}
