package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit
