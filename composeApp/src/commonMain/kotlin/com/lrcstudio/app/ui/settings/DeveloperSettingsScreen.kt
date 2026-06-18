package com.lrcstudio.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val settings by settingsRepository.settings.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Developer", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection("Layout") {
                SettingsRow(
                    title = "Force landscape editor",
                    subtitle = "Split screen: lyrics left, controls right",
                    trailing = {
                        AccentSwitch(
                            checked = settings.forceLandscapeEditor,
                            onCheckedChange = { settingsRepository.toggleForceLandscapeEditor() }
                        )
                    }
                )
                SettingsRow(
                    title = "Invert landscape sides",
                    subtitle = "Swap lyrics and controls sides",
                    trailing = {
                        AccentSwitch(
                            checked = settings.invertLandscapeSides,
                            onCheckedChange = { settingsRepository.toggleInvertLandscapeSides() }
                        )
                    }
                )
            }

            SettingsSection("Debug") {
                SettingsRow(
                    title = "Debug borders",
                    subtitle = "Show borders around gradient overlay areas",
                    trailing = {
                        AccentSwitch(
                            checked = settings.showDebugBorders,
                            onCheckedChange = { settingsRepository.toggleShowDebugBorders() }
                        )
                    }
                )
                SettingsRow(
                    title = "Vibration toast",
                    subtitle = "Show a snackbar when haptic feedback triggers",
                    trailing = {
                        AccentSwitch(
                            checked = settings.showVibrationToast,
                            onCheckedChange = { settingsRepository.toggleVibrationToast() }
                        )
                    }
                )
            }

            SettingsSection("Platform") {
                SettingsRow(
                    title = "Show platform-specific options",
                    subtitle = "Show settings from other platforms for testing",
                    trailing = {
                        AccentSwitch(
                            checked = settings.showPlatformSpecific,
                            onCheckedChange = { settingsRepository.toggleShowPlatformSpecific() }
                        )
                    }
                )
            }

            SettingsSection("Animation") {
                SettingsRow(
                    title = "Slow animations",
                    subtitle = "Slow down all screen transitions by 3x",
                    trailing = {
                        AccentSwitch(
                            checked = settings.slowAnimations,
                            onCheckedChange = { settingsRepository.toggleSlowAnimations() }
                        )
                    }
                )
            }

            var showResetDialog by remember { mutableStateOf(false) }
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text("Reset settings", style = MaterialTheme.typography.headlineSmall)
                    },
                    text = { Text("This will reset all settings to their default values.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                settingsRepository.resetToDefaults()
                                showResetDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            SettingsSection("Reset") {
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset all settings to defaults")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
