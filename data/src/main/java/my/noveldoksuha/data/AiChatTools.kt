package my.noveldoksuha.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.tooling.ai_engine.domain.AiToolSpec
import my.noveldokusha.tooling.ai_engine.engine.AiTool
import my.noveldokusha.tooling.ai_engine.engine.TextToolProtocol

/**
 * Snapshot of what the assistant is talking about: the book and the chapter
 * the reader is currently on. Tools resolve relative chapter offsets from it.
 */
data class AiChatContext(
    val book: Book?,
    val bookUrl: String,
    val chapters: List<Chapter>,
    val currentChapterIndex: Int,
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
}

/**
 * Builds the tool set available to the novel assistant for one conversation.
 */
internal class AiChatTools(
    private val context: AiChatContext,
    private val chapterBodyRepository: ChapterBodyRepository,
    private val appDatabase: AppDatabase,
    private val webSearchClient: WebSearchClient,
    private val appPreferences: AppPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<AiTool> = buildList {
        add(readChapter)
        add(listChapters)
        add(novelInfo)
        add(searchNovelText)
        if (appPreferences.AI_CHAT_WEB_SEARCH_ENABLED.value) {
            add(webSearch)
            add(fetchWebPage)
        }
    }

    private fun args(raw: String) = runCatching { json.parseToJsonElement(raw).jsonObject }
        .getOrDefault(buildJsonObject { })

    private val readChapter = object : AiTool {
        override val spec = AiToolSpec(
            name = "read_chapter",
            description = "Returns the text of a chapter of this novel. Use offset=0 for the chapter " +
                    "the reader is on, -1 for the previous and 1 for the next; or pass number " +
                    "(1-based chapter number). Long chapters are split in parts: request part=2, 3... " +
                    "when the result says more parts exist.",
            parameters = TextToolProtocol.schema {
                putJsonObject("properties") {
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Chapter relative to the current one (0 current, -1 previous, 1 next)")
                    }
                    putJsonObject("number") {
                        put("type", "integer")
                        put("description", "Absolute 1-based chapter number; overrides offset")
                    }
                    putJsonObject("part") {
                        put("type", "integer")
                        put("description", "Part of a long chapter to return, starting at 1")
                    }
                }
            }
        )

        override suspend fun execute(argumentsJson: String): String {
            val a = args(argumentsJson)
            val number = a["number"]?.jsonPrimitive?.intOrNull
            val offset = a["offset"]?.jsonPrimitive?.intOrNull ?: 0
            val part = (a["part"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)

            val index = if (number != null) number - 1 else context.currentChapterIndex + offset
            val chapter = context.chapters.getOrNull(index)
                ?: return "Error: chapter does not exist (novel has ${context.chapters.size} chapters, " +
                        "reader is on chapter ${context.currentChapterIndex + 1})."

            val body = when (val r = chapterBodyRepository.fetchBody(chapter.url, tryCache = true)) {
                is Response.Success -> r.data
                is Response.Error -> return "Error: could not load chapter ${index + 1}: ${r.message.lineSequence().first()}"
            }
            val parts = body.chunked(PART_CHARS)
            val chosen = parts.getOrNull(part - 1)
                ?: return "Error: chapter ${index + 1} has only ${parts.size} part(s)."
            return buildString {
                appendLine("Chapter ${index + 1} of ${context.chapters.size}: ${chapter.title}")
                if (parts.size > 1) {
                    appendLine(
                        if (part < parts.size) "Part $part of ${parts.size}. Request part=${part + 1} for more."
                        else "Part $part of ${parts.size} (last)."
                    )
                }
                appendLine()
                append(chosen)
            }
        }
    }

    private val listChapters = object : AiTool {
        override val spec = AiToolSpec(
            name = "list_chapters",
            description = "Lists chapter numbers and titles of this novel around the current chapter, " +
                    "or in a given range. Marks the reader's current chapter.",
            parameters = TextToolProtocol.schema {
                putJsonObject("properties") {
                    putJsonObject("from") { put("type", "integer"); put("description", "First 1-based chapter number") }
                    putJsonObject("to") { put("type", "integer"); put("description", "Last 1-based chapter number (inclusive)") }
                }
            }
        )

        override suspend fun execute(argumentsJson: String): String {
            val a = args(argumentsJson)
            val total = context.chapters.size
            val from = (a["from"]?.jsonPrimitive?.intOrNull ?: (context.currentChapterIndex + 1 - 10)).coerceIn(1, maxOf(total, 1))
            val to = (a["to"]?.jsonPrimitive?.intOrNull ?: (from + 29)).coerceIn(from, maxOf(total, 1))
            if (total == 0) return "This novel has no chapters loaded."
            return buildString {
                appendLine("Chapters $from-$to of $total:")
                for (i in (from - 1) until to) {
                    val marker = if (i == context.currentChapterIndex) "  <- current" else ""
                    val read = if (context.chapters[i].read) " (read)" else ""
                    appendLine("${i + 1}. ${context.chapters[i].title}$read$marker")
                }
            }
        }
    }

    private val novelInfo = object : AiTool {
        override val spec = AiToolSpec(
            name = "get_novel_info",
            description = "Returns the novel's title, description, chapter count and the reader's current position.",
            parameters = TextToolProtocol.schema { putJsonObject("properties") { } }
        )

        override suspend fun execute(argumentsJson: String): String = buildString {
            appendLine("Title: ${context.book?.title ?: "(unknown)"}")
            appendLine("Chapters: ${context.chapters.size}")
            appendLine("Completed: ${context.book?.completed ?: false}")
            appendLine("Reader is on chapter ${context.currentChapterIndex + 1}: ${context.currentChapter?.title.orEmpty()}")
            val readCount = context.chapters.count { it.read }
            appendLine("Chapters marked read: $readCount")
            context.book?.description?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Description:")
                appendLine(it)
            }
        }
    }

    private val searchNovelText = object : AiTool {
        override val spec = AiToolSpec(
            name = "search_novel_text",
            description = "Searches the downloaded chapters of this novel for a word or phrase (case-insensitive) " +
                    "and returns matching chapters with short excerpts. Useful to recall where a character, " +
                    "place or event appeared. Only chapters already downloaded are searched.",
            parameters = TextToolProtocol.schema {
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "string"); put("description", "Text to search for") }
                    putJsonObject("max_chapters") { put("type", "integer"); put("description", "Max chapters to return (default 8)") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
            }
        )

        override suspend fun execute(argumentsJson: String): String {
            val a = args(argumentsJson)
            val query = a["query"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (query.isEmpty()) return "Error: query is required."
            val limit = (a["max_chapters"]?.jsonPrimitive?.intOrNull ?: 8).coerceIn(1, 20)
            val bodies = appDatabase.chapterBodyDao().searchInBook(context.bookUrl, escapeLike(query), limit)
            if (bodies.isEmpty()) return "No downloaded chapter contains \"$query\"."
            val byUrl = context.chapters.withIndex().associateBy({ it.value.url }, { it })
            return buildString {
                appendLine("Found \"$query\" in ${bodies.size} chapter(s):")
                bodies.forEach { body ->
                    val entry = byUrl[body.url]
                    val label = entry?.let { "Chapter ${it.index + 1}: ${it.value.title}" } ?: body.url
                    appendLine()
                    appendLine(label)
                    excerpts(body.body, query).forEach { appendLine("  …$it…") }
                }
            }
        }

        private fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        private fun excerpts(text: String, query: String, max: Int = 3, radius: Int = 120): List<String> {
            val out = mutableListOf<String>()
            var start = 0
            while (out.size < max) {
                val at = text.indexOf(query, start, ignoreCase = true)
                if (at < 0) break
                val from = (at - radius).coerceAtLeast(0)
                val to = (at + query.length + radius).coerceAtMost(text.length)
                out += text.substring(from, to).replace(Regex("\\s+"), " ").trim()
                start = to
            }
            return out
        }
    }

    private val webSearch = object : AiTool {
        override val spec = AiToolSpec(
            name = "web_search",
            description = "Searches the web and returns result titles, URLs and snippets. Use for facts " +
                    "outside the novel text: author, publication info, fan wikis, terminology, real-world references.",
            parameters = TextToolProtocol.schema {
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "string"); put("description", "Search query") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
            }
        )

        override suspend fun execute(argumentsJson: String): String {
            val query = args(argumentsJson)["query"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (query.isEmpty()) return "Error: query is required."
            return when (val r = webSearchClient.search(query)) {
                is Response.Error -> "Error: web search failed: ${r.message.lineSequence().first()}"
                is Response.Success -> if (r.data.isEmpty()) "No results for \"$query\"." else buildString {
                    appendLine("Results for \"$query\":")
                    r.data.forEachIndexed { i, item ->
                        appendLine()
                        appendLine("${i + 1}. ${item.title}")
                        appendLine("   ${item.url}")
                        if (item.snippet.isNotBlank()) appendLine("   ${item.snippet}")
                    }
                }
            }
        }
    }

    private val fetchWebPage = object : AiTool {
        override val spec = AiToolSpec(
            name = "fetch_web_page",
            description = "Downloads a web page (e.g. a search result) and returns its readable text.",
            parameters = TextToolProtocol.schema {
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", "string"); put("description", "Absolute http(s) URL") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("url")) }
            }
        )

        override suspend fun execute(argumentsJson: String): String {
            val url = args(argumentsJson)["url"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) return "Error: a valid http(s) url is required."
            return when (val r = webSearchClient.fetchReadable(url)) {
                is Response.Error -> "Error: could not fetch page: ${r.message.lineSequence().first()}"
                is Response.Success -> r.data
            }
        }
    }

    private companion object {
        const val PART_CHARS = 14_000
    }
}
