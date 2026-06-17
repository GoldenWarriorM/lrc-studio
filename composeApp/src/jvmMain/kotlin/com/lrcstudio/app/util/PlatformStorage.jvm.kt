package com.lrcstudio.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

actual fun lrcFileInDirectoryExists(directory: String, fileName: String): Boolean {
    return File(directory, fileName).exists()
}

@Composable
actual fun rememberFileExistsChecker(directory: String?): (String) -> Boolean {
    return { fileName -> directory != null && File(directory, fileName).exists() }
}

@Composable
actual fun rememberStorageDir(): String {
    return remember {
        File(System.getProperty("user.home"), ".lrcstudio").absolutePath.also {
            File(it).mkdirs()
        }
    }
}

actual fun treeUriToDisplayPathImpl(treeUriString: String): String = treeUriString
