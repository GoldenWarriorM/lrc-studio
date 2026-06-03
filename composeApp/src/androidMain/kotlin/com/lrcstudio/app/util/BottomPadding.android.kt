package com.lrcstudio.app.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp

@Composable
actual fun fabBottomPadding(): Dp {
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val navBarInset = with(density) { navBarInsetPx.toDp() }
    return navBarInset + 80.dp
}
