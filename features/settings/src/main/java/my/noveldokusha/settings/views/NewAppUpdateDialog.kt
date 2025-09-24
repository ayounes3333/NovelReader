package my.noveldokusha.settings.views

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.aliyounes.aurui.components.AurDialog
import com.aliyounes.aurui.components.AurDialogType
import my.noveldokusha.settings.R
import my.noveldokusha.settings.SettingsScreenState

@Composable
internal fun NewAppUpdateDialog(
    updateApp: SettingsScreenState.UpdateApp,
) {
    val newVersion = updateApp.showNewVersionDialog.value
    val context = LocalContext.current

    AurDialog(
        showDialog = newVersion != null,
        type = AurDialogType.Info,
        icon = Icons.Default.CloudDownload,
        title = "Update Available",
        message = stringResource(
            R.string.new_app_version_found_s,
            newVersion?.version.toString()
        ),
        onDismiss = { updateApp.showNewVersionDialog.value = null },
        confirmText =  stringResource(R.string.download),
        onConfirm = {
            context.startActivity(Intent(Intent.ACTION_VIEW)
                .also { it.data = newVersion?.sourceUrl?.toUri() })
        }
    )
}