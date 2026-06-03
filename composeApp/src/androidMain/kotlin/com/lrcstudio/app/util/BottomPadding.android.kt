package com.lrcstudio.app.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun fabBottomPadding(): Dp {
    val density = LocalDensity.current
    val navBarInset = WindowInsets.navigationBars.getBottom(density)
    return navBarInset + 80.dp
}
