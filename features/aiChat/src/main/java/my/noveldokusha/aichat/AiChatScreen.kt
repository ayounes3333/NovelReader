package my.noveldokusha.aichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilledTonalButton
import my.noveldokusha.tooling.ai_engine.engine.StepLimitDecision
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldoksuha.coreui.theme.colorApp

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

    LaunchedEffect(items.size, isSending) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size)
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
            SnackbarHost(snackbarHostState) { Snackbar(it, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                AnimatedVisibility(visible = activity is ChatActivity.AwaitingContinue) {
                    StepLimitPrompt(
                        stepsUsed = (activity as? ChatActivity.AwaitingContinue)?.stepsUsed ?: 0,
                        onDecision = onStepLimitDecision,
                    )
                }
                AnimatedVisibility(visible = activity != null && activity !is ChatActivity.AwaitingContinue) {
                    ActivityIndicator(activity)
                }
                if (fallbackNotice) Text(
                    text = stringResource(R.string.ai_chat_fallback_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorNotice,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ai_no_provider), color = ColorNotice, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (items.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Assistant -> AssistantBubble(item.text)
                        is ChatItem.ToolUse -> ToolUseRow(item.calls)
                    }
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

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = ColorAccent,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorApp.tintedSurface,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun ToolUseRow(calls: List<ToolUseCall>) {
    Column(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        calls.forEach { call ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

private val STEP_LIMIT_OPTIONS = listOf(8, 16, 32, 64, 0)

@Composable
private fun stepLimitLabel(value: Int): String =
    if (value <= 0) stringResource(R.string.ai_chat_step_limit_unlimited) else value.toString()

@Composable
private fun StepLimitPrompt(stepsUsed: Int, onDecision: (StepLimitDecision) -> Unit) {
    Surface(
        color = MaterialTheme.colorApp.tintedSurface,
        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun ActivityIndicator(activity: ChatActivity?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ColorAccent)
        Text(
            text = when (activity) {
                is ChatActivity.Tool -> toolLabel(activity.name)
                else -> stringResource(R.string.ai_chat_tool_working)
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
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
    Surface(color = MaterialTheme.colorApp.tintedSurface) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            if (isSending) {
                FilledIconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Stop, stringResource(R.string.ai_chat_stop))
                }
            } else {
                FilledIconButton(onClick = onSend, enabled = enabled && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_chat_send))
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
        Icon(Icons.Outlined.AutoAwesome, null, tint = ColorAccent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.ai_chat_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ai_chat_empty_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        val suggestions = listOf(
            stringResource(R.string.ai_chat_suggestion_summarize),
            stringResource(R.string.ai_chat_suggestion_recap),
            stringResource(R.string.ai_chat_suggestion_characters),
            stringResource(R.string.ai_chat_suggestion_explain),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            suggestions.forEach { suggestion ->
                AssistChip(
                    enabled = enabled,
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(suggestion) },
                    modifier = Modifier.background(Color.Transparent),
                )
            }
        }
    }
}
