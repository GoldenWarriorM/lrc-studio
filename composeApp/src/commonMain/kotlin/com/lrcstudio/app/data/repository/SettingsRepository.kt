package com.lrcstudio.app.data.repository

import com.lrcstudio.app.util.readTextFile
import com.lrcstudio.app.util.writeTextFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    val isDarkTheme: Boolean = true,
    val timestampFormat: String = "mm:ss.xx",
    val compactControls: Boolean = false,
    val swipeDeleteThresholdDp: Int = 60,
    val swipeActivationThresholdDp: Int = 90,
    val swipeGesturesEnabled: Boolean = true,
    val showSnapButton: Boolean = true,
    val showClearDeleteButton: Boolean = true,
    val swipeInstantDelete: Boolean = false,
    val devSettingsUnlocked: Boolean = false,
    val showDebugBorders: Boolean = false,
    val slowAnimations: Boolean = false,
    val showUndoRedo: Boolean = true,
    val showVibrationToast: Boolean = false,
    val accentColorName: String = "Purple",
    val customAccentColor: String? = null,
    val lrcSaveDirectory: String? = null,
    val forceLandscapeEditor: Boolean = false,
    val invertLandscapeSides: Boolean = false,
    val ignoreCutout: Boolean = false,
    val showPlatformSpecific: Boolean = false,
    val landscapeSplitRatio: Float = 0.5f
)

class SettingsRepository(private val storageDir: String) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    private val jsonLibrary = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        loadFromDisk()
    }

    fun setDarkTheme(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            isDarkTheme = enabled
        )
        saveToDisk()
    }

    fun toggleCompactControls() {
        _settings.value = _settings.value.copy(
            compactControls = !_settings.value.compactControls
        )
        saveToDisk()
    }

    fun setSwipeDeleteThresholdDp(value: Int) {
        _settings.value = _settings.value.copy(
            swipeDeleteThresholdDp = value.coerceIn(20, 120)
        )
        saveToDisk()
    }

    fun setSwipeActivationThresholdDp(value: Int) {
        _settings.value = _settings.value.copy(
            swipeActivationThresholdDp = value.coerceIn(50, 150)
        )
        saveToDisk()
    }

    fun toggleSwipeGestures() {
        _settings.value = _settings.value.copy(
            swipeGesturesEnabled = !_settings.value.swipeGesturesEnabled
        )
        saveToDisk()
    }

    fun toggleSnapButton() {
        _settings.value = _settings.value.copy(
            showSnapButton = !_settings.value.showSnapButton
        )
        saveToDisk()
    }

    fun toggleClearDeleteButton() {
        _settings.value = _settings.value.copy(
            showClearDeleteButton = !_settings.value.showClearDeleteButton
        )
        saveToDisk()
    }

    fun toggleSwipeInstantDelete() {
        _settings.value = _settings.value.copy(
            swipeInstantDelete = !_settings.value.swipeInstantDelete
        )
        saveToDisk()
    }

    fun toggleDevSettingsUnlocked() {
        _settings.value = _settings.value.copy(
            devSettingsUnlocked = !_settings.value.devSettingsUnlocked
        )
        saveToDisk()
    }

    fun toggleShowDebugBorders() {
        _settings.value = _settings.value.copy(
            showDebugBorders = !_settings.value.showDebugBorders
        )
        saveToDisk()
    }

    fun toggleSlowAnimations() {
        _settings.value = _settings.value.copy(
            slowAnimations = !_settings.value.slowAnimations
        )
        saveToDisk()
    }

    fun toggleUndoRedo() {
        _settings.value = _settings.value.copy(
            showUndoRedo = !_settings.value.showUndoRedo
        )
        saveToDisk()
    }

    fun toggleVibrationToast() {
        _settings.value = _settings.value.copy(
            showVibrationToast = !_settings.value.showVibrationToast
        )
        saveToDisk()
    }

    fun setAccentColor(name: String) {
        _settings.value = _settings.value.copy(
            accentColorName = name
        )
        saveToDisk()
    }

    fun setCustomAccentColor(hex: String?) {
        _settings.value = _settings.value.copy(
            customAccentColor = hex,
            accentColorName = if (hex != null) "Custom" else "Purple"
        )
        saveToDisk()
    }

    fun toggleForceLandscapeEditor() {
        _settings.value = _settings.value.copy(
            forceLandscapeEditor = !_settings.value.forceLandscapeEditor
        )
        saveToDisk()
    }

    fun toggleInvertLandscapeSides() {
        _settings.value = _settings.value.copy(
            invertLandscapeSides = !_settings.value.invertLandscapeSides
        )
        saveToDisk()
    }

    fun toggleIgnoreCutout() {
        _settings.value = _settings.value.copy(
            ignoreCutout = !_settings.value.ignoreCutout
        )
        saveToDisk()
    }

    fun toggleShowPlatformSpecific() {
        _settings.value = _settings.value.copy(
            showPlatformSpecific = !_settings.value.showPlatformSpecific
        )
        saveToDisk()
    }

    fun setLandscapeSplitRatio(ratio: Float) {
        _settings.value = _settings.value.copy(
            landscapeSplitRatio = ratio.coerceIn(0.15f, 0.85f)
        )
        saveToDisk()
    }

    fun setLrcSaveDirectory(path: String?) {
        _settings.value = _settings.value.copy(
            lrcSaveDirectory = path
        )
        saveToDisk()
    }

    fun resetToDefaults() {
        _settings.value = AppSettings()
        saveToDisk()
    }

    private fun settingsFile() = "$storageDir/settings.json"

    private fun saveToDisk() {
        try {
            val json = jsonLibrary.encodeToString(_settings.value)
            writeTextFile(settingsFile(), json)
        } catch (e: Exception) {
            println("SettingsRepository: failed to save — ${e.message}")
        }
    }

    private fun loadFromDisk() {
        try {
            val json = readTextFile(settingsFile()) ?: return
            _settings.value = jsonLibrary.decodeFromString(json)
        } catch (e: Exception) {
            println("SettingsRepository: failed to load — ${e.message}")
        }
    }
}
