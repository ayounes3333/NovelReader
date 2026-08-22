package my.noveldokusha.settings.views

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import my.noveldokusha.settings.R
import my.noveldokusha.settings.SettingsScreenState

@Composable
internal fun NewAppUpdateDialog(
    updateApp: SettingsScreenState.UpdateApp,
) {
    val newVersion = updateApp.showNewVersionDialog.value ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { updateApp.showNewVersionDialog.value = null },
        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
        title = { Text(text = "Update Available") },
        text = {
            Text(
                text = stringResource(
                    R.string.new_app_version_found_s,
                    newVersion.version
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW)
                        .also { it.data = newVersion.sourceUrl.toUri() })
                }
            ) {
                Text(text = stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = { updateApp.showNewVersionDialog.value = null }) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}