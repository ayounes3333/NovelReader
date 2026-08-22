package my.noveldokusha.tooling.quick_setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.tooling.backup_restore.RestoreDataService
import my.noveldokusha.tooling.backup_create.onBackupCreate
import my.noveldoksuha.coreui.theme.AppSpacing

@Composable
fun onQuickSetupSend(
    appPreferences: AppPreferences,
): () -> Unit {
    val backupSend = onBackupCreate()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) Dialog(
        onDismissRequest = { showDialog = false },
        content = {
            Card {
                Column {
                    Text(
                        text = "Quick Setup - Send",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Export your library, chapters, reading progress, and optionally images to a backup file that can be transferred to your new device.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(
                        onClick = {
                            showDialog = false
                            backupSend()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.large)
                    ) {
                        Text(text = "Create backup file")
                    }
                }
            }
        }
    )

    return { showDialog = true }
}

@Composable
fun onQuickSetupReceive(): () -> Unit {
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val fileExplorer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                RestoreDataService.start(ctx = context, uri = uri)
            }
        }
    )

    if (showDialog) Dialog(
        onDismissRequest = { showDialog = false },
        content = {
            Card {
                Column {
                    Text(
                        text = "Quick Setup - Receive",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Select the backup file from your old device to import library, chapters, and reading progress.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(
                        onClick = {
                            showDialog = false
                            fileExplorer.launch("*/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.large)
                    ) {
                        Text(text = "Select backup file")
                    }
                }
            }
        }
    )

    return { showDialog = true }
}
