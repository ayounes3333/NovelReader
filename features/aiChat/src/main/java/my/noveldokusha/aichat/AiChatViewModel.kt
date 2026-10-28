package my.noveldokusha.aichat

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import my.noveldokusha.tooling.ai_engine.engine.StepLimitDecision
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.noveldoksuha.coreui.BaseViewModel
import my.noveldoksuha.data.AiChatContext
import my.noveldoksuha.data.AiChatRepository
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.asMutableStateOf
import my.noveldokusha.feature.local_database.tables.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRole
import my.noveldokusha.tooling.ai_engine.engine.AiAgentEvent
import javax.inject.Inject

internal interface AiChatStateBundle {
    var bookUrl: String
    var chapterUrl: String
}

/** A message as shown in the chat list. */
internal sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Assistant(override val id: Long, val text: String) : ChatItem

    /** One tool round requested by the model, shown inline in the transcript. */
    data class ToolUse(override val id: Long, val calls: List<ToolUseCall>) : ChatItem
}

internal data class ToolUseCall(val name: String, val argsSummary: String)

/** What the assistant is doing right now, for the progress indicator. */
internal sealed interface ChatActivity {
    data object Thinking : ChatActivity
    data class Tool(val name: String) : ChatActivity

    /** Step budget exhausted; waiting for the user to decide. */
    data class AwaitingContinue(val stepsUsed: Int) : ChatActivity
}

@HiltViewModel
internal class AiChatViewModel @Inject constructor(
    stateHandle: SavedStateHandle,
    private val aiChatRepository: AiChatRepository,
    private val appPreferences: AppPreferences,
) : BaseViewModel(), AiChatStateBundle {

    override var bookUrl by StateExtra_String(stateHandle)
    override var chapterUrl by StateExtra_String(stateHandle)

    val input = stateHandle.asMutableStateOf("input") { "" }

    var bookTitle by mutableStateOf("")
        private set
    var chapterLabel by mutableStateOf("")
        private set
    var isConfigured by mutableStateOf(aiChatRepository.isConfigured())
        private set
    var activity by mutableStateOf<ChatActivity?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var fallbackNotice by mutableStateOf(false)
        private set
    val webSearchEnabled: State<Boolean> = appPreferences.AI_CHAT_WEB_SEARCH_ENABLED.state(viewModelScope)
    val maxSteps: State<Int> = appPreferences.AI_CHAT_MAX_STEPS.state(viewModelScope)
    val askOnStepLimit: State<Boolean> = appPreferences.AI_CHAT_ASK_ON_STEP_LIMIT.state(viewModelScope)

    val isSending by derivedStateOf { activity != null }

    val items: StateFlow<List<ChatItem>> = aiChatRepository.messagesFlow(bookUrl)
        .map { it.toChatItems() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var context: AiChatContext? = null
    private var sendJob: Job? = null
    private var stepLimitDecision: CompletableDeferred<StepLimitDecision>? = null

    init {
        viewModelScope.launch { refreshContext() }
    }

    private suspend fun refreshContext(): AiChatContext {
        val loaded = aiChatRepository.loadContext(bookUrl, chapterUrl)
        context = loaded
        bookTitle = loaded.book?.title.orEmpty()
        chapterLabel = loaded.currentChapter?.let { "${loaded.currentChapterIndex + 1}/${loaded.chapters.size} · ${it.title}" }.orEmpty()
        isConfigured = aiChatRepository.isConfigured()
        return loaded
    }

    fun send(text: String = input.value) {
        val message = text.trim()
        if (message.isEmpty() || isSending) return
        input.value = ""
        error = null
        activity = ChatActivity.Thinking

        sendJob = viewModelScope.launch {
            try {
                val ctx = context ?: refreshContext()
                val result = aiChatRepository.send(
                    context = ctx,
                    text = message,
                    onEvent = { event ->
                        when (event) {
                            is AiAgentEvent.ToolCallStarted -> activity = ChatActivity.Tool(event.call.name)
                            is AiAgentEvent.ToolCallFinished -> activity = ChatActivity.Thinking
                            is AiAgentEvent.FallbackToTextProtocol -> fallbackNotice = true
                        }
                    },
                    onStepLimit = ::askStepLimit,
                )
                if (result is Response.Error) error = result.message
            } finally {
                stepLimitDecision = null
                activity = null
            }
        }
    }

    private suspend fun askStepLimit(stepsUsed: Int): StepLimitDecision {
        if (!askOnStepLimit.value) return StepLimitDecision.ANSWER_NOW
        val deferred = CompletableDeferred<StepLimitDecision>()
        stepLimitDecision = deferred
        activity = ChatActivity.AwaitingContinue(stepsUsed)
        return try {
            deferred.await()
        } finally {
            stepLimitDecision = null
            activity = ChatActivity.Thinking
        }
    }

    fun resolveStepLimit(decision: StepLimitDecision) {
        stepLimitDecision?.complete(decision)
    }

    fun cancel() {
        sendJob?.cancel()
        sendJob = null
    }

    fun setAskOnStepLimit(enabled: Boolean) {
        appPreferences.AI_CHAT_ASK_ON_STEP_LIMIT.value = enabled
    }

    fun clearConversation() {
        cancel()
        viewModelScope.launch { aiChatRepository.clear(bookUrl) }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        appPreferences.AI_CHAT_WEB_SEARCH_ENABLED.value = enabled
    }

    fun setMaxSteps(value: Int) {
        appPreferences.AI_CHAT_MAX_STEPS.value = value
    }

    fun dismissError() {
        error = null
    }

    /**
     * Maps the raw transcript for display: tool results are hidden, tool
     * requests become compact [ChatItem.ToolUse] rows.
     */
    private fun List<AiChatMessage>.toChatItems(): List<ChatItem> {
        val out = mutableListOf<ChatItem>()
        for (row in this) {
            when (AiChatRole.fromWire(row.role)) {
                AiChatRole.USER -> out += ChatItem.User(row.id, row.content)

                AiChatRole.ASSISTANT -> {
                    val calls = row.toolCallsJson?.let(::parseToolCalls).orEmpty()
                    if (calls.isNotEmpty()) out += ChatItem.ToolUse(row.id, calls)
                    if (row.content.isNotBlank()) out += ChatItem.Assistant(-row.id, row.content)
                }

                else -> Unit
            }
        }
        return out
    }

    private fun parseToolCalls(raw: String): List<ToolUseCall> = runCatching {
        json.parseToJsonElement(raw).jsonArray.map { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            val args = obj["arguments"]?.jsonPrimitive?.content
                ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ToolUseCall(name, args?.let(::summarizeArgs).orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun summarizeArgs(args: JsonObject): String = args.entries
        .filter { (_, v) -> v is JsonPrimitive && v.content.isNotBlank() }
        .joinToString(", ") { (k, v) -> "$k=${(v as JsonPrimitive).content.take(60)}" }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
