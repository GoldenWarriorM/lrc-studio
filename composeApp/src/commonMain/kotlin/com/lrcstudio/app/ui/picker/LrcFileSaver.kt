package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLrcFileSaveLauncher(): (content: String) -> Unit
