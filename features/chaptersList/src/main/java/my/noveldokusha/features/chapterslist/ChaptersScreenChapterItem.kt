package my.noveldokusha.features.chapterslist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import my.noveldoksuha.coreui.components.AnimatedTransition
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldoksuha.coreui.theme.Success400
import my.noveldoksuha.coreui.theme.InternalTheme
import my.noveldoksuha.coreui.theme.PreviewThemes
import my.noveldoksuha.coreui.theme.colorApp
import my.noveldokusha.chapterslist.R
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.tooling.ai_engine.domain.AiTask

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
internal fun ChaptersScreenChapterItem(
    chapterWithContext: ChapterWithContext,
    selected: Boolean,
    isLocalSource: Boolean,
    aiAvailable: Boolean,
    hasAiRevision: Boolean,
    aiRunning: Boolean,
    aiTranslateTarget: String,
    customPrompts: List<Pair<Long, String>>,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onAiTask: (AiTask) -> Unit = {},
) {
    val chapter = chapterWithContext.chapter
    ListItem(
        headlineContent = {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingContent = {
            AnimatedTransition(
                targetState = Triple(chapterWithContext.lastReadChapter, chapter.read, hasAiRevision),
                transitionSpec = { fadeIn() togetherWith fadeOut(tween(delayMillis = 150)) }
            ) { (lastReadPosition, read, aiRevision) ->
                when {
                    lastReadPosition -> Text(
                        stringResource(id = R.string.last_read),
                        color = ColorNotice
                    )
                    read -> Text(
                        stringResource(id = R.string.read),
                        color = Success400
                    )
                    aiRevision -> Text(
                        stringResource(id = R.string.ai_revision_badge),
                        color = ColorAccent
                    )
                    else -> Text("")
                }
            }
        },
        trailingContent = if (isLocalSource) null else {
            {
                AnimatedTransition(
                    targetState = chapterWithContext.downloaded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { downloaded ->
                    Row {
                        if (aiAvailable) {
                            ChapterAiMenuButton(
                                aiRunning = aiRunning,
                                aiTranslateTarget = aiTranslateTarget,
                                customPrompts = customPrompts,
                                onAiTask = onAiTask,
                            )
                        }
                        IconButton(onClick = onDownload) {
                            Icon(
                                if (downloaded) Icons.Filled.CloudDownload
                                else Icons.Outlined.CloudDownload,
                                null
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            supportingColor = MaterialTheme.colorScheme.onTertiary,
            containerColor =
            if (selected) MaterialTheme.colorApp.tintedSelectedSurface
            else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .animateContentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
    )
}

@Composable
private fun ChapterAiMenuButton(
    aiRunning: Boolean,
    aiTranslateTarget: String,
    customPrompts: List<Pair<Long, String>>,
    onAiTask: (AiTask) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, enabled = !aiRunning) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = stringResource(R.string.ai_tools),
            tint = if (aiRunning) MaterialTheme.colorScheme.primary else ColorAccent
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ai_run_task_title)) },
            onClick = { expanded = false; onAiTask(AiTask.CreateTitle) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ai_run_task_summary)) },
            onClick = { expanded = false; onAiTask(AiTask.CreateSummary) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ai_run_task_grammar)) },
            onClick = { expanded = false; onAiTask(AiTask.FixGrammar) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ai_run_task_narration)) },
            onClick = { expanded = false; onAiTask(AiTask.FixNarration) }
        )
        if (aiTranslateTarget.isNotBlank()) DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.ai_run_task_translate) +
                            " (${aiTranslateTarget})"
                )
            },
            onClick = {
                expanded = false
                onAiTask(AiTask.Translate(aiTranslateTarget))
            }
        )
        if (customPrompts.isNotEmpty()) {
            HorizontalDivider()
            customPrompts.forEach { (promptId, promptName) ->
                DropdownMenuItem(
                    text = { Text(promptName) },
                    onClick = {
                        expanded = false
                        onAiTask(AiTask.Custom(promptId))
                    }
                )
            }
        }
    }
}


@PreviewThemes
@Composable
private fun PreviewView(
    @PreviewParameter(PreviewProvider::class) previewProviderState: PreviewProviderState
) {
    InternalTheme {
        ChaptersScreenChapterItem(
            chapterWithContext = previewProviderState.chapterWithContext,
            selected = previewProviderState.selected,
            isLocalSource = false,
            aiAvailable = false,
            hasAiRevision = false,
            aiRunning = false,
            aiTranslateTarget = "",
            customPrompts = emptyList(),
            onLongClick = {},
            onClick = {},
            onDownload = {}
        )
    }
}


private data class PreviewProviderState(
    val chapterWithContext: ChapterWithContext,
    val selected: Boolean
)

private class PreviewProvider : PreviewParameterProvider<PreviewProviderState> {
    override val values = sequenceOf(
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = false
                ),
                downloaded = false,
                lastReadChapter = false
            ),
            selected = false
        ),
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter, Title of the chapter, Title of the chapter, Title of the chapter, Title of the chapter,Title of the chapter ,Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = true
                ),
                downloaded = true,
                lastReadChapter = false
            ),
            selected = false
        ),
        PreviewProviderState(
            chapterWithContext = ChapterWithContext(
                chapter = Chapter(
                    title = "Title of the chapter",
                    url = "url",
                    bookUrl = "bookUrl",
                    lastReadOffset = 0,
                    lastReadPosition = 0,
                    position = 0,
                    read = false
                ),
                downloaded = true,
                lastReadChapter = true
            ),
            selected = true
        )
    )
}
