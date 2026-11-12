package my.noveldokusha.settings.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldoksuha.coreui.components.SettingsSectionTitle
import my.noveldokusha.settings.viewmodels.SyncSettingsViewModel
import my.noveldokusha.tooling.local_server_sync.ui.LocalServerSyncPreferencesSection

@Composable
fun SyncSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val syncProvider by viewModel.selectedSyncProvider.collectAsState()
    val isFirebaseAvailable = viewModel.isFirebaseAvailable()

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(text = "Library Sync")

        SyncProviderRow(
            icon = Icons.Filled.Dns,
            title = "Local Server",
            description = "Self-hosted sync server",
            selected = syncProvider == SyncProvider.LOCAL_SERVER,
            onClick = { viewModel.setSyncProvider(SyncProvider.LOCAL_SERVER) }
        )
        if (isFirebaseAvailable) {
            SyncProviderRow(
                icon = Icons.Filled.Cloud,
                title = "Firebase",
                description = "Google Firebase cloud sync",
                selected = syncProvider == SyncProvider.FIREBASE,
                onClick = { viewModel.setSyncProvider(SyncProvider.FIREBASE) }
            )
        }
        SyncProviderRow(
            icon = Icons.Filled.CloudOff,
            title = "No Sync",
            description = "Your library is stored on this device only",
            selected = syncProvider == SyncProvider.NONE,
            onClick = { viewModel.setSyncProvider(SyncProvider.NONE) }
        )

        AnimatedContent(
            targetState = syncProvider,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "syncProvider"
        ) { provider ->
            when (provider) {
                SyncProvider.LOCAL_SERVER -> LocalServerSyncPreferencesSection()
                SyncProvider.FIREBASE -> if (isFirebaseAvailable) FirebaseSyncSection() else Unit
                SyncProvider.NONE -> Unit
            }
        }
    }
}

@Composable
private fun SyncProviderRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
        },
        trailingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
        modifier = Modifier.clickable(role = Role.RadioButton, onClick = onClick)
    )
}

enum class SyncProvider {
    NONE,
    LOCAL_SERVER,
    FIREBASE
}
