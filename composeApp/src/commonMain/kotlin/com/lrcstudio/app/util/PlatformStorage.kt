package com.lrcstudio.app.util

import androidx.compose.runtime.Composable

expect fun readTextFile(path: String): String?

expect fun writeTextFile(path: String, content: String)

@Composable
expect fun rememberStorageDir(): String
