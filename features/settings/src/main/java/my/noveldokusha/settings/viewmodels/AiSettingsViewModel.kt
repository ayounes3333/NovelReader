package my.noveldokusha.settings.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldoksuha.data.AiPromptsRepository
import my.noveldoksuha.data.AiUsageStore
import my.noveldoksuha.data.AiUsageSummary
import my.noveldokusha.feature.local_database.tables.AiPrompt
import my.noveldokusha.tooling.ai_engine.domain.AiModelInfo
import my.noveldokusha.tooling.ai_engine.domain.AiProviderConfig
import my.noveldokusha.tooling.ai_engine.engine.AiSecretsStore
import my.noveldokusha.tooling.ai_engine.engine.ProviderRegistry
import javax.inject.Inject

sealed interface AiTestState {
    data object Idle : AiTestState
    data object Testing : AiTestState
    data class Success(val modelCount: Int) : AiTestState
    data class Failed(val message: String) : AiTestState
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val secretsStore: AiSecretsStore,
    private val providerRegistry: ProviderRegistry,
    private val aiPromptsRepository: AiPromptsRepository,
    private val usageStore: AiUsageStore,
) : ViewModel() {

    val presets: List<AiProviderConfig> = providerRegistry.presets()

    val providerId: MutableState<String> = mutableStateOf(appPreferences.AI_PROVIDER_ID.value)
    val baseUrl: MutableState<String> = mutableStateOf(appPreferences.AI_PROVIDER_BASE_URL.value)
    val model: MutableState<String> = mutableStateOf(appPreferences.AI_MODEL_DEFAULT.value)
    val targetLanguage: MutableState<String> = mutableStateOf(appPreferences.AI_TRANSLATION_TARGET.value)
    val apiKeyInput: MutableState<String> = mutableStateOf("")
    val testState: MutableState<AiTestState> = mutableStateOf(AiTestState.Idle)

    val fetchedModels: MutableState<List<AiModelInfo>> = mutableStateOf(emptyList())

    /** Per-task model overrides, e.g. {"TRANSLATION": "model-x"} */
    val modelPerTask: MutableState<Map<String, String>> =
        mutableStateOf(appPreferences.AI_MODEL_PER_TASK.value)

    val prompts: MutableState<List<AiPrompt>> = mutableStateOf(emptyList())

    /** Local token accounting (last 30 days). */
    val usage: MutableState<AiUsageSummary> =
        mutableStateOf(AiUsageSummary(0, 0, 0, 0))

    val isCustomProvider: Boolean
        get() = providerId.value == ProviderRegistry.ID_CUSTOM

    val hasApiKey: Boolean
        get() = providerId.value.isNotBlank() && secretsStore.has(providerId.value)

    val isConfigured: Boolean
        get() = providerId.value.isNotBlank() &&
                (!isCustomProvider || baseUrl.value.isNotBlank()) &&
                hasApiKey

    fun currentConfig(): AiProviderConfig? =
        providerRegistry.resolve(providerId.value, baseUrl.value)

    fun selectProvider(config: AiProviderConfig) {
        providerId.value = config.id
        appPreferences.AI_PROVIDER_ID.value = config.id
        if (config.id != ProviderRegistry.ID_CUSTOM) {
            baseUrl.value = config.baseUrl
            appPreferences.AI_PROVIDER_BASE_URL.value = config.baseUrl
        }
        model.value = ""
        appPreferences.AI_MODEL_DEFAULT.value = ""
        fetchedModels.value = emptyList()
        testState.value = AiTestState.Idle
    }

    fun setBaseUrl(url: String) {
        baseUrl.value = url
        appPreferences.AI_PROVIDER_BASE_URL.value = url
    }

    fun setModel(value: String) {
        model.value = value
        appPreferences.AI_MODEL_DEFAULT.value = value
    }

    fun setTargetLanguage(value: String) {
        targetLanguage.value = value
        appPreferences.AI_TRANSLATION_TARGET.value = value
    }

    fun saveApiKey() {
        val config = currentConfig() ?: return
        secretsStore.set(config.id, apiKeyInput.value.trim())
        apiKeyInput.value = ""
        testState.value = AiTestState.Idle
    }

    fun testConnection() {
        val config = currentConfig()
        if (config == null) {
            testState.value = AiTestState.Failed("Select a provider and base URL first.")
            return
        }
        testState.value = AiTestState.Testing
        viewModelScope.launch {
            when (val result = providerRegistry.testConnection(config)) {
                is Response.Success -> {
                    fetchedModels.value = result.data
                    testState.value = AiTestState.Success(result.data.size)
                    if (model.value.isBlank() && config.suggestedModel.isNotBlank()) {
                        setModel(config.suggestedModel)
                    }
                }

                is Response.Error -> testState.value = AiTestState.Failed(result.message)
            }
        }
    }

    // ── Per-task model overrides ───────────────────────────────────────────

    fun setModelForTask(taskKey: String, value: String) {
        val updated = modelPerTask.value.toMutableMap()
        if (value.isBlank()) updated.remove(taskKey) else updated[taskKey] = value
        modelPerTask.value = updated
        appPreferences.AI_MODEL_PER_TASK.value = updated
    }

    // ── Prompt management ──────────────────────────────────────────────────

    fun refreshPrompts() {
        viewModelScope.launch {
            prompts.value = aiPromptsRepository.getAll()
            usage.value = usageStore.summary(lastDays = 30)
        }
    }

    fun savePrompt(prompt: AiPrompt) {
        viewModelScope.launch {
            if (prompt.id == 0L) aiPromptsRepository.create(prompt) else aiPromptsRepository.update(prompt)
            refreshPrompts()
        }
    }

    fun deletePrompt(id: Long) {
        viewModelScope.launch {
            aiPromptsRepository.removeUserPrompt(id)
            refreshPrompts()
        }
    }

    fun resetBuiltInPrompts() {
        viewModelScope.launch {
            aiPromptsRepository.resetBuiltIns()
            refreshPrompts()
        }
    }
}
