package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@Composable
actual fun rememberLrcFilePickerLauncher(
    onContentLoaded: (String) -> Unit
): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as java.awt.Frame?, "Select LRC file", FileDialog.LOAD).apply {
                filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".lrc") || name.endsWith(".txt")
                }
                isVisible = true
            }
            if (dialog.file != null) {
                val file = File(dialog.directory, dialog.file)
                val content = file.readText()
                onContentLoaded(content)
            }
        }
    }
}
