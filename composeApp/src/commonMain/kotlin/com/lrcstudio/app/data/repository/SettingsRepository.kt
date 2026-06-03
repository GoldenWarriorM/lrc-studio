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
    val swipeDeleteThresholdDp: Int = 130,
    val swipeGesturesEnabled: Boolean = true,
    val showSnapButton: Boolean = true,
    val showClearDeleteButton: Boolean = true,
    val swipeInstantDelete: Boolean = false,
    val devSettingsUnlocked: Boolean = false,
    val showDebugBorders: Boolean = false,
    val slowAnimations: Boolean = false
)

class SettingsRepository(private val storageDir: String) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    private val jsonLibrary = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        loadFromDisk()
    }

    fun toggleTheme() {
        _settings.value = _settings.value.copy(
            isDarkTheme = !_settings.value.isDarkTheme
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
            swipeDeleteThresholdDp = value.coerceIn(40, 200)
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
