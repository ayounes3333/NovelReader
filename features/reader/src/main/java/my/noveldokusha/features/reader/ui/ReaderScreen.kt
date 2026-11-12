package my.noveldokusha.features.reader.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.setValue
import my.noveldoksuha.coreui.theme.ColorAccent
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.InternalTheme
import my.noveldoksuha.coreui.theme.Themes
import my.noveldoksuha.coreui.theme.colorApp
import my.noveldoksuha.coreui.theme.rememberMutableStateOf
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.features.AiSettingData
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.features.reader.features.TextSynthesis
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.features.reader.ui.ReaderScreenState.Settings.Type
import my.noveldokusha.features.reader.ui.settingDialogs.VoiceReaderDialog
import my.noveldokusha.reader.R
import my.noveldokusha.text_to_speech.Utterance
import my.noveldokusha.text_to_speech.VoiceData
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.tooling.ai_engine.domain.AiTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    state: ReaderScreenState,
    aiCallbacks: ReaderViewModelAiCallbacks,
    onSelectableTextChange: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFollowSystem: (Boolean) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    onThemeSelected: (Themes) -> Unit,
    onTextFontChanged: (String) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onPressBack: () -> Unit,
    onOpenChapterInWeb: () -> Unit,
    onOpenAiChat: () -> Unit,
    readerContent: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    // Capture back action when viewing info
    BackHandler(enabled = state.showReaderInfo.value) {
        state.showReaderInfo.value = false
    }

    Scaffold(
        topBar = {
            val fullScreen by rememberUpdatedState(state.showReaderInfo.value)
            AnimatedVisibility(
                visible = state.showReaderInfo.value,
                enter = expandVertically(initialHeight = { 0 }, expandFrom = Alignment.Top)
                        + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }, shrinkTowards = Alignment.Top)
                        + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorApp.tintedSurface,
                    modifier = Modifier.animateContentSize(),
                ) {
                    Column(
                        modifier = when (fullScreen) {
                            true -> Modifier.displayCutoutPadding()
                            false -> Modifier
                        }
                    ) {
                        val toggleOrSet = { type: Type ->
                            state.settings.selectedSetting.value =
                                when (state.settings.selectedSetting.value) {
                                    type -> Type.None
                                    else -> type
                                }
                        }
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorApp.tintedSurface,
                                scrolledContainerColor = MaterialTheme.colorApp.tintedSurface,
                            ),
                            title = {
                                Text(
                                    text = state.readerInfo.chapterTitle.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.animateContentSize()
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onPressBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                }
                            },
                            actions = {
                                var menuExpanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (state.settings.ai.isAvailable.value) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_chat_open)) },
                                        leadingIcon = { Icon(Icons.Outlined.Forum, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenAiChat()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.open_in_web)) },
                                        leadingIcon = { Icon(Icons.Filled.Public, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenChapterInWeb()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.more)) },
                                        leadingIcon = { Icon(Icons.Outlined.MoreHoriz, null) },
                                        onClick = {
                                            menuExpanded = false
                                            toggleOrSet(Type.More)
                                        }
                                    )
                                }
                            }
                        )
                        // Reader tools: evenly spaced toggles under the title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val selected = state.settings.selectedSetting.value
                            if (state.settings.liveTranslation.isAvailable) ReaderToolItem(
                                selected = selected == Type.LiveTranslation,
                                textId = R.string.translator,
                                icon = Icons.Outlined.Translate,
                                onClick = { toggleOrSet(Type.LiveTranslation) },
                            )
                            if (state.settings.ai.isAvailable.value) ReaderToolItem(
                                selected = selected == Type.Ai,
                                textId = R.string.ai_tools,
                                icon = Icons.Outlined.AutoAwesome,
                                onClick = { toggleOrSet(Type.Ai) },
                            )
                            ReaderToolItem(
                                selected = selected == Type.TextToSpeech,
                                textId = R.string.voice_reader,
                                icon = Icons.Filled.RecordVoiceOver,
                                onClick = { toggleOrSet(Type.TextToSpeech) },
                            )
                            ReaderToolItem(
                                selected = selected == Type.Style,
                                textId = R.string.style,
                                icon = Icons.Outlined.ColorLens,
                                onClick = { toggleOrSet(Type.Style) },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.chapter_x_over_n,
                                    state.readerInfo.chapterCurrentNumber.value,
                                    state.readerInfo.chaptersCount.value,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.progress_x_percentage,
                                    state.readerInfo.chapterPercentageProgress.value
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = ColorAccent,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        content = readerContent,
        bottomBar = {

            AnimatedVisibility(
                visible = state.showReaderInfo.value,
                enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }) + fadeOut(),
            ) {
                Column {
                    ReaderScreenBottomBarDialogs(
                        settings = state.settings,
                        aiCallbacks = aiCallbacks,
                        onTextFontChanged = onTextFontChanged,
                        onTextSizeChanged = onTextSizeChanged,
                        onSelectableTextChange = onSelectableTextChange,
                        onFollowSystem = onFollowSystem,
                        onThemeSelected = onThemeSelected,
                        onKeepScreenOn = onKeepScreenOn,
                        onFullScreen = onFullScreen,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BottomAppBar(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .animateContentSize(),
                        ) {
                        VoiceReaderDialog(state = state.settings.textToSpeech)
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ViewsPreview(
    @PreviewParameter(PreviewDataProvider::class) data: PreviewDataProvider.Data
) {

    val liveTranslationSettingData = LiveTranslationSettingData(
        isAvailable = true,
        enable = remember { mutableStateOf(true) },
        listOfAvailableModels = remember { mutableStateListOf() },
        source = remember {
            mutableStateOf(
                TranslationModelState(
                    language = "fr",
                    available = true,
                    downloading = false,
                    downloadingFailed = false
                )
            )
        },
        target = remember {
            mutableStateOf(
                TranslationModelState(
                    language = "en",
                    available = true,
                    downloading = false,
                    downloadingFailed = false
                )
            )
        },
        onTargetChange = {},
        onEnable = {},
        onSourceChange = {},
        onDownloadTranslationModel = {}
    )

    val textToSpeechSettingData = TextToSpeechSettingData(
        isPlaying = rememberMutableStateOf(false),
        isLoadingChapter = rememberMutableStateOf(false),
        voicePitch = rememberMutableStateOf(1f),
        voiceSpeed = rememberMutableStateOf(1f),
        availableVoices = remember { mutableStateListOf() },
        activeVoice = remember {
            mutableStateOf(
                VoiceData(
                    id = "",
                    language = "",
                    quality = 100,
                    needsInternet = true
                )
            )
        },
        currentActiveItemState = remember {
            mutableStateOf(
                TextSynthesis(
                    playState = Utterance.PlayState.PLAYING,
                    itemPos = ReaderItem.Title(
                        chapterUrl = "",
                        chapterIndex = 0,
                        chapterItemPosition = 1,
                        text = ""
                    )
                )
            )
        },
        isThereActiveItem = rememberMutableStateOf(true),
        setPlaying = {},
        playPreviousItem = {},
        playPreviousChapter = {},
        playNextItem = {},
        playNextChapter = {},
        setVoiceId = {},
        playFirstVisibleItem = {},
        scrollToActiveItem = {},
        setVoiceSpeed = {},
        setVoicePitch = {},
        setCustomSavedVoices = {},
        customSavedVoices = rememberMutableStateOf(value = listOf())
    )

    val style = ReaderScreenState.Settings.StyleSettingsData(
        followSystem = remember { mutableStateOf(true) },
        currentTheme = remember { mutableStateOf(Themes.DARK) },
        textFont = remember { mutableStateOf("Arial") },
        textSize = remember { mutableFloatStateOf(20f) },
    )

    val aiSettingData = AiSettingData(
        isAvailable = remember { mutableStateOf(true) },
        useAiRevision = remember { mutableStateOf(false) },
        summaryMode = remember { mutableStateOf(false) },
        hasActiveRevision = remember { mutableStateOf(false) },
        isRunning = remember { mutableStateOf(false) },
        runningTaskLabel = remember { mutableStateOf(null) },
        lastResultMessage = remember { mutableStateOf(null) },
        lastResultSuccess = remember { mutableStateOf(null) },
        summary = remember { mutableStateOf(null) },
        translateTarget = remember { mutableStateOf("en") },
        customPrompts = remember { mutableStateListOf() },
        activeTitle = remember { mutableStateOf(null) },
        onUseRevisionChange = {},
        onSummaryModeChange = {},
        onApplyTitle = {},
    )

    InternalTheme {
        Surface(color = Color.Black) {
            ReaderScreen(
                state = ReaderScreenState(
                    showReaderInfo = remember { mutableStateOf(true) },
                    readerInfo = ReaderScreenState.CurrentInfo(
                        chapterTitle = remember { mutableStateOf("Chapter title") },
                        chapterCurrentNumber = remember { mutableIntStateOf(2) },
                        chapterPercentageProgress = remember { mutableFloatStateOf(0.5f) },
                        chaptersCount = remember { mutableIntStateOf(255) },
                        chapterUrl = remember { mutableStateOf("Chapter url") },
                    ),
                    settings = ReaderScreenState.Settings(
                        isTextSelectable = remember { mutableStateOf(false) },
                        keepScreenOn = remember { mutableStateOf(false) },
                        textToSpeech = textToSpeechSettingData,
                        liveTranslation = liveTranslationSettingData,
                        ai = aiSettingData,
                        style = style,
                        selectedSetting = remember { mutableStateOf(data.selectedSetting) },
                        fullScreen = remember { mutableStateOf(false) },
                    ),
                    showInvalidChapterDialog = remember { mutableStateOf(false) }
                ),
                aiCallbacks = object : ReaderViewModelAiCallbacks {
                    override fun onRunAiTask(task: AiTask) {}
                    override fun onRefreshAiState() {}
                    override fun onApplyAiTitle() {}
                },
                onTextSizeChanged = {},
                onTextFontChanged = {},
                onSelectableTextChange = {},
                onFollowSystem = {},
                onThemeSelected = {},
                onPressBack = {},
                onOpenChapterInWeb = {},
                onOpenAiChat = {},
                readerContent = {},
                onKeepScreenOn = {},
                onFullScreen = {},
            )
        }
    }
}


private class PreviewDataProvider : PreviewParameterProvider<PreviewDataProvider.Data> {
    data class Data(
        val selectedSetting: Type
    )

    override val values = sequenceOf(
        Data(selectedSetting = Type.None),
        Data(selectedSetting = Type.LiveTranslation),
        Data(selectedSetting = Type.TextToSpeech),
        Data(selectedSetting = Type.Style),
        Data(selectedSetting = Type.More),
        Data(selectedSetting = Type.Ai),
    )
}

@Composable
private fun RowScope.ReaderToolItem(
    selected: Boolean,
    @StringRes textId: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) ColorAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) ColorAccent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(
            text = stringResource(id = textId),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
