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
actual fun rememberLrcFileSaveLauncher(defaultName: String): (content: String) -> Unit {
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(defaultName) {
        { content: String ->
            scope.launch(Dispatchers.IO) {
                val path = NativeFileDialog.showSaveDialog(
                    title = "Save LRC file",
                    defaultName = defaultName,
                    isDark = isDark
                )
                if (path != null) {
                    java.io.File(path).writeText(content, Charsets.UTF_8)
                }
            }
        }
    }
}
