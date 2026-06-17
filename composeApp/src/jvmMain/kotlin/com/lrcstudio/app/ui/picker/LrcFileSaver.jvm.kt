package com.lrcstudio.app.ui.picker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberLrcFileSaveLauncher(defaultName: String, directory: String?, onSuccess: () -> Unit, onError: (String) -> Unit): (content: String) -> Unit {
    if (directory != null) {
        return remember(defaultName, directory) {
            { content: String ->
                try {
                    java.io.File(directory, defaultName).writeText(content, Charsets.UTF_8)
                    onSuccess()
                } catch (e: Exception) {
                    onError(e.message ?: "Failed to save")
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentOnSuccess = rememberUpdatedState(onSuccess)
    val currentOnError = rememberUpdatedState(onError)
    return remember(defaultName) {
        { content: String ->
            scope.launch(Dispatchers.IO) {
                val path = NativeFileDialog.showSaveDialog(
                    title = "Save LRC file",
                    defaultName = defaultName,
                    isDark = isDark
                )
                if (path != null) {
                    try {
                        java.io.File(path).writeText(content, Charsets.UTF_8)
                        withContext(Dispatchers.Main) { currentOnSuccess.value() }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { currentOnError.value(e.message ?: "Failed to save") }
                    }
                }
            }
        }
    }
}
