package my.noveldokusha.tooling.ai_engine.engine

import my.noveldokusha.core.Response
import my.noveldokusha.tooling.ai_engine.domain.AiModelInfo
import my.noveldokusha.tooling.ai_engine.domain.AiProvider
import my.noveldokusha.tooling.ai_engine.domain.AiProviderConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Known OpenAI-compatible provider presets plus the user-defined custom endpoint.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    private val aiHttpClient: AiHttpClient,
    private val secrets: AiSecretsStore,
) {

    fun presets(): List<AiProviderConfig> = listOf(
        AiProviderConfig(
            id = ID_OPENAI,
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            suggestedModel = "gpt-4o-mini",
        ),
        AiProviderConfig(
            id = ID_DEEPSEEK,
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            suggestedModel = "deepseek-chat",
        ),
        AiProviderConfig(
            id = ID_ZAI,
            name = "Z.ai",
            baseUrl = "https://api.z.ai/api/paas/v4",
            suggestedModel = "glm-4-flash",
        ),
        AiProviderConfig(
            id = ID_ZAI_CODING,
            name = "Z.ai (GLM Coding Plan)",
            baseUrl = "https://api.z.ai/api/coding/paas/v4",
            suggestedModel = "glm-4.6",
        ),
        AiProviderConfig(
            id = ID_OPENCODE,
            name = "OpenCode Zen",
            baseUrl = "https://opencode.ai/zen/v1",
            suggestedModel = "",
        ),
        AiProviderConfig(
            id = ID_CUSTOM,
            name = "Custom (OpenAI-compatible)",
            baseUrl = "",
        ),
    )

    fun preset(id: String): AiProviderConfig? = presets().firstOrNull { it.id == id }

    /**
     * Resolves the effective configuration for a selected provider id.
     * For [ID_CUSTOM] the [customBaseUrl] is required (and returned as-is).
     */
    fun resolve(providerId: String, customBaseUrl: String): AiProviderConfig? {
        val preset = preset(providerId) ?: return null
        return if (providerId == ID_CUSTOM) {
            if (customBaseUrl.isBlank()) null
            else preset.copy(baseUrl = customBaseUrl.trim())
        } else preset
    }

    /**
     * Builds a provider for the given [config]. The instance is stateless apart
     * from config, so creating on demand is cheap.
     */
    fun create(config: AiProviderConfig): AiProvider =
        OpenAiCompatibleProvider(aiHttpClient, secrets, config)

    /**
     * Convenience for connection tests: builds a provider and immediately lists
     * its models, verifying both reachability and credentials.
     */
    suspend fun testConnection(config: AiProviderConfig): Response<List<AiModelInfo>> =
        create(config).listModels()

    companion object {
        const val ID_OPENAI = "openai"
        const val ID_DEEPSEEK = "deepseek"
        const val ID_ZAI = "zai"
        const val ID_ZAI_CODING = "zai_coding"
        const val ID_OPENCODE = "opencode"
        const val ID_CUSTOM = "custom"
    }
}
