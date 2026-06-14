package com.lrcstudio.app.ui.picker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance

@Composable
actual fun rememberDirectoryPickerLauncher(onResult: (String?) -> Unit): () -> Unit {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember {
        {
            val path = NativeFileDialog.showDirectoryDialog(
                title = "Select LRC save folder",
                isDark = isDark
            )
            onResult(path)
        }
    }
}
