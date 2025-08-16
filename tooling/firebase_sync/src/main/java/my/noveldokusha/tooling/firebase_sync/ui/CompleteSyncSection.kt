package my.noveldokusha.tooling.firebase_sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.firebase_sync.auth.AuthState
import my.noveldokusha.tooling.firebase_sync.manager.SyncManager
import my.noveldokusha.tooling.firebase_sync.sync.SyncState

@Composable
fun CompleteSyncSection(
    syncManager: SyncManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authState by syncManager.authState.collectAsStateWithLifecycle(initialValue = AuthState.NotAuthenticated)
    val syncState by syncManager.syncState.collectAsStateWithLifecycle(initialValue = SyncState.Idle)
    val canSync by syncManager.canSync.collectAsStateWithLifecycle(initialValue = false)

    var showAuthDialog by remember { mutableStateOf(false) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var automaticSyncEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Library Sync",
            style = MaterialTheme.typography.titleMedium
        )

        when (authState) {
            is AuthState.NotAuthenticated -> {
                SignInCard(
                    onSignInClick = { showAuthDialog = true },
                    isLoading = isAuthLoading
                )
            }
            is AuthState.Authenticated -> {
                AuthenticatedCard(
                    email = (authState as AuthState.Authenticated).email,
                    syncState = syncState,
                    canSync = canSync,
                    automaticSyncEnabled = automaticSyncEnabled,
                    onSignOut = {
                        scope.launch {
                            syncManager.signOut()
                            automaticSyncEnabled = false
                            syncManager.disableAutomaticSync(context)
                        }
                    },
                    onInitialSync = {
                        scope.launch {
                            syncManager.performInitialSync()
                        }
                    },
                    onManualSync = {
                        scope.launch {
                            syncManager.performManualSync()
                        }
                    },
                    onAutomaticSyncToggle = { enabled ->
                        automaticSyncEnabled = enabled
                        if (enabled) {
                            syncManager.enableAutomaticSync(context)
                        } else {
                            syncManager.disableAutomaticSync(context)
                        }
                    }
                )
            }
        }

        // Show error message if any
        authErrorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { authErrorMessage = null }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // Authentication Dialog
    FirebaseAuthDialog(
        isVisible = showAuthDialog,
        onDismiss = {
            showAuthDialog = false
            isAuthLoading = false
            authErrorMessage = null
        },
        onSignIn = { email, password ->
            scope.launch {
                isAuthLoading = true
                authErrorMessage = null
                val result = syncManager.signIn(email, password)
                if (result.isSuccess) {
                    showAuthDialog = false
                    // Perform initial sync after first login
                    syncManager.performInitialSync()
                } else {
                    authErrorMessage = result.exceptionOrNull()?.message ?: "Sign in failed"
                }
                isAuthLoading = false
            }
        },
        onSignUp = { email, password ->
            scope.launch {
                isAuthLoading = true
                authErrorMessage = null
                val result = syncManager.signUp(email, password)
                if (result.isSuccess) {
                    showAuthDialog = false
                    // Perform initial sync after account creation
                    syncManager.performInitialSync()
                } else {
                    authErrorMessage = result.exceptionOrNull()?.message ?: "Sign up failed"
                }
                isAuthLoading = false
            }
        },
        isLoading = isAuthLoading
    )
}

@Composable
private fun SignInCard(
    onSignInClick: () -> Unit,
    isLoading: Boolean
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sign in to sync your library",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Keep your reading progress synchronized across all your devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSignInClick,
                modifier = Modifier.align(Alignment.End),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign In")
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedCard(
    email: String,
    syncState: SyncState,
    canSync: Boolean,
    automaticSyncEnabled: Boolean,
    onSignOut: () -> Unit,
    onInitialSync: () -> Unit,
    onManualSync: () -> Unit,
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
            // User info and sign out
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
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
                TextButton(onClick = onSignOut) {
                    Text("Sign Out")
                }
            }

            HorizontalDivider()

            // Sync status
            SyncStatusRow(syncState = syncState)

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
                    onClick = onInitialSync,
                    modifier = Modifier.weight(1f),
                    enabled = canSync
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
                    onClick = onManualSync,
                    modifier = Modifier.weight(1f),
                    enabled = canSync
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
private fun SyncStatusRow(syncState: SyncState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (syncState) {
            is SyncState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Cloud,
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
                    imageVector = Icons.Default.CheckCircle,
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
                    imageVector = Icons.Default.Error,
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
