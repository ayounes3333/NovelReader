package my.noveldokusha.tooling.ai_engine.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import my.noveldokusha.tooling.ai_engine.domain.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRole
import my.noveldokusha.tooling.ai_engine.domain.AiToolCall
import my.noveldokusha.tooling.ai_engine.domain.AiToolSpec

/**
 * Prompt-based tool protocol used when a provider does not support native
 * function calling. Tools are described in the system prompt and the model
 * requests a call by emitting a fenced block:
 *
 * ```tool_call
 * {"name": "read_chapter", "arguments": {"offset": 0}}
 * ```
 *
 * Tool results are fed back as user messages. This keeps the conversation
 * valid for any plain chat endpoint.
 */
object TextToolProtocol {

    private const val FENCE = "tool_call"

    private val json = Json { ignoreUnknownKeys = true }

    private val blockRegex = Regex(
        "```$FENCE\\s*\\n([\\s\\S]*?)```",
        RegexOption.IGNORE_CASE
    )

    /** Appended to the system prompt to teach the protocol. */
    fun instructions(tools: List<AiToolSpec>): String = buildString {
        appendLine()
        appendLine()
        appendLine("## Tools")
        appendLine("You can call the tools below. To call one, reply with ONLY a fenced block like:")
        appendLine("```$FENCE")
        appendLine("{\"name\": \"<tool_name>\", \"arguments\": { ... }}")
        appendLine("```")
        appendLine("You will then receive the result and can continue. Call at most one tool per reply.")
        appendLine("When you have enough information, answer the user directly without a tool block.")
        appendLine()
        tools.forEach { tool ->
            appendLine("- ${tool.name}: ${tool.description}")
            appendLine("  arguments schema: ${tool.parameters}")
        }
    }

    /**
     * Extracts the tool calls requested in [text]. Returns an empty list when
     * the text is a plain answer.
     */
    fun parseToolCalls(text: String): List<AiToolCall> {
        val calls = blockRegex.findAll(text).mapIndexedNotNull { index, match ->
            parseCall(match.groupValues[1].trim(), index)
        }.toList()
        if (calls.isNotEmpty()) return calls

        // Tolerate a bare JSON object reply without the fence.
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            parseCall(trimmed, 0)?.let { return listOf(it) }
        }
        return emptyList()
    }

    /** [text] with tool-call blocks removed, for display. */
    fun stripToolBlocks(text: String): String = blockRegex.replace(text, "").trim()

    /** Encodes a tool result as the user message that follows the call. */
    fun toolResultMessage(call: AiToolCall, result: String): AiChatMessage =
        AiChatMessage.user("Result of tool `${call.name}`:\n$result")

    /** Renders an assistant message that requested [calls] in the text protocol. */
    fun assistantCallMessage(content: String, calls: List<AiToolCall>): AiChatMessage {
        val blocks = calls.joinToString("\n") { call ->
            "```$FENCE\n{\"name\": \"${call.name}\", \"arguments\": ${call.argumentsJson}}\n```"
        }
        val text = listOf(content.trim(), blocks).filter { it.isNotBlank() }.joinToString("\n")
        return AiChatMessage.assistant(text)
    }

    /**
     * Rewrites a native tool-calling history into one a plain chat endpoint
     * accepts: assistant tool calls become fenced blocks and tool results
     * become user messages.
     */
    fun flattenHistory(messages: List<AiChatMessage>): List<AiChatMessage> =
        messages.map { message ->
            when (message.role) {
                AiChatRole.ASSISTANT ->
                    if (message.toolCalls.isEmpty()) message
                    else assistantCallMessage(message.content, message.toolCalls)

                AiChatRole.TOOL -> toolResultMessage(
                    AiToolCall(message.toolCallId.orEmpty(), message.toolName.orEmpty(), "{}"),
                    message.content
                )

                else -> message
            }
        }

    private fun parseCall(raw: String, index: Int): AiToolCall? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val arguments = when (val args = obj["arguments"]) {
            null -> "{}"
            is JsonObject -> args.toString()
            is JsonPrimitive -> if (args.isString) args.content else args.toString()
            else -> args.toString()
        }
        AiToolCall(id = "text_call_$index", name = name, argumentsJson = arguments)
    }.getOrNull()

    /** Helper for building a JSON Schema object literal. */
    fun schema(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            block()
        }
}
