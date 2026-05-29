package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberLrcFileSaveLauncher(): (content: String) -> Unit {
    return remember {
        { content: String ->
            val chooser = JFileChooser().apply {
                dialogTitle = "Save LRC file"
                fileFilter = FileNameExtensionFilter("LRC files (*.lrc)", "lrc")
                selectedFile = File("lyrics.lrc")
            }
            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.writeText(content, Charsets.UTF_8)
            }
        }
    }
}
