package com.lrcstudio.app.util

import androidx.compose.runtime.Composable

expect fun readTextFile(path: String): String?

expect fun writeTextFile(path: String, content: String)

expect fun lrcFileInDirectoryExists(directory: String, fileName: String): Boolean

@Composable
expect fun rememberFileExistsChecker(directory: String?): (String) -> Boolean

@Composable
expect fun rememberStorageDir(): String
