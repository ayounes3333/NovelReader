package my.noveldokusha.tooling.local_server_sync.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ListItem
import my.noveldoksuha.coreui.theme.Success500
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldokusha.tooling.local_server_sync.auth.AuthState

@Composable
fun LocalServerSyncPreferencesSection(
    modifier: Modifier = Modifier,
    viewModel: LocalServerSyncViewModel = hiltViewModel()
) {
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var showAddBssidDialog by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val serverUrls by viewModel.serverUrls.collectAsState()
    val homeBssids by viewModel.homeBssids.collectAsState()
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Connection / account row
        when (val state = authState) {
            is AuthState.Authenticated -> {
                ListItem(
                    headlineContent = { Text("Connected as ${state.username}") },
                    supportingContent = { Text(state.email) },
                    leadingContent = {
                        Icon(Icons.Default.AccountCircle, null, tint = onPrimary)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.CheckCircle, "Connected", tint = Success500)
                    }
                )
            }
            is AuthState.Loading -> {
                ListItem(
                    headlineContent = { Text("Connecting…") },
                    leadingContent = {
                        Icon(Icons.Default.AccountCircle, null, tint = onPrimary)
                    },
                    trailingContent = {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                )
            }
            else -> {
                ListItem(
                    headlineContent = { Text("Connect to local server") },
                    supportingContent = { Text("Sign in to sync your library with your own server") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Login, null, tint = onPrimary)
                    },
                    modifier = Modifier.clickable { showAuthDialog = true }
                )
            }
        }

        if (authState is AuthState.Authenticated) {
            // Sync now
            ListItem(
                headlineContent = { Text("Sync now") },
                supportingContent = {
                    when (val s = syncState) {
                        is SyncState.Syncing -> Column {
                            Text("Syncing: ${s.operation}")
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        }
                        is SyncState.Success -> Text("Last sync: ${s.timestamp}")
                        is SyncState.Error -> Text(
                            text = "Sync error: ${s.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text("Upload progress and pull new chapters")
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Sync, null, tint = onPrimary)
                },
                trailingContent = if (syncState is SyncState.Syncing) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else null,
                modifier = Modifier.clickable(enabled = syncState !is SyncState.Syncing) {
                    viewModel.syncLibrary()
                }
            )
            // Full sync
            ListItem(
                headlineContent = { Text("Full sync") },
                supportingContent = { Text("Re-download the whole library from the server") },
                leadingContent = {
                    Icon(Icons.Filled.CloudDownload, null, tint = onPrimary)
                },
                modifier = Modifier.clickable(enabled = syncState !is SyncState.Syncing) {
                    viewModel.syncComplete()
                }
            )
            // Auto sync
            ListItem(
                headlineContent = { Text("Auto-sync on Wi-Fi") },
                supportingContent = { Text("Automatically sync when connected to a network with the server") },
                leadingContent = {
                    Icon(Icons.Filled.Wifi, null, tint = onPrimary)
                },
                trailingContent = {
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                    )
                },
                modifier = Modifier.clickable { viewModel.setAutoSyncEnabled(!autoSyncEnabled) }
            )
            // Server addresses
            ListItem(
                headlineContent = { Text("Server addresses") },
                supportingContent = {
                    if (serverUrls.isEmpty()) {
                        Text("No server addresses configured")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            serverUrls.forEach { url ->
                                RemovableRow(text = url, onRemove = { viewModel.removeServerUrl(url) })
                            }
                        }
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Dns, null, tint = onPrimary)
                },
                trailingContent = {
                    IconButton(onClick = { showAddUrlDialog = true }) {
                        Icon(Icons.Filled.Add, "Add server URL", tint = onPrimary)
                    }
                }
            )
            // Home Wi-Fi networks
            ListItem(
                headlineContent = { Text("Home Wi-Fi networks") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (homeBssids.isEmpty())
                                "Any Wi-Fi will trigger bulk sync. Add the current network to restrict."
                            else
                                "Bulk sync runs only when connected to a listed network.",
                            color = muted
                        )
                        homeBssids.forEach { bssid ->
                            RemovableRow(text = bssid, onRemove = { viewModel.removeHomeBssid(bssid) })
                        }
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Home, null, tint = onPrimary)
                },
                trailingContent = {
                    IconButton(onClick = { showAddBssidDialog = true }) {
                        Icon(Icons.Filled.Add, "Add home Wi-Fi", tint = onPrimary)
                    }
                }
            )
            // Sign out
            ListItem(
                headlineContent = { Text("Sign out") },
                supportingContent = { Text("You can sign back in anytime") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = onPrimary)
                },
                modifier = Modifier.clickable(enabled = syncState !is SyncState.Syncing) {
                    viewModel.signOut()
                }
            )
        }
    }

    // Show auth dialog when needed
    if (showAuthDialog) {
        LocalServerAuthDialog(
            onDismiss = { showAuthDialog = false }
        )
    }

    // Add server URL dialog
    if (showAddUrlDialog) {
        var urlInput by remember { mutableStateOf("http://") }
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            title = { Text("Add Server Address") },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addServerUrl(urlInput)
                        showAddUrlDialog = false
                    },
                    enabled = urlInput.startsWith("http")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add home WiFi BSSID dialog
    if (showAddBssidDialog) {
        val ctx = LocalContext.current
        var hasLocationPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        var detected by remember(hasLocationPermission) {
            mutableStateOf(if (hasLocationPermission) viewModel.getCurrentBssid() else null)
        }
        var bssidInput by remember { mutableStateOf(detected.orEmpty()) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasLocationPermission = granted
            if (granted) {
                val current = viewModel.getCurrentBssid()
                detected = current
                if (!current.isNullOrBlank()) bssidInput = current
            }
        }

        AlertDialog(
            onDismissRequest = { showAddBssidDialog = false },
            title = { Text("Add Home WiFi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val helper = when {
                        !hasLocationPermission ->
                            "Location permission is required to read the current WiFi BSSID. Grant it, or paste the BSSID manually (aa:bb:cc:dd:ee:ff)."
                        detected != null ->
                            "Detected current WiFi BSSID. Tap Add to mark this network as home."
                        else ->
                            "Could not read BSSID. Make sure WiFi is on, or paste it manually (aa:bb:cc:dd:ee:ff)."
                    }
                    Text(text = helper, style = MaterialTheme.typography.bodySmall)

                    if (!hasLocationPermission) {
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        ) { Text("Grant location permission") }
                    }

                    OutlinedTextField(
                        value = bssidInput,
                        onValueChange = { bssidInput = it.lowercase() },
                        label = { Text("BSSID") },
                        placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addHomeBssid(bssidInput)
                        showAddBssidDialog = false
                    },
                    enabled = bssidInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBssidDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemovableRow(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(16.dp))
        }
    }
}
