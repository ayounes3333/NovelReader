package my.noveldoksuha.data

import my.noveldokusha.core.Response
import my.noveldokusha.core.getOrNull
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.ChapterAiRevision
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.tooling.ai_engine.domain.AiModelInfo
import my.noveldokusha.tooling.ai_engine.domain.AiProvider
import my.noveldokusha.tooling.ai_engine.domain.AiRequest
import my.noveldokusha.tooling.ai_engine.domain.AiResult
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import my.noveldokusha.tooling.ai_engine.domain.AiUsage
import my.noveldokusha.tooling.ai_engine.domain.AiProviderConfig
import my.noveldokusha.tooling.ai_engine.domain.DefaultPrompts
import my.noveldokusha.tooling.ai_engine.domain.PromptTemplate
import my.noveldokusha.tooling.ai_engine.engine.AiChunker
import my.noveldokusha.tooling.ai_engine.engine.AiSecretsStore
import my.noveldokusha.tooling.ai_engine.engine.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates single-chapter AI tasks: loads the body, builds the prompt,
 * chunks when needed, calls the provider and persists the result as a
 * non-destructive `ChapterAiRevision`.
 */
@Singleton
class AiRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val chapterBodyRepository: ChapterBodyRepository,
    private val bookChaptersRepository: BookChaptersRepository,
    private val aiPromptsRepository: AiPromptsRepository,
    private val providerRegistry: ProviderRegistry,
    private val secrets: AiSecretsStore,
    private val appPreferences: AppPreferences,
    private val usageStore: AiUsageStore,
) {

    private val revisionDao get() = appDatabase.chapterAiRevisionDao()

    /** Resolved provider config for the currently selected provider, or null. */
    fun currentConfig(): AiProviderConfig? {
        val providerId = appPreferences.AI_PROVIDER_ID.value
        if (providerId.isEmpty()) return null
        return providerRegistry.resolve(providerId, appPreferences.AI_PROVIDER_BASE_URL.value)
    }

    /** True when a provider is selected, configured and has an API key stored. */
    fun isConfigured(): Boolean {
        val config = currentConfig() ?: return false
        return !config.requiresApiKey || secrets.has(config.id)
    }

    suspend fun listModels(): Response<List<AiModelInfo>> {
        val config = currentConfig()
            ?: return Response.Error("No AI provider configured", IllegalStateException("no provider"))
        return providerRegistry.create(config).listModels()
    }

    /**
     * Resolves the model for [task]: explicit override > per-task preference >
     * default model > provider suggested model.
     */
    fun resolveModel(task: AiTask?): String {
        task?.let {
            appPreferences.AI_MODEL_PER_TASK.value[it.key]?.takeIf { m -> m.isNotBlank() }
                ?.let { m -> return m }
        }
        appPreferences.AI_MODEL_DEFAULT.value.takeIf { it.isNotBlank() }?.let { return it }
        task ?: return currentConfig()?.suggestedModel.orEmpty()
        return currentConfig()?.suggestedModel.orEmpty()
    }

    /**
     * Runs [task] over the chapter at [chapterUrl] and stores the result as the
     * active revision for that task. Originals are never modified.
     *
     * @param model explicit model override; falls back to preferences.
     * @param promptOverride explicit system prompt; falls back to stored template.
     * @param onChunkProgress reports (chunksDone, chunksTotal) while a long
     * chapter is processed in sequential chunks; not invoked for tasks that
     * use whole-document sampling (title/summary).
     */
    suspend fun runTask(
        chapterUrl: String,
        task: AiTask,
        model: String? = null,
        promptOverride: String? = null,
        onChunkProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Response<ChapterAiRevision> {
        if (!isConfigured()) {
            return Response.Error(
                "AI provider is not configured. Set it up in Settings.",
                IllegalStateException("provider not configured")
            )
        }
        val config = currentConfig()!!
        val provider = providerRegistry.create(config)
        val usedModel = model?.takeIf { it.isNotBlank() } ?: resolveModel(task)
        if (usedModel.isBlank()) {
            return Response.Error(
                "No model selected. Choose one in the AI settings.",
                IllegalStateException("no model")
            )
        }

        val body = chapterBodyRepository.fetchBody(chapterUrl, tryCache = true)
        val content = when (body) {
            is Response.Success -> body.data
            is Response.Error -> return Response.Error(body.message, body.exception)
        }

        val systemPrompt = promptOverride ?: resolveSystemPrompt(task)
        if (systemPrompt.isBlank()) {
            return Response.Error(
                "No prompt configured for this task.",
                IllegalStateException("empty prompt")
            )
        }

        val variables = mapOf(
            PromptTemplate.VAR_LANGUAGE to (task as? AiTask.Translate)?.targetLanguage.orEmpty()
        )

        val result = when (task) {
            // Whole-document tasks use head+tail sampling instead of chunking.
            is AiTask.CreateTitle -> provider.complete(
                AiRequest(
                    model = usedModel,
                    systemPrompt = PromptTemplate.render(
                        systemPrompt,
                        variables + (PromptTemplate.VAR_CONTENT to AiChunker.sampleHeadAndTail(content))
                    ),
                    userContent = STANDARD_USER_MESSAGE,
                    temperature = 0.7f,
                    maxTokens = 64,
                )
            )

            is AiTask.CreateSummary -> provider.complete(
                AiRequest(
                    model = usedModel,
                    systemPrompt = PromptTemplate.render(
                        systemPrompt,
                        variables + (PromptTemplate.VAR_CONTENT to AiChunker.sampleHeadAndTail(content))
                    ),
                    userContent = STANDARD_USER_MESSAGE,
                    temperature = 0.5f,
                )
            )

            // Body-rewriting tasks process long chapters in sequential chunks.
            is AiTask.Translate, is AiTask.FixNarration, is AiTask.FixGrammar, is AiTask.Custom -> {
                runChunked(provider, usedModel, systemPrompt, variables, content, onChunkProgress)
            }
        }

        return when (result) {
            is Response.Error -> result
            is Response.Success -> {
                usageStore.add(result.data.usage)
                val revision = ChapterAiRevision(
                    chapterUrl = chapterUrl,
                    taskType = task.key,
                    meta = task.metaKey,
                    content = result.data.text.trim(),
                    model = result.data.model,
                    providerId = config.id,
                    active = true,
                )
                revisionDao.insertAsActive(revision)
                Response.Success(revision)
            }
        }
    }

    private suspend fun runChunked(
        provider: AiProvider,
        model: String,
        systemPrompt: String,
        variables: Map<String, String>,
        content: String,
        onChunkProgress: suspend (done: Int, total: Int) -> Unit,
    ): Response<AiResult> {
        val chunks = AiChunker.split(content)
        val output = StringBuilder()
        var totalUsage: AiUsage? = null
        var lastModel = model

        for ((index, chunk) in chunks.withIndex()) {
            onChunkProgress(index, chunks.size)
            val request = AiRequest(
                model = model,
                systemPrompt = PromptTemplate.render(
                    systemPrompt,
                    variables + (PromptTemplate.VAR_CONTENT to chunk)
                ),
                userContent = STANDARD_USER_MESSAGE,
                temperature = 0.4f,
            )
            val response = provider.complete(request)
            if (response is Response.Error) return response

            val data = (response as Response.Success).data
            if (index > 0) output.append("\n\n")
            output.append(data.text.trim())
            lastModel = data.model
            data.usage?.let { usage ->
                totalUsage = if (totalUsage == null) usage
                else totalUsage!!.copy(
                    promptTokens = totalUsage!!.promptTokens + usage.promptTokens,
                    completionTokens = totalUsage!!.completionTokens + usage.completionTokens,
                    totalTokens = totalUsage!!.totalTokens + usage.totalTokens,
                )
            }
        }

        return Response.Success(AiResult(text = output.toString(), model = lastModel, usage = totalUsage))
    }

    /** Active revision for the task, if any. */
    suspend fun getActiveRevision(chapterUrl: String, task: AiTask): ChapterAiRevision? =
        revisionDao.getActive(chapterUrl, task.key, task.metaKey)

    /**
     * Content of the active body revision for the chapter (grammar > narration
     * > translation precedence), or null when there is none.
     */
    suspend fun activeRevisionBodyOrNull(chapterUrl: String): String? {
        for (task in listOf(AiTask.FixGrammar, AiTask.FixNarration)) {
            revisionDao.getActive(chapterUrl, task.key, task.metaKey)?.let { return it.content }
        }
        val target = translationTarget()
        if (target.isNotBlank()) {
            revisionDao.getActive(chapterUrl, AiTask.Translate(target).key, target)
                ?.let { return it.content }
        }
        return null
    }

    /**
     * Stored summary for the chapter, if any.
     */
    suspend fun getActiveSummary(chapterUrl: String): String? =
        revisionDao.getActive(chapterUrl, AiTask.CreateSummary.key, AiTask.CreateSummary.metaKey)
            ?.content

    /**
     * System prompt for [task]: stored template (by id for custom tasks, by
     * task kind otherwise) or the built-in default.
     */
    suspend fun resolveSystemPrompt(task: AiTask): String = when (task) {
        is AiTask.Custom ->
            aiPromptsRepository.get(task.promptId)?.systemPrompt
                .orEmpty()

        else ->
            aiPromptsRepository.getForTask(task)?.systemPrompt
                ?: DefaultPrompts.systemPromptFor(task)
    }

    /** User-defined prompts available as custom tasks. */
    suspend fun getCustomPrompts(): List<my.noveldokusha.feature.local_database.tables.AiPrompt> =
        aiPromptsRepository.getAll().filter { !it.isBuiltIn && it.taskType == AiTask.Custom(0).key }

    /** Chapter urls from [chaptersUrl] that have any active AI revision. */
    suspend fun getChaptersWithActiveRevision(chaptersUrl: List<String>): List<String> =
        chaptersUrl.chunked(500).flatMap { revisionDao.getActiveChapterUrls(it) }

    /**
     * Body the reader should display: the active rewriting/translation revision
     * when present, otherwise the original stored body.
     */
    suspend fun resolveReaderBody(chapterUrl: String): String? {
        activeRevisionBodyOrNull(chapterUrl)?.let { return it }
        return chapterBodyRepository.fetchBody(chapterUrl).getOrNull()
    }

    /** Sets the active state of a stored revision (restore original, etc.). */
    suspend fun setActiveRevision(revision: ChapterAiRevision, active: Boolean) {
        if (active) revisionDao.insertAsActive(revision.copy(id = 0))
        else revisionDao.remove(revision.id)
    }

    /** Target language used for AI translation; falls back to the live-translation preference. */
    fun translationTarget(): String =
        appPreferences.AI_TRANSLATION_TARGET.value.ifBlank {
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        }

    /** Stored generated title for the chapter, if any. */
    suspend fun getActiveTitle(chapterUrl: String): String? =
        revisionDao.getActive(chapterUrl, AiTask.CreateTitle.key, AiTask.CreateTitle.metaKey)
            ?.content

    /** Local token usage accounting for the last [lastDays] days. */
    fun usageSummary(lastDays: Int = 30): AiUsageSummary = usageStore.summary(lastDays)

    /**
     * Applies the stored generated title for [chapterUrl] as the chapter's
     * actual title. The only destructive AI operation, and always explicit.
     */
    suspend fun applyActiveTitle(chapterUrl: String): String? {
        val revision = revisionDao.getActive(chapterUrl, AiTask.CreateTitle.key, AiTask.CreateTitle.metaKey)
            ?: return null
        bookChaptersRepository.updateTitle(url = chapterUrl, title = revision.content)
        revisionDao.remove(revision.id)
        return revision.content
    }

    private companion object {
        /**
         * Chapter content is embedded into the rendered system prompt
         * (`{{content}}`); the user message is just a trigger.
         */
        const val STANDARD_USER_MESSAGE = "Perform the task described above on the chapter content."
    }
}
