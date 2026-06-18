package com.lrcstudio.app.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable
actual fun cutoutPaddingValues(): PaddingValues {
    val view = LocalView.current
    val density = LocalDensity.current
    val cutout = view.rootWindowInsets?.displayCutout
    return if (cutout != null) {
        PaddingValues(
            start = with(density) { cutout.safeInsetLeft.toDp() },
            top = with(density) { cutout.safeInsetTop.toDp() },
            end = with(density) { cutout.safeInsetRight.toDp() },
            bottom = with(density) { cutout.safeInsetBottom.toDp() }
        )
    } else {
        PaddingValues(0.dp)
    }
}
