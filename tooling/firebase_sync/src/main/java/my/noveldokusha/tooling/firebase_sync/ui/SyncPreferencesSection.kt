package my.noveldokusha.tooling.firebase_sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.noveldokusha.tooling.firebase_sync.auth.AuthState
import my.noveldokusha.tooling.firebase_sync.sync.SyncState

@Composable
fun SyncPreferencesSection(
    authState: AuthState,
    syncState: SyncState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    onInitialSyncClick: () -> Unit,
    automaticSyncEnabled: Boolean,
    onAutomaticSyncToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Library Sync",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        when (authState) {
            is AuthState.NotAuthenticated -> {
                SignInSection(onSignInClick = onSignInClick)
            }
            is AuthState.Authenticated -> {
                AuthenticatedSection(
                    email = authState.email,
                    syncState = syncState,
                    onSignOutClick = onSignOutClick,
                    onManualSyncClick = onManualSyncClick,
                    onInitialSyncClick = onInitialSyncClick,
                    automaticSyncEnabled = automaticSyncEnabled,
                    onAutomaticSyncToggle = onAutomaticSyncToggle
                )
            }
        }
    }
}

@Composable
private fun SignInSection(onSignInClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sign in to sync your library",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Keep your reading progress synchronized across devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSignInClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Sign In")
            }
        }
    }
}

@Composable
private fun AuthenticatedSection(
    email: String,
    syncState: SyncState,
    onSignOutClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    onInitialSyncClick: () -> Unit,
    automaticSyncEnabled: Boolean,
    onAutomaticSyncToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Signed in as:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onSignOutClick) {
                    Text("Sign Out")
                }
            }

            Divider()

            // Sync status
            SyncStatusIndicator(syncState = syncState)

            // Automatic sync toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Sync",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Sync every 6 hours in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = automaticSyncEnabled,
                    onCheckedChange = onAutomaticSyncToggle
                )
            }

            // Manual sync buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInitialSyncClick,
                    modifier = Modifier.weight(1f),
                    enabled = syncState != SyncState.Syncing
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Initial Sync")
                }

                Button(
                    onClick = onManualSyncClick,
                    modifier = Modifier.weight(1f),
                    enabled = syncState != SyncState.Syncing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Now")
                }
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(syncState: SyncState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (syncState) {
            is SyncState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ready to sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is SyncState.Syncing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Syncing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is SyncState.Success -> {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sync completed successfully",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is SyncState.Error -> {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Sync failed: ${syncState.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
