package my.noveldokusha.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorNotice
import my.noveldokusha.feature.local_database.tables.AiPrompt
import my.noveldokusha.settings.R
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import my.noveldokusha.tooling.ai_engine.domain.PromptTemplate

/**
 * Lists stored prompts (built-in + user custom). Click to edit in the
 * embedded [PromptEditorDialog]; built-in prompts can be reset to defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAiPromptsDialog(
    prompts: List<AiPrompt>,
    onSavePrompt: (AiPrompt) -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onResetBuiltIns: () -> Unit,
    visible: Boolean,
    setVisible: (Boolean) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<AiPrompt?>(null) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }

    if (visible) {
        BasicAlertDialog(onDismissRequest = { setVisible(false) }) {
            Card {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_ai_prompts_manager),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_prompts_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorNotice
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(prompts, key = { it.id }) { prompt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editing = prompt
                                        editorVisible = true
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = prompt.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            if (prompt.isBuiltIn) R.string.settings_ai_prompt_builtin_tag
                                            else R.string.settings_ai_prompt_custom_tag
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ColorAccent
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                editing = AiPrompt(
                                    id = 0,
                                    name = "",
                                    systemPrompt = "",
                                    taskType = AiTask.Custom(0).key,
                                )
                                editorVisible = true
                            }
                        ) {
                            Text(text = stringResource(R.string.settings_ai_prompt_new))
                        }
                        OutlinedButton(onClick = onResetBuiltIns) {
                            Text(text = stringResource(R.string.settings_ai_prompts_reset))
                        }
                    }
                }
            }
        }
    }

    PromptEditorDialog(
        prompt = editing,
        visible = editorVisible,
        setVisible = { editorVisible = it },
        onSave = {
            onSavePrompt(it)
            editorVisible = false
        },
        onDelete = { id ->
            onDeletePrompt(id)
            editorVisible = false
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptEditorDialog(
    prompt: AiPrompt?,
    visible: Boolean,
    setVisible: (Boolean) -> Unit,
    onSave: (AiPrompt) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (!visible || prompt == null) return

    var name by rememberSaveable(prompt.id) { mutableStateOf(prompt.name) }
    var systemPrompt by rememberSaveable(prompt.id) { mutableStateOf(prompt.systemPrompt) }

    BasicAlertDialog(onDismissRequest = { setVisible(false) }) {
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (prompt.id == 0L) stringResource(R.string.settings_ai_prompt_new)
                    else stringResource(R.string.settings_ai_prompts_manager),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_ai_prompt_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.settings_ai_prompt_system)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    supportingText = {
                        Text(text = stringResource(R.string.settings_ai_prompt_variables_hint))
                    }
                )

                if (!PromptTemplate.isValid(systemPrompt)) {
                    Text(
                        text = stringResource(R.string.settings_ai_prompt_missing_content_var),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onSave(
                                prompt.copy(
                                    name = name.ifBlank { prompt.name },
                                    systemPrompt = systemPrompt
                                )
                            )
                        },
                        enabled = name.isNotBlank() && PromptTemplate.isValid(systemPrompt)
                    ) {
                        Text(text = stringResource(R.string.settings_ai_prompt_save))
                    }
                    if (!prompt.isBuiltIn && prompt.id != 0L) {
                        OutlinedButton(onClick = { onDelete(prompt.id) }) {
                            Icon(Icons.Filled.Delete, null)
                            Text(
                                text = stringResource(R.string.delete),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { setVisible(false) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
