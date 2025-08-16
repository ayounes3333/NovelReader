package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.textPadding
import my.noveldokusha.settings.viewmodels.FirebaseSyncViewModel
import my.noveldokusha.tooling.firebase_sync.auth.AuthState
import my.noveldokusha.tooling.firebase_sync.sync.SyncState
import my.noveldokusha.tooling.firebase_sync.ui.FirebaseAuthDialog

@Composable
fun FirebaseSyncSection(
    modifier: Modifier = Modifier
) {
    // Check if Firebase sync is available (full flavor)
    val isSyncAvailable = remember {
        try {
            Class.forName("my.noveldokusha.tooling.firebase_sync.manager.SyncManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Library Sync",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = ColorAccent
        )

        if (isSyncAvailable) {
            FirebaseSyncContent()
        } else {
            // FOSS version - sync not available
            ListItem(
                headlineContent = {
                    Text(text = "Library sync not available")
                },
                supportingContent = {
                    Text(text = "This feature is only available in the full version of the app")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            )
        }
    }
}

@Composable
private fun FirebaseSyncContent() {
    val viewModel: FirebaseSyncViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authState by viewModel.syncManager.authState.collectAsState(initial = AuthState.NotAuthenticated)
    val syncState by viewModel.syncManager.syncState.collectAsState(initial = SyncState.Idle)
    val canSync by viewModel.syncManager.canSync.collectAsState(initial = false)

    var showAuthDialog by remember { mutableStateOf(false) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var automaticSyncEnabled by remember { mutableStateOf(false) }

    when (authState) {
        is AuthState.NotAuthenticated -> {
            // Sign in option
            ListItem(
                headlineContent = {
                    Text(text = "Sign in to sync library")
                },
                supportingContent = {
                    Text(text = "Access your library across multiple devices")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                },
                trailingContent = if (isAuthLoading) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else null,
                modifier = Modifier.clickable {
                    if (!isAuthLoading) {
                        showAuthDialog = true
                    }
                }
            )
        }

        is AuthState.Authenticated -> {
            val email = (authState as AuthState.Authenticated).email

            // Account info
            ListItem(
                headlineContent = {
                    Text(text = "Signed in as")
                },
                supportingContent = {
                    Text(text = email)
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            )

            // Manual sync
            ListItem(
                headlineContent = {
                    Text(text = "Sync now")
                },
                supportingContent = {
                    when (syncState) {
                        is SyncState.Syncing -> Text(text = "Syncing library...")
                        is SyncState.Success -> Text(text = "Last sync successful")
                        is SyncState.Error -> Text(text = "Sync failed: ${(syncState as SyncState.Error).message}")
                        else -> Text(text = "Manually sync your library")
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = when (syncState) {
                            is SyncState.Syncing -> Icons.Default.CloudSync
                            is SyncState.Error -> Icons.Default.SyncDisabled
                            else -> Icons.Default.Sync
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                },
                trailingContent = if (syncState is SyncState.Syncing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else null,
                modifier = Modifier.clickable {
                    if (canSync && syncState !is SyncState.Syncing) {
                        scope.launch {
                            viewModel.syncManager.performManualSync()
                        }
                    }
                }
            )

            // Automatic sync toggle
            ListItem(
                headlineContent = {
                    Text(text = "Automatic sync")
                },
                supportingContent = {
                    Text(text = if (automaticSyncEnabled) "Sync automatically in background" else "Manual sync only")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                },
                trailingContent = {
                    Switch(
                        checked = automaticSyncEnabled,
                        onCheckedChange = { enabled ->
                            automaticSyncEnabled = enabled
                            if (enabled) {
                                viewModel.syncManager.enableAutomaticSync(context)
                            } else {
                                viewModel.syncManager.disableAutomaticSync(context)
                            }
                        }
                    )
                }
            )

            // Sign out option
            ListItem(
                headlineContent = {
                    Text(text = "Sign out")
                },
                supportingContent = {
                    Text(text = "You can sign back in anytime")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch {
                        viewModel.syncManager.signOut()
                        automaticSyncEnabled = false
                        viewModel.syncManager.disableAutomaticSync(context)
                    }
                }
            )
        }
    }

    // Error display
    authErrorMessage?.let { error ->
        ListItem(
            headlineContent = {
                Text(
                    text = "Authentication Error",
                    color = MaterialTheme.colorScheme.error
                )
            },
            supportingContent = {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable { authErrorMessage = null }
        )
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
                val result = viewModel.syncManager.signIn(email, password)
                if (result.isSuccess) {
                    showAuthDialog = false
                    // Perform initial sync after first login
                    viewModel.syncManager.performInitialSync()
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
                val result = viewModel.syncManager.signUp(email, password)
                if (result.isSuccess) {
                    showAuthDialog = false
                    // Perform initial sync after successful signup
                    viewModel.syncManager.performInitialSync()
                } else {
                    authErrorMessage = result.exceptionOrNull()?.message ?: "Sign up failed"
                }
                isAuthLoading = false
            }
        }
    )
}
