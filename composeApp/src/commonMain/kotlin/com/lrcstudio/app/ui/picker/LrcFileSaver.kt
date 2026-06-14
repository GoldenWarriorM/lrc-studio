package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLrcFileSaveLauncher(
    defaultName: String = "lyrics.lrc",
    directory: String? = null
): (content: String) -> Unit
