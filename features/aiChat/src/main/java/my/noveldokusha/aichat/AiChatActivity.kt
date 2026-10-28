package my.noveldokusha.aichat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import my.noveldoksuha.coreui.BaseActivity
import my.noveldoksuha.coreui.theme.Theme
import my.noveldokusha.core.utils.Extra_String

@AndroidEntryPoint
class AiChatActivity : BaseActivity() {

    class IntentData : Intent, AiChatStateBundle {
        override var bookUrl by Extra_String()
        override var chapterUrl by Extra_String()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, bookUrl: String, chapterUrl: String) : super(
            ctx,
            AiChatActivity::class.java
        ) {
            this.bookUrl = bookUrl
            this.chapterUrl = chapterUrl
        }
    }

    private val viewModel by viewModels<AiChatViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme(themeProvider = themeProvider) {
                val items by viewModel.items.collectAsStateWithLifecycle()
                AiChatScreen(
                    title = viewModel.bookTitle,
                    chapterLabel = viewModel.chapterLabel,
                    items = items,
                    input = viewModel.input.value,
                    isConfigured = viewModel.isConfigured,
                    isSending = viewModel.isSending,
                    activity = viewModel.activity,
                    error = viewModel.error,
                    fallbackNotice = viewModel.fallbackNotice,
                    webSearchEnabled = viewModel.webSearchEnabled.value,
                    maxSteps = viewModel.maxSteps.value,
                    askOnStepLimit = viewModel.askOnStepLimit.value,
                    onInputChange = { viewModel.input.value = it },
                    onSend = viewModel::send,
                    onCancel = viewModel::cancel,
                    onClear = viewModel::clearConversation,
                    onWebSearchEnabledChange = viewModel::setWebSearchEnabled,
                    onMaxStepsChange = viewModel::setMaxSteps,
                    onAskOnStepLimitChange = viewModel::setAskOnStepLimit,
                    onStepLimitDecision = viewModel::resolveStepLimit,
                    onDismissError = viewModel::dismissError,
                    onPressBack = ::finish,
                )
            }
        }
    }
}
