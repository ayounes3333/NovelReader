package my.noveldokusha.features.reader.ui

import my.noveldokusha.tooling.ai_engine.domain.AiTask

/**
 * AI callbacks the reader UI needs from the view model layer.
 */
internal interface ReaderViewModelAiCallbacks {
    fun onRunAiTask(task: AiTask)
    fun onRefreshAiState()
    fun onApplyAiTitle()
}
