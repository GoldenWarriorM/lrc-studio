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
actual fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                val path = NativeFileDialog.showOpenDialog(
                    title = "Select audio file",
                    extensions = listOf("mp3", "flac", "ogg", "opus", "aac", "wav", "m4a"),
                    isDark = isDark
                )
                if (path != null) {
                    withContext(Dispatchers.Main) { onFilePicked(path) }
                }
            }
        }
    }
}
