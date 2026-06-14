package com.lrcstudio.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

actual fun readTextFile(path: String): String? {
    return try {
        File(path).takeIf { it.exists() }?.readText()
    } catch (e: Exception) { null }
}

actual fun writeTextFile(path: String, content: String) {
    File(path).parentFile?.mkdirs()
    File(path).writeText(content)
}

actual fun lrcFileInDirectoryExists(directory: String, fileName: String): Boolean = false

@Composable
actual fun rememberStorageDir(): String {
    val context = LocalContext.current
    return remember {
        File(context.filesDir, "lrcstudio").absolutePath.also {
            File(it).mkdirs()
        }
    }
}
