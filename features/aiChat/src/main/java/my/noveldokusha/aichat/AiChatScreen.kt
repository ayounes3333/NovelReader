package my.noveldokusha.aichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldoksuha.coreui.theme.colorApp
import my.noveldokusha.tooling.ai_engine.engine.StepLimitDecision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiChatScreen(
    title: String,
    chapterLabel: String,
    items: List<ChatItem>,
    input: String,
    isConfigured: Boolean,
    isSending: Boolean,
    activity: ChatActivity?,
    streamingText: String,
    error: String?,
    fallbackNotice: Boolean,
    webSearchEnabled: Boolean,
    maxSteps: Int,
    askOnStepLimit: Boolean,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onMaxStepsChange: (Int) -> Unit,
    onAskOnStepLimitChange: (Boolean) -> Unit,
    onStepLimitDecision: (StepLimitDecision) -> Unit,
    onDismissError: () -> Unit,
    onPressBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStepsMenu by remember { mutableStateOf(false) }

    val showLiveRow = activity != null && activity !is ChatActivity.AwaitingContinue
    val totalRows = items.size + if (showLiveRow) 1 else 0

    // Keep the newest content in view, including while text streams in.
    LaunchedEffect(totalRows, streamingText.length / 40) {
        if (totalRows > 0) listState.animateScrollToItem(totalRows - 1, scrollOffset = Int.MAX_VALUE)
    }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorApp.tintedSurface,
                    scrolledContainerColor = MaterialTheme.colorApp.tintedSurface,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistantAvatar(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title.ifBlank { stringResource(R.string.ai_chat_title) },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (chapterLabel.isNotBlank()) Text(
                                text = chapterLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPressBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_web_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Language, null) },
                            trailingIcon = {
                                Switch(checked = webSearchEnabled, onCheckedChange = onWebSearchEnabledChange)
                            },
                            onClick = { onWebSearchEnabledChange(!webSearchEnabled) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_step_limit, stepLimitLabel(maxSteps))) },
                            leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                            onClick = { showMenu = false; showStepsMenu = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_ask_on_step_limit)) },
                            trailingIcon = {
                                Switch(checked = askOnStepLimit, onCheckedChange = onAskOnStepLimitChange)
                            },
                            onClick = { onAskOnStepLimitChange(!askOnStepLimit) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_clear)) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                            onClick = { showMenu = false; showClearDialog = true },
                        )
                    }
                    DropdownMenu(expanded = showStepsMenu, onDismissRequest = { showStepsMenu = false }) {
                        STEP_LIMIT_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stepLimitLabel(option)) },
                                trailingIcon = { if (option == maxSteps) Icon(Icons.Outlined.Check, null) },
                                onClick = { showStepsMenu = false; onMaxStepsChange(option) },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                Snackbar(
                    it,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                AnimatedVisibility(visible = activity is ChatActivity.AwaitingContinue) {
                    StepLimitPrompt(
                        stepsUsed = (activity as? ChatActivity.AwaitingContinue)?.stepsUsed ?: 0,
                        onDecision = onStepLimitDecision,
                    )
                }
                if (fallbackNotice) Text(
                    text = stringResource(R.string.ai_chat_fallback_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorNotice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                InputBar(
                    input = input,
                    enabled = isConfigured,
                    isSending = isSending,
                    onInputChange = onInputChange,
                    onSend = { onSend(input) },
                    onCancel = onCancel,
                )
            }
        },
    ) { innerPadding ->
        if (!isConfigured) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.ai_no_provider),
                    color = ColorNotice,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (items.isEmpty() && !showLiveRow) {
            EmptyState(
                contentPadding = innerPadding,
                enabled = !isSending,
                onSuggestion = onSend,
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Assistant -> AssistantBubble(item.text)
                        is ChatItem.ToolUse -> ToolUseRow(item.calls)
                    }
                }
                if (showLiveRow) item(key = "live") {
                    LiveAssistantRow(activity = activity, streamingText = streamingText)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.ai_chat_clear)) },
            text = { Text(stringResource(R.string.ai_chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClear() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private val UserBubbleShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
private val AssistantBubbleShape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)

@Composable
private fun AssistantAvatar(size: androidx.compose.ui.unit.Dp = 28.dp, pulsing: Boolean = false) {
    val scale = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "avatarPulse")
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "avatarScale"
        ).value
    } else 1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(ColorAccent, ColorAccent.copy(alpha = 0.55f))
                )
            )
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(UserBubbleShape)
                .background(
                    Brush.linearGradient(
                        listOf(ColorAccent, ColorAccent.copy(alpha = 0.78f))
                    )
                )
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorApp.tintedSurface,
            shape = AssistantBubbleShape,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (text.isNotEmpty()) SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
                trailing?.invoke()
            }
        }
    }
}

/** The assistant row for the in-flight turn: streamed text, tool pill or thinking dots. */
@Composable
private fun LiveAssistantRow(activity: ChatActivity?, streamingText: String) {
    when {
        streamingText.isNotEmpty() -> AssistantBubble(text = streamingText) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                ThinkingDots(dotSize = 5.dp)
            }
        }

        activity is ChatActivity.Tool -> ToolActivityRow(name = activity.name)

        else -> Row(verticalAlignment = Alignment.Top) {
            AssistantAvatar(pulsing = true)
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorApp.tintedSurface,
                shape = AssistantBubbleShape,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    ThinkingDots()
                    Text(
                        text = stringResource(R.string.ai_chat_tool_working),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolActivityRow(name: String) {
    Row(verticalAlignment = Alignment.Top) {
        AssistantAvatar(pulsing = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = ColorAccent.copy(alpha = 0.12f),
            shape = AssistantBubbleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorAccent.copy(alpha = 0.35f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    toolIcon(name),
                    contentDescription = null,
                    tint = ColorAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = toolLabel(name),
                    style = MaterialTheme.typography.labelMedium,
                    color = ColorAccent,
                )
                ThinkingDots(dotSize = 5.dp, color = ColorAccent)
            }
        }
    }
}

/** Three dots that rise and fade in sequence. */
@Composable
private fun ThinkingDots(
    dotSize: androidx.compose.ui.unit.Dp = 7.dp,
    color: Color = ColorAccent,
) {
    val transition = rememberInfiniteTransition(label = "thinkingDots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1100
                        0f at 0 using LinearEasing
                        1f at 300 using LinearEasing
                        0f at 600 using LinearEasing
                        0f at 1100
                    },
                    initialStartOffset = StartOffset(index * 160),
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(0.75f + 0.45f * progress)
                    .alpha(0.35f + 0.65f * progress)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun ToolUseRow(calls: List<ToolUseCall>) {
    Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            calls.forEach { call ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        toolIcon(call.name),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = ColorAccent,
                    )
                    Text(
                        text = buildString {
                            append(toolLabel(call.name))
                            if (call.argsSummary.isNotBlank()) append(" · ").append(call.argsSummary)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val STEP_LIMIT_OPTIONS = listOf(8, 16, 32, 64, 0)

@Composable
private fun stepLimitLabel(value: Int): String =
    if (value <= 0) stringResource(R.string.ai_chat_step_limit_unlimited) else value.toString()

@Composable
private fun StepLimitPrompt(stepsUsed: Int, onDecision: (StepLimitDecision) -> Unit) {
    Surface(
        color = MaterialTheme.colorApp.tintedSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.ai_chat_step_limit_reached, stepsUsed),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onDecision(StepLimitDecision.CONTINUE) }) {
                    Text(stringResource(R.string.ai_chat_step_limit_continue))
                }
                TextButton(onClick = { onDecision(StepLimitDecision.ANSWER_NOW) }) {
                    Text(stringResource(R.string.ai_chat_step_limit_answer_now))
                }
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "read_chapter", "list_chapters", "get_novel_info" -> Icons.Outlined.MenuBook
    "search_novel_text" -> Icons.Outlined.ManageSearch
    "web_search" -> Icons.Outlined.Language
    "fetch_web_page" -> Icons.Outlined.Public
    else -> Icons.Outlined.Tune
}

@Composable
private fun toolLabel(name: String): String = when (name) {
    "read_chapter" -> stringResource(R.string.ai_chat_tool_reading_chapter)
    "list_chapters" -> stringResource(R.string.ai_chat_tool_listing_chapters)
    "get_novel_info" -> stringResource(R.string.ai_chat_tool_novel_info)
    "search_novel_text" -> stringResource(R.string.ai_chat_tool_searching_novel)
    "web_search" -> stringResource(R.string.ai_chat_tool_searching_web)
    "fetch_web_page" -> stringResource(R.string.ai_chat_tool_fetching_page)
    else -> name
}.trimEnd('…')

@Composable
private fun InputBar(
    input: String,
    enabled: Boolean,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorApp.tintedSurface, tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) Text(
                    text = stringResource(R.string.ai_chat_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = enabled,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(ColorAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = enabled && input.isNotBlank()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSending -> MaterialTheme.colorScheme.surfaceContainerHighest
                            canSend -> ColorAccent
                            else -> ColorAccent.copy(alpha = 0.35f)
                        }
                    )
                    .clickable(enabled = isSending || canSend) {
                        if (isSending) onCancel() else onSend()
                    }
            ) {
                if (isSending) {
                    Icon(
                        Icons.Filled.Stop,
                        stringResource(R.string.ai_chat_stop),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.ai_chat_send),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(
    contentPadding: PaddingValues,
    enabled: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssistantAvatar(size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ai_chat_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ai_chat_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(24.dp))
        val suggestions = listOf(
            stringResource(R.string.ai_chat_suggestion_summarize),
            stringResource(R.string.ai_chat_suggestion_recap),
            stringResource(R.string.ai_chat_suggestion_characters),
            stringResource(R.string.ai_chat_suggestion_explain),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) ColorAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ColorAccent.copy(alpha = 0.12f))
                        .border(1.dp, ColorAccent.copy(alpha = 0.3f), CircleShape)
                        .clickable(enabled = enabled) { onSuggestion(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
