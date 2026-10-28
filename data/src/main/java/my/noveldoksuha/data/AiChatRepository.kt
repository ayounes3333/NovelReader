package my.noveldoksuha.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.tooling.ai_engine.domain.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRole
import my.noveldokusha.tooling.ai_engine.domain.AiToolCall
import my.noveldokusha.tooling.ai_engine.engine.AiAgentEvent
import my.noveldokusha.tooling.ai_engine.engine.AiAgentLoop
import my.noveldokusha.tooling.ai_engine.engine.StepLimitDecision
import my.noveldokusha.tooling.ai_engine.engine.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton
import my.noveldokusha.feature.local_database.tables.AiChatMessage as AiChatMessageRow

/**
 * Novel-aware AI assistant: a persisted per-book conversation where the model
 * can read chapters, search the novel text and the web through tools.
 */
@Singleton
class AiChatRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val libraryBooksRepository: LibraryBooksRepository,
    private val bookChaptersRepository: BookChaptersRepository,
    private val chapterBodyRepository: ChapterBodyRepository,
    private val aiRepository: AiRepository,
    private val providerRegistry: ProviderRegistry,
    private val webSearchClient: WebSearchClient,
    private val appPreferences: AppPreferences,
    private val usageStore: AiUsageStore,
) {
    private val dao get() = appDatabase.aiChatMessageDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val agentLoop = AiAgentLoop()

    fun isConfigured(): Boolean = aiRepository.isConfigured()

    fun messagesFlow(bookUrl: String): Flow<List<AiChatMessageRow>> = dao.getForBookFlow(bookUrl)

    suspend fun clear(bookUrl: String) = dao.removeForBook(bookUrl)

    /** Loads what the assistant needs to know about the book and reading position. */
    suspend fun loadContext(bookUrl: String, currentChapterUrl: String?): AiChatContext {
        val book = libraryBooksRepository.get(bookUrl)
        val chapters = bookChaptersRepository.chapters(bookUrl)
        val resolvedUrl = currentChapterUrl?.takeIf { it.isNotBlank() } ?: book?.lastReadChapter
        val index = chapters.indexOfFirst { it.url == resolvedUrl }.let { if (it < 0) 0 else it }
        return AiChatContext(
            book = book,
            bookUrl = bookUrl,
            chapters = chapters,
            currentChapterIndex = index,
        )
    }

    /**
     * Appends the user's [text] to the book's conversation, runs the agent and
     * persists every produced message (tool requests, results and the answer).
     */
    suspend fun send(
        context: AiChatContext,
        text: String,
        onEvent: suspend (AiAgentEvent) -> Unit = {},
        onStepLimit: suspend (stepsUsed: Int) -> StepLimitDecision = { StepLimitDecision.ANSWER_NOW },
    ): Response<String> {
        if (!isConfigured()) {
            return Response.Error(
                "AI provider is not configured. Set it up in Settings.",
                IllegalStateException("provider not configured")
            )
        }
        val config = aiRepository.currentConfig()!!
        val model = aiRepository.resolveModel(null)
        if (model.isBlank()) {
            return Response.Error(
                "No model selected. Choose one in the AI settings.",
                IllegalStateException("no model")
            )
        }

        val userMessage = AiChatMessage.user(text.trim())
        dao.insert(listOf(userMessage.toRow(context.bookUrl)))

        val history = dao.getForBook(context.bookUrl).map { it.toDomain() }.compactForPrompt()
        val tools = AiChatTools(
            context = context,
            chapterBodyRepository = chapterBodyRepository,
            appDatabase = appDatabase,
            webSearchClient = webSearchClient,
            appPreferences = appPreferences,
        ).all()

        val result = agentLoop.run(
            provider = providerRegistry.create(config),
            model = model,
            systemPrompt = systemPrompt(context),
            history = history,
            tools = tools,
            maxSteps = appPreferences.AI_CHAT_MAX_STEPS.value,
            onEvent = onEvent,
            onStepLimit = onStepLimit,
            // Persist as we go so tool activity stays visible even if the run fails.
            onMessage = { dao.insert(listOf(it.toRow(context.bookUrl))) },
        )

        return when (result) {
            is Response.Error -> result
            is Response.Success -> {
                usageStore.add(result.data.usage)
                Response.Success(result.data.finalAnswer)
            }
        }
    }

    private fun systemPrompt(context: AiChatContext): String = buildString {
        appendLine("You are a knowledgeable, spoiler-aware reading companion inside a novel reader app.")
        appendLine()
        appendLine("## Novel")
        appendLine("Title: ${context.book?.title ?: "(unknown)"}")
        appendLine("Chapters available: ${context.chapters.size}")
        context.currentChapter?.let {
            appendLine("Reader is currently on chapter ${context.currentChapterIndex + 1}: ${it.title}")
        }
        context.book?.description?.takeIf { it.isNotBlank() }?.let {
            appendLine("Description: ${it.take(1_200)}")
        }
        appendLine()
        appendLine("## Guidelines")
        appendLine("- You do NOT know the chapter text until you read it: call `read_chapter` before answering questions about events, characters or wording. Use offset 0 for the current chapter, -1/1 for previous/next.")
        appendLine("- For questions spanning several chapters, read them one by one, or use `search_novel_text` to locate mentions first.")
        appendLine("- Avoid spoilers from chapters after the reader's current one unless the user explicitly asks about upcoming content; when you do reveal, warn first.")
        appendLine("- Use `web_search` / `fetch_web_page` only for information outside the text (author, releases, wikis, real-world terms).")
        appendLine("- Be concise, answer in the user's language, and quote short passages when helpful. Say so when the text does not contain the answer.")
    }

    /**
     * Keeps the prompt bounded: only the most recent messages are sent and
     * old tool results (chapter dumps, web pages) are shortened.
     */
    private fun List<AiChatMessage>.compactForPrompt(): List<AiChatMessage> {
        val recent = takeLast(MAX_HISTORY_MESSAGES)
        val lastUserIndex = recent.indexOfLast { it.role == AiChatRole.USER }
        return recent.mapIndexed { index, message ->
            if (message.role == AiChatRole.TOOL && index < lastUserIndex && message.content.length > OLD_TOOL_RESULT_CHARS) {
                message.copy(content = message.content.take(OLD_TOOL_RESULT_CHARS) + "\n[older tool result shortened]")
            } else message
        }.dropWhile { it.role == AiChatRole.TOOL } // never start with an orphan tool result
    }

    private fun AiChatMessage.toRow(bookUrl: String) = AiChatMessageRow(
        bookUrl = bookUrl,
        role = role.wire,
        content = content,
        toolCallsJson = toolCalls.takeIf { it.isNotEmpty() }?.let { calls ->
            buildJsonArray {
                calls.forEach { call ->
                    add(buildJsonObject {
                        put("id", call.id)
                        put("name", call.name)
                        put("arguments", call.argumentsJson)
                    })
                }
            }.toString()
        },
        toolCallId = toolCallId,
        toolName = toolName,
    )

    private fun AiChatMessageRow.toDomain() = AiChatMessage(
        role = AiChatRole.fromWire(role) ?: AiChatRole.USER,
        content = content,
        toolCalls = toolCallsJson?.let { raw ->
            runCatching {
                json.parseToJsonElement(raw).jsonArray.map { element ->
                    val obj = element.jsonObject
                    AiToolCall(
                        id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                        name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                        argumentsJson = obj["arguments"]?.jsonPrimitive?.content ?: "{}",
                    )
                }
            }.getOrDefault(emptyList())
        }.orEmpty(),
        toolCallId = toolCallId,
        toolName = toolName,
    )

    private companion object {
        const val MAX_HISTORY_MESSAGES = 40
        const val OLD_TOOL_RESULT_CHARS = 1_500
    }
}
