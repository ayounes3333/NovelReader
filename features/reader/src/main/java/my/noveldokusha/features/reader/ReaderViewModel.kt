package my.noveldokusha.features.reader

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldoksuha.coreui.BaseViewModel
import my.noveldoksuha.coreui.mappers.toTheme
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.utils.StateExtra_Boolean
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.features.reader.ui.ReaderScreenState
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import my.noveldokusha.features.reader.ui.ReaderViewModelAiCallbacks
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import javax.inject.Inject
import kotlin.properties.Delegates

interface ReaderStateBundle {
    var bookUrl: String
    var chapterUrl: String
    var introScrollToSpeaker: Boolean
}

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    stateHandler: SavedStateHandle,
    appPreferences: AppPreferences,
    private val readerManager: ReaderManager,
    readerViewHandlersActions: ReaderViewHandlersActions,
) : BaseViewModel(), ReaderStateBundle, ReaderViewModelAiCallbacks {

    override var bookUrl by StateExtra_String(stateHandler)
    override var chapterUrl by StateExtra_String(stateHandler)
    override var introScrollToSpeaker by StateExtra_Boolean(stateHandler)

    private val readerSession = readerManager.initiateOrGetSession(
        bookUrl = bookUrl,
        chapterUrl = chapterUrl
    )

    private val readingPosStats = readerSession.readingStats
    private val themeId = appPreferences.THEME_ID.state(viewModelScope)

    val state = ReaderScreenState(
        showReaderInfo = mutableStateOf(false),
        readerInfo = ReaderScreenState.CurrentInfo(
            chapterTitle = derivedStateOf {
                readingPosStats.value?.chapterTitle ?: ""
            },
            chapterCurrentNumber = derivedStateOf {
                readingPosStats.value?.run { chapterIndex + 1 } ?: 0
            },
            chapterPercentageProgress = readerSession.readingChapterProgressPercentage,
            chaptersCount = derivedStateOf { readingPosStats.value?.chapterCount ?: 0 },
            chapterUrl = derivedStateOf { readingPosStats.value?.chapterUrl ?: "" }
        ),
        settings = ReaderScreenState.Settings(
            selectedSetting = mutableStateOf(ReaderScreenState.Settings.Type.None),
            isTextSelectable = appPreferences.READER_SELECTABLE_TEXT.state(viewModelScope),
            keepScreenOn = appPreferences.READER_KEEP_SCREEN_ON.state(viewModelScope),
            textToSpeech = readerSession.readerTextToSpeech.state,
            liveTranslation = readerSession.readerLiveTranslation.state,
            ai = readerSession.readerAi.state,
            fullScreen = appPreferences.READER_FULL_SCREEN.state(viewModelScope),
            style = ReaderScreenState.Settings.StyleSettingsData(
                followSystem = appPreferences.THEME_FOLLOW_SYSTEM.state(viewModelScope),
                currentTheme = derivedStateOf { themeId.value.toTheme },
                textFont = appPreferences.READER_FONT_FAMILY.state(viewModelScope),
                textSize = appPreferences.READER_FONT_SIZE.state(viewModelScope),
            )
        ),
        showInvalidChapterDialog = mutableStateOf(false)
    )

    init {
        readerViewHandlersActions.showInvalidChapterDialog = {
            withContext(Dispatchers.Main) {
                state.showInvalidChapterDialog.value = true
            }
        }
    }

    val items = readerSession.items
    val chaptersLoader = readerSession.readerChaptersLoader
    val readerSpeaker = readerSession.readerTextToSpeech
    var readingCurrentChapter by Delegates.observable(readerSession.currentChapter) { _, _, new ->
        readerSession.currentChapter = new
    }
    val onTranslatorChanged = readerSession.readerLiveTranslation.onTranslatorChanged
    val ttsScrolledToTheTop = readerSession.readerTextToSpeech.scrolledToTheTop
    val ttsScrolledToTheBottom = readerSession.readerTextToSpeech.scrolledToTheBottom

    fun onCloseManually() {
        readerManager.close()
    }


    fun startSpeaker(itemIndex: Int) =
        readerSession.startSpeaker(itemIndex = itemIndex)

    fun reloadReader() {
        val currentChapter = readingCurrentChapter.copy()
        readerSession.reloadReader()
        chaptersLoader.tryLoadRestartedInitial(currentChapter)
    }

    /**
     * Refreshes AI chapter-dependent state. Called when the AI panel is opened.
     */
    override fun onRefreshAiState() {
        viewModelScope.launch {
            readerSession.readerAi.refresh(currentChapterUrlForAi())
        }
    }

    /**
     * Runs an AI task over the current chapter. Body-affecting tasks reload the
     * reader automatically when the revision is set to be displayed.
     */
    override fun onRunAiTask(task: AiTask) {
        readerSession.readerAi.runTask(
            chapterUrl = currentChapterUrlForAi(),
            task = task,
            label = task.key,
        )
    }

    /** Applies the generated title (if any) to the chapter. */
    override fun onApplyAiTitle() {
        readerSession.readerAi.state.onApplyTitle()
    }

    private fun currentChapterUrlForAi(): String =
        state.readerInfo.chapterUrl.value.ifBlank { chapterUrl }

    fun updateInfoViewTo(itemIndex: Int) =
        readerSession.updateInfoViewTo(itemIndex = itemIndex)

    fun markChapterStartAsSeen(chapterUrl: String) =
        readerSession.markChapterStartAsSeen(chapterUrl = chapterUrl)

    fun markChapterEndAsSeen(chapterUrl: String) =
        readerSession.markChapterEndAsSeen(chapterUrl = chapterUrl)
}
