package com.lrcstudio.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op: desktop has no system back gesture
}
