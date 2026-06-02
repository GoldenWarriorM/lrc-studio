package com.lrcstudio.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.repository.AppSettings
import com.lrcstudio.app.data.repository.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository
) {
    val settings by settingsRepository.settings.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleLarge)
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
            SettingsSection("Appearance") {
                SettingsRow(
                    title = "Dark theme",
                    subtitle = "Use dark color scheme",
                    trailing = {
                        Switch(
                            checked = settings.isDarkTheme,
                            onCheckedChange = { settingsRepository.toggleTheme() }
                        )
                    }
                )
            }

            SettingsSection("Layout") {
                SettingsRow(
                    title = "Compact controls",
                    subtitle = "Stack speed and timestamp buttons vertically",
                    trailing = {
                        Switch(
                            checked = settings.compactControls,
                            onCheckedChange = { settingsRepository.toggleCompactControls() }
                        )
                    }
                )
            }

            SettingsSection("Swipe") {
                var thresholdSlider by remember(settings.swipeDeleteThresholdDp) {
                    mutableFloatStateOf(settings.swipeDeleteThresholdDp.toFloat())
                }
                SettingsRow(
                    title = "Delete threshold",
                    subtitle = "${thresholdSlider.toInt()} dp — how far to swipe for Delete instead of Clear",
                    trailing = {
                        Text(
                            "${thresholdSlider.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    onValueChangeFinished = {
                        settingsRepository.setSwipeDeleteThresholdDp(thresholdSlider.toInt())
                    },
                    valueRange = 40f..200f,
                    steps = 15,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                SettingsRow(
                    title = "Swipe gestures",
                    subtitle = "Enable swipe-to-delete/clear/timestamp on lyric cards",
                    trailing = {
                        Switch(
                            checked = settings.swipeGesturesEnabled,
                            onCheckedChange = { settingsRepository.toggleSwipeGestures() }
                        )
                    }
                )
            }

            SettingsSection("About") {
                SettingsRow(
                    title = "Version",
                    subtitle = "1.0.0"
                )
                SettingsRow(
                    title = "LRC Studio",
                    subtitle = "Create and edit LRC lyrics easily"
                )
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
