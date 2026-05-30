package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLrcFileSaveLauncher(defaultName: String = "lyrics.lrc"): (content: String) -> Unit
