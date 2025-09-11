package my.noveldokusha.tooling.local_server_sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldokusha.tooling.local_server_sync.auth.AuthState
import my.noveldokusha.tooling.local_server_sync.ui.LocalServerAuthDialog
import my.noveldokusha.tooling.local_server_sync.ui.SyncState

@Composable
fun LocalServerSyncPreferencesSection(
    modifier: Modifier = Modifier,
    viewModel: LocalServerSyncViewModel = hiltViewModel()
) {
    var showAuthDialog by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Server Sync",
                    style = MaterialTheme.typography.titleMedium
                )

                // Connection status indicator
                when (authState) {
                    is AuthState.Authenticated -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AuthState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "Not connected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Connection info
            when (authState) {
                is AuthState.Authenticated -> {
                    Text(
                        text = "Connected as: ${(authState as AuthState.Authenticated).username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Email: ${(authState as AuthState.Authenticated).email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is AuthState.Loading -> {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Not connected to local server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sync status
            when (syncState) {
                is SyncState.Syncing -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Syncing: ${(syncState as SyncState.Syncing).operation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is SyncState.Success -> {
                    Text(
                        text = "Last sync: ${(syncState as SyncState.Success).timestamp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SyncState.Error -> {
                    Text(
                        text = "Sync error: ${(syncState as SyncState.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    // No sync status to show
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (authState) {
                    is AuthState.Authenticated -> {
                        Button(
                            onClick = { viewModel.syncLibrary() },
                            enabled = syncState !is SyncState.Syncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now")
                        }

                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            enabled = syncState !is SyncState.Syncing
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { showAuthDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }

    // Show auth dialog when needed
    if (showAuthDialog) {
        LocalServerAuthDialog(
            onDismiss = { showAuthDialog = false }
        )
    }
}
