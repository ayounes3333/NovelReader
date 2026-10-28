package my.noveldokusha.tooling.ai_engine.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import my.noveldokusha.core.Response
import my.noveldokusha.tooling.ai_engine.domain.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRequest
import my.noveldokusha.tooling.ai_engine.domain.AiChatResult
import my.noveldokusha.tooling.ai_engine.domain.AiProvider
import my.noveldokusha.tooling.ai_engine.domain.AiToolCall
import my.noveldokusha.tooling.ai_engine.domain.AiToolSpec
import my.noveldokusha.tooling.ai_engine.domain.AiUsage
import kotlin.coroutines.coroutineContext

/**
 * A callable tool exposed to the agent.
 */
interface AiTool {
    val spec: AiToolSpec

    /** Executes with the raw JSON arguments and returns text for the model. */
    suspend fun execute(argumentsJson: String): String
}

/**
 * Progress events emitted while the agent is working.
 */
sealed interface AiAgentEvent {
    data class ToolCallStarted(val call: AiToolCall) : AiAgentEvent
    data class ToolCallFinished(val call: AiToolCall, val resultPreview: String) : AiAgentEvent
    data class FallbackToTextProtocol(val reason: String) : AiAgentEvent
}

/** What to do when the tool step budget is exhausted. */
enum class StepLimitDecision {
    /** Make one tool-less call and answer with what was gathered so far. */
    ANSWER_NOW,

    /** Grant another full budget of steps and keep going. */
    CONTINUE,
}

/**
 * Messages produced by one agent run (assistant tool requests, tool results
 * and the final answer), in order, plus the aggregated token usage.
 */
data class AiAgentRun(
    val newMessages: List<AiChatMessage>,
    val finalAnswer: String,
    val model: String,
    val usage: AiUsage?,
)

/**
 * Generic model → tool → model loop. Prefers native function calling and
 * transparently switches to [TextToolProtocol] when the provider rejects the
 * `tools` field (custom endpoints, older models).
 */
class AiAgentLoop(
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val toolResultMaxChars: Int = DEFAULT_TOOL_RESULT_MAX_CHARS,
) {

    /**
     * @param maxSteps model round-trips allowed before the model is forced to
     *   answer without tools; `<= 0` means unlimited. Overrides the constructor value.
     * @param onMessage invoked for every message as soon as it is produced so
     *   callers can persist partial progress even if the run later fails.
     * @param onStepLimit consulted when [maxSteps] is reached; may suspend
     *   (e.g. to ask the user). Defaults to answering immediately.
     */
    suspend fun run(
        provider: AiProvider,
        model: String,
        systemPrompt: String,
        history: List<AiChatMessage>,
        tools: List<AiTool>,
        temperature: Float = 0.5f,
        maxSteps: Int = this.maxSteps,
        onMessage: suspend (AiChatMessage) -> Unit = {},
        onStepLimit: suspend (stepsUsed: Int) -> StepLimitDecision = { StepLimitDecision.ANSWER_NOW },
        onEvent: suspend (AiAgentEvent) -> Unit = {},
    ): Response<AiAgentRun> {
        val toolsByName = tools.associateBy { it.spec.name }
        val specs = tools.map { it.spec }
        val produced = mutableListOf<AiChatMessage>()
        var usage: AiUsage? = null
        var lastModel = model
        var useTextProtocol = tools.isEmpty()
        var step = 0
        var budget = maxSteps

        suspend fun emit(message: AiChatMessage) {
            produced += message
            onMessage(message)
        }

        while (true) {
            coroutineContext.ensureActive()
            // Once the budget is spent, make one last call without any tools so
            // the user still gets an answer built from what was gathered.
            var forceAnswer = false
            if (budget > 0 && step >= budget) {
                when (onStepLimit(step)) {
                    StepLimitDecision.CONTINUE -> budget += maxSteps
                    StepLimitDecision.ANSWER_NOW -> forceAnswer = true
                }
            }
            step++

            val conversation = history + produced
            val result = if (useTextProtocol || forceAnswer) {
                val prompt = when {
                    forceAnswer -> systemPrompt + FORCE_ANSWER_INSTRUCTIONS
                    tools.isEmpty() -> systemPrompt
                    else -> systemPrompt + TextToolProtocol.instructions(specs)
                }
                provider.chat(
                    AiChatRequest(
                        model = model,
                        messages = listOf(AiChatMessage.system(prompt)) +
                                TextToolProtocol.flattenHistory(conversation),
                        temperature = temperature,
                    )
                )
            } else {
                val native = provider.chat(
                    AiChatRequest(
                        model = model,
                        messages = listOf(AiChatMessage.system(systemPrompt)) + conversation,
                        tools = specs,
                        temperature = temperature,
                    )
                )
                if (native is Response.Error && looksLikeToolsUnsupported(native.message)) {
                    useTextProtocol = true
                    onEvent(AiAgentEvent.FallbackToTextProtocol(native.message))
                    continue
                }
                native
            }

            val completion: AiChatResult = when (result) {
                is Response.Error -> return result
                is Response.Success -> result.data
            }
            usage = usage + completion.usage
            lastModel = completion.model

            val calls = when {
                forceAnswer -> emptyList()
                useTextProtocol -> TextToolProtocol.parseToolCalls(completion.message.content)
                else -> completion.message.toolCalls
            }

            if (calls.isEmpty()) {
                val answer = if (useTextProtocol || forceAnswer) {
                    TextToolProtocol.stripToolBlocks(completion.message.content)
                } else completion.message.content
                emit(AiChatMessage.assistant(answer))
                return Response.Success(
                    AiAgentRun(
                        newMessages = produced,
                        finalAnswer = answer,
                        model = lastModel,
                        usage = usage
                    )
                )
            }

            // Record the request as a native tool-call message regardless of
            // protocol so persistence has one shape; flattening happens on send.
            val visibleText = if (useTextProtocol) {
                TextToolProtocol.stripToolBlocks(completion.message.content)
            } else completion.message.content
            emit(AiChatMessage.assistant(visibleText, calls))

            for (call in calls) {
                coroutineContext.ensureActive()
                onEvent(AiAgentEvent.ToolCallStarted(call))
                val output = executeTool(toolsByName[call.name], call)
                onEvent(AiAgentEvent.ToolCallFinished(call, output.take(PREVIEW_CHARS)))
                emit(AiChatMessage.tool(call, output))
            }
        }
    }

    private suspend fun executeTool(tool: AiTool?, call: AiToolCall): String {
        if (tool == null) return "Error: unknown tool `${call.name}`."
        return try {
            tool.execute(call.argumentsJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: tool `${call.name}` failed: ${e.message ?: e.javaClass.simpleName}"
        }.let { if (it.length > toolResultMaxChars) it.take(toolResultMaxChars) + "\n[truncated]" else it }
    }

    private operator fun AiUsage?.plus(other: AiUsage?): AiUsage? = when {
        this == null -> other
        other == null -> this
        else -> AiUsage(
            promptTokens = promptTokens + other.promptTokens,
            completionTokens = completionTokens + other.completionTokens,
            totalTokens = totalTokens + other.totalTokens,
        )
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 16
        private const val FORCE_ANSWER_INSTRUCTIONS =
            "\n\nYou have used up your tool budget for this turn. Do NOT request any more tools. " +
                    "Answer the user now using only what you have already gathered, and state " +
                    "clearly which parts you could not verify."
        const val DEFAULT_TOOL_RESULT_MAX_CHARS = 24_000
        private const val PREVIEW_CHARS = 200

        private val UNSUPPORTED_HINTS = listOf(
            "tool", "function", "unknown parameter", "unrecognized", "not supported",
            "unsupported", "invalid_request", "400"
        )

        /**
         * Heuristic: a 4xx-style error mentioning tools/functions most likely
         * means the endpoint does not implement function calling.
         */
        fun looksLikeToolsUnsupported(message: String): Boolean {
            val lower = message.lowercase()
            if (lower.contains("authentication") || lower.contains("api key")) return false
            if (lower.contains("rate limit")) return false
            if (lower.contains("endpoint not found")) return false
            return UNSUPPORTED_HINTS.any { lower.contains(it) }
        }
    }
}
