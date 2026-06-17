package com.lrcstudio.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.repository.AppSettings
import com.lrcstudio.app.data.repository.SettingsRepository
import com.lrcstudio.app.theme.AccentPreset
import com.lrcstudio.app.theme.LocalSnapSurface
import com.lrcstudio.app.ui.components.ColorPickerDialog
import com.lrcstudio.app.ui.components.parseHexColor
import com.lrcstudio.app.ui.components.toHex
import com.lrcstudio.app.ui.picker.rememberDirectoryPickerLauncher
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
    val topBarSurface = LocalSnapSurface.current

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
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
                        AccentSwitch(
                            checked = settings.isDarkTheme,
                            onCheckedChange = { settingsRepository.setDarkTheme(it) }
                        )
                    }
                )
                AccentColorPicker(
                    selected = AccentPreset.fromName(settings.accentColorName),
                    customAccentColor = settings.customAccentColor,
                    onSelect = {
                        settingsRepository.setCustomAccentColor(null)
                        settingsRepository.setAccentColor(it.label)
                    },
                    onCustomSelect = { settingsRepository.setCustomAccentColor(it) }
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        thumbColor = MaterialTheme.colorScheme.primary,
                    )
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
                    valueRange = 20f..120f,
                    steps = 9,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        thumbColor = MaterialTheme.colorScheme.primary,
                    )
                )
                SettingsRow(
                    title = "Swipe gestures",
                    subtitle = "Enable swipe-to-delete/clear/timestamp on lyric cards",
                    trailing = {
                        AccentSwitch(
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
                        AccentSwitch(
                            checked = settings.showSnapButton,
                            onCheckedChange = { settingsRepository.toggleSnapButton() }
                        )
                    }
                )
                SettingsRow(
                    title = "Compact controls",
                    subtitle = "Stack speed and timestamp buttons vertically",
                    trailing = {
                        AccentSwitch(
                            checked = settings.compactControls,
                            onCheckedChange = { settingsRepository.toggleCompactControls() }
                        )
                    }
                )
                SettingsRow(
                    title = "Clear / Delete button",
                    subtitle = "Show clear-timestamp / delete-line button",
                    trailing = {
                        AccentSwitch(
                            checked = settings.showClearDeleteButton,
                            onCheckedChange = { settingsRepository.toggleClearDeleteButton() }
                        )
                    }
                )
                SettingsRow(
                    title = "Instant delete",
                    subtitle = "Skip confirmation dialog when swiping to delete",
                    trailing = {
                        AccentSwitch(
                            checked = settings.swipeInstantDelete,
                            onCheckedChange = { settingsRepository.toggleSwipeInstantDelete() }
                        )
                    }
                )
                SettingsRow(
                    title = "Undo / Redo buttons",
                    subtitle = "Show floating undo and redo buttons in the editor",
                    trailing = {
                        AccentSwitch(
                            checked = settings.showUndoRedo,
                            onCheckedChange = { settingsRepository.toggleUndoRedo() }
                        )
                    }
                )
            }

            SettingsSection("Export") {
                val pickDirectory = rememberDirectoryPickerLauncher { path ->
                    if (path != null) {
                        settingsRepository.setLrcSaveDirectory(path)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LRC save folder",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            text = settings.lrcSaveDirectory
                                ?: "Not set — will ask each time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.lrcSaveDirectory != null) {
                            TextButton(onClick = { settingsRepository.setLrcSaveDirectory(null) }) {
                                Text("Clear")
                            }
                        }
                        Button(
                            onClick = { pickDirectory() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Choose")
                        }
                    }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPicker(
    selected: AccentPreset,
    customAccentColor: String?,
    onSelect: (AccentPreset) -> Unit,
    onCustomSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val customColor = remember(customAccentColor) {
        customAccentColor?.let { parseHexColor(it) }
    }
    val isCustom = customAccentColor != null

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "Accent color",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentColorOption(AccentPreset.Purple, selected == AccentPreset.Purple && !isCustom, onClick = { onSelect(AccentPreset.Purple) })
            AccentColorOption(AccentPreset.Blue, selected == AccentPreset.Blue && !isCustom, onClick = { onSelect(AccentPreset.Blue) })
            AccentColorOption(AccentPreset.Green, selected == AccentPreset.Green && !isCustom, onClick = { onSelect(AccentPreset.Green) })
            AccentColorOption(AccentPreset.Orange, selected == AccentPreset.Orange && !isCustom, onClick = { onSelect(AccentPreset.Orange) })
            AccentColorOption(AccentPreset.Pink, selected == AccentPreset.Pink && !isCustom, onClick = { onSelect(AccentPreset.Pink) })
            AccentColorOption(AccentPreset.Teal, selected == AccentPreset.Teal && !isCustom, onClick = { onSelect(AccentPreset.Teal) })
            CustomColorOption(
                color = customColor,
                isSelected = isCustom,
                onClick = { showColorPicker = true }
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = customColor ?: AccentPreset.Purple.lightPrimary,
            onColorSelected = { color ->
                onCustomSelect(color.toHex())
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun CustomColorOption(
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .let { mod ->
                    if (color != null) mod.background(color)
                    else mod.background(Color.Gray.copy(alpha = 0.3f))
                }
                .let { mod ->
                    if (isSelected) mod.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else mod.border(1.dp, (color ?: Color.Gray).copy(alpha = 0.3f), CircleShape)
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else if (color == null) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Custom color",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Custom",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AccentSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    )
}

@Composable
private fun AccentColorOption(
    preset: AccentPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(preset.lightPrimary)
                .then(
                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier.border(1.dp, preset.lightPrimary.copy(alpha = 0.3f), CircleShape)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = preset.lightOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
