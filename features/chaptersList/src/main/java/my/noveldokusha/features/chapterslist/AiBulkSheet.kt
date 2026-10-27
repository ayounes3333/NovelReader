package my.noveldokusha.features.chapterslist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldokusha.chapterslist.R
import my.noveldoksuha.data.AiBulkJobProgress
import my.noveldokusha.tooling.ai_engine.domain.AiErrorCategory
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import java.text.DateFormat
import java.util.Date

/**
 * UI snapshot of the latest bulk job for the book.
 */
internal data class AiBulkSheetJobState(
    val finished: Boolean,
    val done: Int,
    val total: Int,
    val skipped: Int,
    val currentChapterUrl: String?,
    val failures: List<AiBulkJobProgress.Failure>,
)

/**
 * Bulk AI task sheet for the selection mode: pick a task and run it over the
 * selected chapters through the background worker. Also shows the state of
 * the most recent bulk job for this book (progress / failed chapters with
 * retry).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiBulkSheet(
    visible: Boolean,
    state: ChaptersScreenState,
    latestJob: AiBulkSheetJobState?,
    onDismiss: () -> Unit,
    onRun: (task: AiTask, regenerate: Boolean) -> Unit,
    onRetryFailed: () -> Unit,
) {
    if (!visible) return

    val selectedCount = state.selectedChaptersUrl.size

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_bulk_title, selectedCount),
                color = ColorAccent,
                style = MaterialTheme.typography.titleMedium
            )

            if (!state.aiAvailable.value) {
                Text(
                    text = stringResource(R.string.ai_no_provider),
                    color = ColorNotice,
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            var selectedTaskKey by remember {
                mutableStateOf(AiTask.FixGrammar.key)
            }
            var selectedPromptId by remember { mutableStateOf(0L) }
            var regenerate by remember {
                mutableStateOf(false)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskOptionChip(
                    label = stringResource(R.string.ai_run_task_title),
                    selected = selectedTaskKey == AiTask.CreateTitle.key,
                    onClick = { selectedTaskKey = AiTask.CreateTitle.key }
                )
                TaskOptionChip(
                    label = stringResource(R.string.ai_run_task_summary),
                    selected = selectedTaskKey == AiTask.CreateSummary.key,
                    onClick = { selectedTaskKey = AiTask.CreateSummary.key }
                )
                TaskOptionChip(
                    label = stringResource(R.string.ai_run_task_grammar),
                    selected = selectedTaskKey == AiTask.FixGrammar.key,
                    onClick = { selectedTaskKey = AiTask.FixGrammar.key }
                )
                TaskOptionChip(
                    label = stringResource(R.string.ai_run_task_narration),
                    selected = selectedTaskKey == AiTask.FixNarration.key,
                    onClick = { selectedTaskKey = AiTask.FixNarration.key }
                )
                if (state.aiTranslateTarget.value.isNotBlank()) {
                    TaskOptionChip(
                        label = stringResource(R.string.ai_run_task_translate) +
                                " (${state.aiTranslateTarget.value})",
                        selected = selectedTaskKey == AiTask.Translate("").key,
                        onClick = { selectedTaskKey = AiTask.Translate("").key }
                    )
                }
                state.aiCustomPrompts.forEach { (promptId, promptName) ->
                    TaskOptionChip(
                        label = promptName,
                        selected = selectedTaskKey == AiTask.Custom(promptId).key &&
                                selectedPromptId == promptId,
                        onClick = {
                            selectedTaskKey = AiTask.Custom(promptId).key
                            selectedPromptId = promptId
                        }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ai_bulk_regenerate),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = regenerate, onCheckedChange = { regenerate = it })
            }

            Button(
                onClick = {
                    val task = when (selectedTaskKey) {
                        AiTask.CreateTitle.key -> AiTask.CreateTitle
                        AiTask.CreateSummary.key -> AiTask.CreateSummary
                        AiTask.FixGrammar.key -> AiTask.FixGrammar
                        AiTask.FixNarration.key -> AiTask.FixNarration
                        AiTask.Custom(0).key -> AiTask.Custom(selectedPromptId)
                        else -> AiTask.Translate(state.aiTranslateTarget.value)
                    }
                    onRun(task, regenerate)
                    onDismiss()
                },
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.ai_bulk_run))
            }

            if (latestJob != null) {
                LatestJobStatus(
                    job = latestJob,
                    resolveChapterTitle = { url ->
                        state.chapters.find { it.chapter.url == url }?.chapter?.title ?: url
                    },
                    onRetryFailed = onRetryFailed
                )
            }
        }
    }
}

@Composable
private fun TaskOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, label = { Text(label) }, onClick = onClick)
}

@Composable
private fun LatestJobStatus(
    job: AiBulkSheetJobState,
    resolveChapterTitle: (String) -> String,
    onRetryFailed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.ai_bulk_last_job),
            style = MaterialTheme.typography.titleSmall
        )
        if (!job.finished) {
            Text(
                text = stringResource(
                    R.string.ai_bulk_progress, job.done, job.total, job.skipped
                ),
                style = MaterialTheme.typography.bodySmall
            )
            job.currentChapterUrl?.let { url ->
                Text(
                    text = stringResource(R.string.ai_bulk_current, resolveChapterTitle(url)),
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorNotice
                )
            }
            LinearProgressIndicator(
                progress = { if (job.total > 0) job.done.toFloat() / job.total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = stringResource(
                    R.string.ai_bulk_finished, job.done, job.skipped, job.failures.size
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }

        AnimatedVisibility(visible = job.failures.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.ai_bulk_failures, job.failures.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    job.failures.forEach { failure ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = resolveChapterTitle(failure.chapterUrl),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = categoryLabel(failure.errorCategory),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorNotice
                                )
                                if (failure.epochTimeMilli > 0) {
                                    Text(
                                        text = DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT, DateFormat.SHORT
                                        ).format(Date(failure.epochTimeMilli)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ColorNotice
                                    )
                                }
                            }
                            Text(
                                text = failure.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorNotice
                            )
                        }
                    }
                }
                Button(onClick = onRetryFailed) {
                    Text(text = stringResource(R.string.ai_bulk_retry_failed))
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: AiErrorCategory): String = stringResource(
    when (category) {
        AiErrorCategory.AUTH -> R.string.ai_error_auth
        AiErrorCategory.RATE_LIMIT -> R.string.ai_error_rate_limit
        AiErrorCategory.NETWORK -> R.string.ai_error_network
        AiErrorCategory.SERVER -> R.string.ai_error_server
        AiErrorCategory.UNKNOWN -> R.string.ai_error_unknown
    }
)
