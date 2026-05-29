package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLrcFilePickerLauncher(
    onContentLoaded: (String) -> Unit
): () -> Unit
