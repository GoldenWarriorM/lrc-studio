package com.lrcstudio.app.ui.settings

import androidx.compose.foundation.clickable
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
import com.lrcstudio.app.util.fabBottomPadding
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val settings by settingsRepository.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .padding(16.dp)
                .padding(bottom = fabBottomPadding()),
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

            SettingsSection("Swipe") {
                var activationSlider by remember(settings.swipeActivationThresholdDp) {
                    mutableFloatStateOf(settings.swipeActivationThresholdDp.toFloat())
                }
                SettingsRow(
                    title = "Activation threshold",
                    subtitle = "${activationSlider.toInt()} dp — how far to swipe before Clear/Time triggers",
                    trailing = {
                        Text(
                            "${activationSlider.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                Slider(
                    value = activationSlider,
                    onValueChange = { activationSlider = it },
                    onValueChangeFinished = {
                        settingsRepository.setSwipeActivationThresholdDp(activationSlider.toInt())
                    },
                    valueRange = 50f..150f,
                    steps = 9,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                var deleteSlider by remember(settings.swipeDeleteThresholdDp) {
                    mutableFloatStateOf(settings.swipeDeleteThresholdDp.toFloat())
                }
                SettingsRow(
                    title = "Delete offset",
                    subtitle = "${deleteSlider.toInt()} dp extra beyond activation for Delete",
                    trailing = {
                        Text(
                            "${deleteSlider.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                Slider(
                    value = deleteSlider,
                    onValueChange = { deleteSlider = it },
                    onValueChangeFinished = {
                        settingsRepository.setSwipeDeleteThresholdDp(deleteSlider.toInt())
                    },
                    valueRange = 50f..150f,
                    steps = 9,
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

            SettingsSection("Buttons") {
                SettingsRow(
                    title = "Snap button",
                    subtitle = "Show snap-to-current-position button",
                    trailing = {
                        Switch(
                            checked = settings.showSnapButton,
                            onCheckedChange = { settingsRepository.toggleSnapButton() }
                        )
                    }
                )
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
                SettingsRow(
                    title = "Clear / Delete button",
                    subtitle = "Show clear-timestamp / delete-line button",
                    trailing = {
                        Switch(
                            checked = settings.showClearDeleteButton,
                            onCheckedChange = { settingsRepository.toggleClearDeleteButton() }
                        )
                    }
                )
                SettingsRow(
                    title = "Instant delete",
                    subtitle = "Skip confirmation dialog when swiping to delete",
                    trailing = {
                        Switch(
                            checked = settings.swipeInstantDelete,
                            onCheckedChange = { settingsRepository.toggleSwipeInstantDelete() }
                        )
                    }
                )
                SettingsRow(
                    title = "Undo / Redo buttons",
                    subtitle = "Show floating undo and redo buttons in the editor",
                    trailing = {
                        Switch(
                            checked = settings.showUndoRedo,
                            onCheckedChange = { settingsRepository.toggleUndoRedo() }
                        )
                    }
                )
            }

            SettingsSection("About") {
                var versionTapCount by remember { mutableIntStateOf(0) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(versionTapCount) {
                    if (versionTapCount >= 7) {
                        val wasUnlocked = settings.devSettingsUnlocked
                        settingsRepository.toggleDevSettingsUnlocked()
                        versionTapCount = 0
                        scope.launch {
                            val msg = if (wasUnlocked) "Developer settings hidden" else "Developer settings activated"
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .clickable { versionTapCount++ },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Version",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SettingsRow(
                    title = "LRC Studio",
                    subtitle = "Create and edit LRC lyrics easily"
                )
                if (settings.devSettingsUnlocked) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDeveloper() }
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Developer settings",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
