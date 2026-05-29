package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit {
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select audio file"
                fileFilter = FileNameExtensionFilter(
                    "Audio files (*.mp3, *.flac, *.ogg, *.opus, *.aac, *.wav, *.m4a)",
                    "mp3", "flac", "ogg", "opus", "aac", "wav", "m4a"
                )
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onFilePicked(chooser.selectedFile.absolutePath)
            }
        }
    }
}
