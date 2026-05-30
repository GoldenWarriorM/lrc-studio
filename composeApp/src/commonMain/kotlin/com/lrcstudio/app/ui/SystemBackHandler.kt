package com.lrcstudio.app.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
