package my.noveldokusha.tooling.local_server_sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.local_server_sync.auth.AuthState

@Composable
fun LocalServerAuthDialog(
    onDismiss: () -> Unit,
    viewModel: LocalServerAuthViewModel = hiltViewModel()
) {
    var showPassword by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (isRegisterMode) "Register to Local Server" else "Login to Local Server")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Server URL input
                OutlinedTextField(
                    value = viewModel.serverUrl,
                    onValueChange = viewModel::setServerUrl,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Username input
                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Email input (only for register mode)
                if (isRegisterMode) {
                    OutlinedTextField(
                        value = viewModel.email,
                        onValueChange = viewModel::setEmail,
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Password input
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = viewModel::setPassword,
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Error message
                if (viewModel.errorMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = viewModel.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Success message
                if (authState is AuthState.Authenticated) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Successfully authenticated as ${(authState as AuthState.Authenticated).username}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Toggle between login and register
                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        viewModel.clearError()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (isRegisterMode) "Already have an account? Login" else "Don't have an account? Register"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (isRegisterMode) {
                            viewModel.register()
                        } else {
                            viewModel.login()
                        }
                        if (viewModel.authState.value is AuthState.Authenticated) {
                            onDismiss()
                        }
                    }
                },
                enabled = authState !is AuthState.Loading &&
                         viewModel.username.isNotBlank() &&
                         viewModel.password.isNotBlank() &&
                         (!isRegisterMode || viewModel.email.isNotBlank())
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegisterMode) "Register" else "Login")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
