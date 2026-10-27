package my.noveldokusha.settings.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import my.noveldokusha.settings.R
import my.noveldokusha.settings.viewmodels.AiSettingsViewModel
import my.noveldokusha.settings.viewmodels.AiTestState
import my.noveldokusha.tooling.ai_engine.domain.AiTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAiDialog(
    viewModel: AiSettingsViewModel,
    visible: Boolean,
    setVisible: (Boolean) -> Unit,
) {
    var showPrompts by rememberSaveable { mutableStateOf(false) }

    SettingsAiPromptsDialog(
        prompts = viewModel.prompts.value,
        onSavePrompt = viewModel::savePrompt,
        onDeletePrompt = viewModel::deletePrompt,
        onResetBuiltIns = viewModel::resetBuiltInPrompts,
        visible = showPrompts,
        setVisible = {
            showPrompts = it
            if (it) viewModel.refreshPrompts()
        }
    )

    if (!visible) return

    BasicAlertDialog(onDismissRequest = { setVisible(false) }) {
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 300.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_title_ai),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                ProviderPicker(viewModel)

                if (viewModel.isCustomProvider) {
                    OutlinedTextField(
                        value = viewModel.baseUrl.value,
                        onValueChange = viewModel::setBaseUrl,
                        label = { Text(stringResource(R.string.settings_ai_base_url)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_base_url_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                ApiKeyField(viewModel)

                ModelField(viewModel)

                OutlinedTextField(
                    value = viewModel.targetLanguage.value,
                    onValueChange = viewModel::setTargetLanguage,
                    label = { Text(stringResource(R.string.settings_ai_target_language)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_target_language_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = viewModel::testConnection) {
                        Text(text = stringResource(R.string.settings_ai_test_connection))
                    }
                    when (val state = viewModel.testState.value) {
                        is AiTestState.Testing -> CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )

                        is AiTestState.Success -> Text(
                            text = stringResource(R.string.settings_ai_test_ok, state.modelCount),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        is AiTestState.Failed -> Text(
                            text = stringResource(R.string.settings_ai_test_failed, state.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )

                        AiTestState.Idle -> Unit
                    }
                }

                PerTaskModelsSection(viewModel)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { showPrompts = true }) {
                        Text(text = stringResource(R.string.settings_ai_prompts_manager))
                    }
                }

                val usage = viewModel.usage.value
                if (usage.requests > 0) {
                    Text(
                        text = stringResource(
                            R.string.settings_ai_usage_summary,
                            usage.requests,
                            usage.promptTokens,
                            usage.completionTokens
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PerTaskModelsSection(viewModel: AiSettingsViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { expanded = !expanded }) {
        Text(text = stringResource(R.string.settings_ai_per_task_models))
    }

    if (!expanded) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val overrides = viewModel.modelPerTask.value
        listOf(
            AiTask.CreateTitle.key to stringResource(R.string.ai_run_task_title),
            AiTask.CreateSummary.key to stringResource(R.string.ai_run_task_summary),
            AiTask.FixGrammar.key to stringResource(R.string.ai_run_task_grammar),
            AiTask.FixNarration.key to stringResource(R.string.ai_run_task_narration),
            AiTask.Translate("").key to stringResource(R.string.ai_run_task_translate),
        ).forEach { (taskKey, label) ->
            OutlinedTextField(
                value = overrides[taskKey].orEmpty(),
                onValueChange = { viewModel.setModelForTask(taskKey, it) },
                label = { Text(stringResource(R.string.settings_ai_model_for_task, label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPicker(viewModel: AiSettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = viewModel.presets.firstOrNull { it.id == viewModel.providerId.value }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.settings_ai_provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            viewModel.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        expanded = false
                        viewModel.selectProvider(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(viewModel: AiSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = viewModel.apiKeyInput.value,
            onValueChange = { viewModel.apiKeyInput.value = it },
            label = { Text(stringResource(R.string.settings_ai_api_key)) },
            placeholder = {
                Text(
                    text = if (viewModel.hasApiKey) stringResource(R.string.settings_ai_api_key_set)
                    else stringResource(R.string.settings_ai_api_key_not_set),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = viewModel::saveApiKey,
            enabled = viewModel.currentConfig() != null && viewModel.apiKeyInput.value.isNotBlank()
        ) {
            Text(text = stringResource(R.string.settings_ai_save_key))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(viewModel: AiSettingsViewModel) {
    val models = viewModel.fetchedModels.value
    if (models.isEmpty()) {
        OutlinedTextField(
            value = viewModel.model.value,
            onValueChange = viewModel::setModel,
            label = { Text(stringResource(R.string.settings_ai_model_default)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = viewModel.model.value,
            onValueChange = viewModel::setModel,
            label = { Text(stringResource(R.string.settings_ai_model_default)) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { modelInfo ->
                DropdownMenuItem(
                    text = { Text(modelInfo.id) },
                    onClick = {
                        expanded = false
                        viewModel.setModel(modelInfo.id)
                    }
                )
            }
        }
    }
}
