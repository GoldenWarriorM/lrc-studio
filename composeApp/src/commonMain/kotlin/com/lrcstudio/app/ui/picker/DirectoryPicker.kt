package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberDirectoryPickerLauncher(onResult: (String?) -> Unit): () -> Unit
