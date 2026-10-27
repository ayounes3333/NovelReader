package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.textPadding
import my.noveldokusha.settings.R
import my.noveldokusha.settings.viewmodels.AiSettingsViewModel
import my.noveldokusha.settings.views.SettingsAiDialog

@Composable
internal fun SettingsAi(
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshPrompts() }
    Column {
        Text(
            text = stringResource(R.string.settings_title_ai),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = ColorAccent
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(R.string.open_ai_settings))
            },
            supportingContent = {
                Text(text = stringResource(R.string.settings_ai_main_description))
            },
            leadingContent = {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { isDialogVisible = true }
        )
    }
    SettingsAiDialog(
        viewModel = viewModel,
        visible = isDialogVisible,
        setVisible = { isDialogVisible = it }
    )
}
