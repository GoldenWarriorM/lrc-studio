package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@Composable
actual fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as java.awt.Frame?, "Select audio file", FileDialog.LOAD).apply {
                filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".ogg") ||
                    name.endsWith(".opus") || name.endsWith(".aac") || name.endsWith(".wav") ||
                    name.endsWith(".m4a")
                }
                isVisible = true
            }
            if (dialog.file != null) {
                onFilePicked(File(dialog.directory, dialog.file).absolutePath)
            }
        }
    }
}
