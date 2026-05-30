package com.lrcstudio.app.ui.picker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberLrcFilePickerLauncher(
    onContentLoaded: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                val path = NativeFileDialog.showOpenDialog(
                    title = "Select LRC file",
                    extensions = listOf("lrc"),
                    isDark = isDark
                )
                if (path != null) {
                    try {
                        val content = java.io.File(path).readText(Charsets.UTF_8)
                        withContext(Dispatchers.Main) { onContentLoaded(content) }
                    } catch (e: Exception) {
                        println("LrcFilePicker: failed to read $path: $e")
                    }
                }
            }
        }
    }
}