package my.noveldokusha.features.reader.features

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldoksuha.data.AiRepository
import my.noveldokusha.tooling.ai_engine.domain.AiTask

internal data class AiSettingData(
    val isAvailable: MutableState<Boolean>,
    val useAiRevision: MutableState<Boolean>,
    val summaryMode: MutableState<Boolean>,
    val hasActiveRevision: MutableState<Boolean>,
    val isRunning: MutableState<Boolean>,
    val runningTaskLabel: MutableState<String?>,
    val lastResultMessage: MutableState<String?>,
    val lastResultSuccess: MutableState<Boolean?>,
    val summary: MutableState<String?>,
    val translateTarget: MutableState<String>,
    val customPrompts: SnapshotStateList<Pair<Long, String>>,
    val activeTitle: MutableState<String?>,
    val onUseRevisionChange: (Boolean) -> Unit,
    val onSummaryModeChange: (Boolean) -> Unit,
    val onApplyTitle: () -> Unit,
)

/**
 * Single-chapter AI tooling for the reader: runs tasks over the current
 * chapter, tracks the active revision and exposes the toggles that decide
 * what the reader displays: the original body, the active AI revision, or
 * the chapter summaries (summary mode).
 *
 * In summary mode the summary becomes the chapter body, so the standard
 * voice reader reads it and chapter auto-advance continues with the next
 * chapter's summary until the toggle is switched off.
 */
internal class ReaderAi(
    private val aiRepository: AiRepository,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope,
    private val noSummaryText: String,
    private val onBodyAffected: () -> Unit,
    private val onTitleApplied: (chapterUrl: String, title: String) -> Unit,
) {
    val state = AiSettingData(
        isAvailable = mutableStateOf(aiRepository.isConfigured()),
        useAiRevision = mutableStateOf(appPreferences.AI_REVISIONS_ACTIVE.value),
        summaryMode = mutableStateOf(false),
        hasActiveRevision = mutableStateOf(false),
        isRunning = mutableStateOf(false),
        runningTaskLabel = mutableStateOf(null),
        lastResultMessage = mutableStateOf(null),
        lastResultSuccess = mutableStateOf(null),
        summary = mutableStateOf(null),
        translateTarget = mutableStateOf(""),
        customPrompts = mutableStateListOf(),
        activeTitle = mutableStateOf(null),
        onUseRevisionChange = ::onUseRevisionChange,
        onSummaryModeChange = ::onSummaryModeChange,
        onApplyTitle = ::applyActiveTitle,
    )

    /** Refreshes chapter-dependent state (revision presence, summary). */
    suspend fun refresh(chapterUrl: String) {
        currentChapterUrl = chapterUrl
        state.isAvailable.value = aiRepository.isConfigured()
        state.hasActiveRevision.value =
            aiRepository.activeRevisionBodyOrNull(chapterUrl) != null
        state.summary.value = aiRepository.getActiveSummary(chapterUrl)
        state.activeTitle.value = aiRepository.getActiveTitle(chapterUrl)
        state.translateTarget.value = aiRepository.translationTarget()
        state.customPrompts.clear()
        state.customPrompts.addAll(
            aiRepository.getCustomPrompts().map { it.id to it.name }
        )
    }

    /** Applies the stored generated title as the chapter title. */
    private fun applyActiveTitle() {
        if (state.isRunning.value) return
        val chapterUrl = currentChapterUrl ?: return
        scope.launch {
            val applied = aiRepository.applyActiveTitle(chapterUrl)
            state.activeTitle.value = null
            if (applied != null) onTitleApplied(chapterUrl, applied)
        }
    }

    /** Chapter url the AI panel last refreshed for. */
    var currentChapterUrl: String? = null
        private set

    /**
     * Runs [task] on [chapterUrl]. On success, tasks that change what the
     * reader currently displays (body revisions, or summaries while in
     * summary mode) trigger [onBodyAffected] (reader reload).
     */
    fun runTask(chapterUrl: String, task: AiTask, label: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (state.isRunning.value) return
        state.isRunning.value = true
        state.runningTaskLabel.value = label
        state.lastResultMessage.value = null
        state.lastResultSuccess.value = null

        scope.launch {
            val result = aiRepository.runTask(
                chapterUrl = chapterUrl,
                task = task,
                onChunkProgress = { chunkDone, chunkTotal ->
                    // Long chapters run as several LLM calls; show the
                    // inner progress instead of a static label.
                    state.runningTaskLabel.value = "$label (${chunkDone + 1}/$chunkTotal)"
                },
            )
            state.isRunning.value = false
            state.runningTaskLabel.value = null
            when (result) {
                is Response.Success -> {
                    state.lastResultMessage.value = null
                    state.lastResultSuccess.value = true
                    refresh(chapterUrl)
                    val affectsDisplayedBody = when (task) {
                        is AiTask.CreateTitle -> false
                        is AiTask.CreateSummary -> state.summaryMode.value
                        else -> true
                    }
                    if (affectsDisplayedBody) onBodyAffected()
                    onResult(true, null)
                }

                is Response.Error -> {
                    state.lastResultMessage.value = result.message
                    state.lastResultSuccess.value = false
                    onResult(false, result.message)
                }
            }
        }
    }

    private fun onUseRevisionChange(enabled: Boolean) {
        state.useAiRevision.value = enabled
        appPreferences.AI_REVISIONS_ACTIVE.value = enabled
        // Summary mode takes precedence over the revision display; switching
        // them is a single reader reload.
        if (enabled) state.summaryMode.value = false
        onBodyAffected()
    }

    fun onSummaryModeChange(enabled: Boolean) {
        if (state.summaryMode.value == enabled) return
        state.summaryMode.value = enabled
        if (enabled && state.useAiRevision.value) {
            state.useAiRevision.value = false
            appPreferences.AI_REVISIONS_ACTIVE.value = false
        }
        onBodyAffected()
    }

    /**
     * Body override for the reader loader. Precedence: summary mode (summary
     * content or a placeholder when the chapter has none) > active revision >
     * null (original body).
     */
    suspend fun bodyOrNull(chapterUrl: String): String? {
        if (state.summaryMode.value) {
            return aiRepository.getActiveSummary(chapterUrl) ?: noSummaryText
        }
        if (!state.useAiRevision.value) return null
        return aiRepository.activeRevisionBodyOrNull(chapterUrl)
    }
}
