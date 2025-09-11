package my.noveldokusha.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldokusha.settings.viewmodels.SyncSettingsViewModel
import my.noveldokusha.tooling.local_server_sync.ui.LocalServerSyncPreferencesSection

@Composable
fun SyncSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val syncProvider by viewModel.selectedSyncProvider.collectAsState()
    val isFirebaseAvailable = viewModel.isFirebaseAvailable()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sync Provider Selection
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sync Provider",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose how to sync your library across devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Local Server Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Server",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Self-hosted sync server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = syncProvider == SyncProvider.LOCAL_SERVER,
                        onClick = { viewModel.setSyncProvider(SyncProvider.LOCAL_SERVER) }
                    )
                }

                // Firebase Option (if available)
                if (isFirebaseAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Firebase",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Google Firebase cloud sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = syncProvider == SyncProvider.FIREBASE,
                            onClick = { viewModel.setSyncProvider(SyncProvider.FIREBASE) }
                        )
                    }
                }

                // No Sync Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Sync",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Local storage only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = syncProvider == SyncProvider.NONE,
                        onClick = { viewModel.setSyncProvider(SyncProvider.NONE) }
                    )
                }
            }
        }

        // Show appropriate sync configuration based on selection
        when (syncProvider) {
            SyncProvider.LOCAL_SERVER -> {
                LocalServerSyncPreferencesSection()
            }
            SyncProvider.FIREBASE -> {
                if (isFirebaseAvailable) {
                    // Keep existing Firebase sync section
                    FirebaseSyncSection()
                }
            }
            SyncProvider.NONE -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sync Disabled",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your library data is stored locally only and won't sync across devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

enum class SyncProvider {
    NONE,
    LOCAL_SERVER,
    FIREBASE
}
