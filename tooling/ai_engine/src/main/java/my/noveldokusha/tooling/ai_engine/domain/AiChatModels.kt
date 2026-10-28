package my.noveldokusha.tooling.ai_engine.domain

import kotlinx.serialization.json.JsonObject

/**
 * Role of a message in a multi-turn chat, mirroring the OpenAI chat schema.
 */
enum class AiChatRole(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    companion object {
        fun fromWire(value: String): AiChatRole? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A tool invocation requested by the model. [argumentsJson] is the raw JSON
 * object string exactly as the provider returned it.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * One chat message. Assistant messages may carry [toolCalls]; tool messages
 * carry the [toolCallId] they answer and the [toolName] that produced them.
 */
data class AiChatMessage(
    val role: AiChatRole,
    val content: String,
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
) {
    companion object {
        fun system(content: String) = AiChatMessage(AiChatRole.SYSTEM, content)
        fun user(content: String) = AiChatMessage(AiChatRole.USER, content)
        fun assistant(content: String, toolCalls: List<AiToolCall> = emptyList()) =
            AiChatMessage(AiChatRole.ASSISTANT, content, toolCalls)

        fun tool(call: AiToolCall, result: String) =
            AiChatMessage(AiChatRole.TOOL, result, toolCallId = call.id, toolName = call.name)
    }
}

/**
 * Declares a function tool the model may call. [parameters] is a JSON Schema
 * object describing the arguments.
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * Multi-turn chat request. When [tools] is non-empty the provider is asked to
 * use native function calling.
 */
data class AiChatRequest(
    val model: String,
    val messages: List<AiChatMessage>,
    val tools: List<AiToolSpec> = emptyList(),
    val temperature: Float = 0.5f,
    val maxTokens: Int? = null,
)

/**
 * Result of a chat completion: the assistant message (text and/or tool calls).
 */
data class AiChatResult(
    val message: AiChatMessage,
    val model: String,
    val finishReason: String? = null,
    val usage: AiUsage? = null,
)
