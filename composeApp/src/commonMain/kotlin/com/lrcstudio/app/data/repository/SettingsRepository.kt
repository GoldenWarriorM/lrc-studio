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
    val compactControls: Boolean = false
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
