package my.noveldokusha.tooling.ai_engine.domain

/**
 * Immutable description of a completion request sent to the provider.
 */
data class AiRequest(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
)

/**
 * Result of a successful completion.
 */
data class AiResult(
    val text: String,
    val model: String,
    val finishReason: String? = null,
    val usage: AiUsage? = null,
)

/**
 * Token accounting reported by the provider, used for local usage tracking.
 */
data class AiUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

/**
 * A model exposed by a provider.
 */
data class AiModelInfo(
    val id: String,
    val ownedBy: String? = null,
)
