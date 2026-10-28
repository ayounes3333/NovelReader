package my.noveldokusha.tooling.ai_engine.domain

import my.noveldokusha.core.Response

/**
 * Configuration for an OpenAI-compatible endpoint.
 */
data class AiProviderConfig(
    val id: String,
    val name: String,
    /** Base URL including the version segment, e.g. "https://api.openai.com/v1" */
    val baseUrl: String,
    val requiresApiKey: Boolean = true,
    /** Model offered as default when listing is not available. */
    val suggestedModel: String = "",
)

/**
 * Provider abstraction. Implementations must not leak network models upwards;
 * all results are wrapped in the core [Response] type.
 */
interface AiProvider {
    val config: AiProviderConfig

    /** Lists models available with the current credentials. */
    suspend fun listModels(): Response<List<AiModelInfo>>

    /** Runs a single completion. */
    suspend fun complete(request: AiRequest): Response<AiResult>

    /**
     * Runs a multi-turn chat completion, optionally offering function tools.
     * Implementations surface a provider's refusal of the `tools` field as
     * [Response.Error] so callers can fall back to a text protocol.
     */
    suspend fun chat(request: AiChatRequest): Response<AiChatResult>
}
