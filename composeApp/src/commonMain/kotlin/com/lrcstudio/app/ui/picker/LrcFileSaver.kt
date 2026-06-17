package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLrcFileSaveLauncher(
    defaultName: String = "lyrics.lrc",
    directory: String? = null,
    onSuccess: () -> Unit = {},
    onError: (String) -> Unit = {}
): (content: String) -> Unit
