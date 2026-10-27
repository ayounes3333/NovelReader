package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.AppElevation
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldoksuha.coreui.theme.Success400
import my.noveldokusha.features.reader.features.AiSettingData
import my.noveldokusha.features.reader.ui.ReaderViewModelAiCallbacks
import my.noveldokusha.reader.R
import my.noveldokusha.tooling.ai_engine.domain.AiTask

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingDialog(
    state: AiSettingData,
    callbacks: ReaderViewModelAiCallbacks,
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = AppElevation.dialog)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!state.isAvailable.value) {
                Text(
                    text = stringResource(R.string.ai_no_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorNotice
                )
                return@Column
            }

            FilterChip(
                selected = state.useAiRevision.value && state.hasActiveRevision.value,
                enabled = state.hasActiveRevision.value,
                label = {
                    Text(
                        text = if (state.hasActiveRevision.value) stringResource(R.string.ai_revision_active)
                        else stringResource(R.string.ai_revision_none)
                    )
                },
                onClick = { state.onUseRevisionChange(!state.useAiRevision.value) },
            )

            FilterChip(
                selected = state.summaryMode.value,
                label = {
                    Text(text = stringResource(R.string.ai_summary_mode))
                },
                onClick = { state.onSummaryModeChange(!state.summaryMode.value) },
            )

            AnimatedVisibility(visible = state.summaryMode.value) {
                Text(
                    text = stringResource(R.string.ai_summary_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorNotice
                )
            }

            Text(
                text = stringResource(R.string.ai_run_task_on_chapter),
                style = MaterialTheme.typography.titleSmall
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskChip(
                    label = stringResource(R.string.ai_run_task_title),
                    enabled = !state.isRunning.value
                ) { callbacks.onRunAiTask(AiTask.CreateTitle) }
                TaskChip(
                    label = stringResource(R.string.ai_run_task_summary),
                    enabled = !state.isRunning.value
                ) { callbacks.onRunAiTask(AiTask.CreateSummary) }
                TaskChip(
                    label = stringResource(R.string.ai_run_task_grammar),
                    enabled = !state.isRunning.value
                ) { callbacks.onRunAiTask(AiTask.FixGrammar) }
                TaskChip(
                    label = stringResource(R.string.ai_run_task_narration),
                    enabled = !state.isRunning.value
                ) { callbacks.onRunAiTask(AiTask.FixNarration) }
                TaskChip(
                    label = stringResource(R.string.ai_run_task_translate) +
                            state.translateTarget.value.takeIf { it.isNotBlank() }
                                ?.let { " ($it)" }.orEmpty(),
                    enabled = !state.isRunning.value && state.translateTarget.value.isNotBlank()
                ) { callbacks.onRunAiTask(AiTask.Translate(state.translateTarget.value)) }

                state.customPrompts.forEach { (promptId, promptName) ->
                    TaskChip(
                        label = promptName,
                        enabled = !state.isRunning.value
                    ) { callbacks.onRunAiTask(AiTask.Custom(promptId)) }
                }
            }

            AnimatedVisibility(visible = state.isRunning.value) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.runningTaskLabel.value?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            val errorMessage = state.lastResultMessage.value
            if (state.lastResultSuccess.value == false && errorMessage != null) {
                Text(
                    text = stringResource(R.string.ai_failed, errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (state.lastResultSuccess.value == true) {
                Text(
                    text = stringResource(R.string.ai_done),
                    style = MaterialTheme.typography.bodySmall,
                    color = Success400
                )
            }

            val summary = state.summary.value
            if (!summary.isNullOrBlank()) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Column {
                    Text(
                        text = stringResource(R.string.ai_summary),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (expanded) summary
                        else summary.take(SUMMARY_PREVIEW_LENGTH) + "…",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { expanded = !expanded }
                    )
                    Text(
                        text = stringResource(
                            if (expanded) R.string.ai_show_less else R.string.ai_show_more
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { expanded = !expanded }
                    )
                }
            }

            val activeTitle = state.activeTitle.value
            if (!activeTitle.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.ai_title_generated),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = activeTitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilterChip(
                        selected = false,
                        label = { Text(stringResource(R.string.ai_title_apply)) },
                        onClick = callbacks::onApplyAiTitle,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        enabled = enabled,
        label = { Text(text = label) },
        onClick = onClick,
    )
}

private const val SUMMARY_PREVIEW_LENGTH = 160
