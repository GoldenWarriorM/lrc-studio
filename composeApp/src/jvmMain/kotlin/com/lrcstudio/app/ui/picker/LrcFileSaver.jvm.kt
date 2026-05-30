package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File

@Composable
actual fun rememberLrcFileSaveLauncher(): (content: String) -> Unit {
    return remember {
        { content: String ->
            val dialog = FileDialog(null as java.awt.Frame?, "Save LRC file", FileDialog.SAVE).apply {
                file = "lyrics.lrc"
                isVisible = true
            }
            if (dialog.file != null) {
                val file = if (dialog.file!!.endsWith(".lrc")) {
                    File(dialog.directory, dialog.file)
                } else {
                    File(dialog.directory, dialog.file + ".lrc")
                }
                file.writeText(content, Charsets.UTF_8)
            }
        }
    }
}
