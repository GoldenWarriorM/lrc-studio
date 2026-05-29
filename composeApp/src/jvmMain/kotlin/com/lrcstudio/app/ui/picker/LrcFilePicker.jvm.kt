package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberLrcFilePickerLauncher(
    onContentLoaded: (String) -> Unit
): () -> Unit {
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select LRC file"
                fileFilter = FileNameExtensionFilter(
                    "LRC files (*.lrc, *.txt)",
                    "lrc", "txt"
                )
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                val content = file.readText()
                onContentLoaded(content)
            }
        }
    }
}
