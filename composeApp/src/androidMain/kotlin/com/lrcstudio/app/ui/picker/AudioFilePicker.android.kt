package com.lrcstudio.app.ui.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@Composable
actual fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val localPath = copyToCache(context, uri)
                if (localPath != null) {
                    scope.launch(Dispatchers.Main) {
                        onFilePicked(localPath)
                    }
                }
            }
        }
    }

    return {
        launcher.launch(arrayOf("audio/mpeg", "audio/ogg", "audio/flac", "audio/opus", "audio/aac", "audio/x-m4a", "audio/wav", "audio/*"))
    }
}

private fun copyToCache(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val ext = getExtension(context, uri)
        val fileName = "audio_${System.currentTimeMillis()}$ext"
        val cacheDir = File(context.cacheDir, "audio_imports")
        cacheDir.mkdirs()
        val dest = File(cacheDir, fileName)
        inputStream.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun getExtension(context: Context, uri: Uri): String {
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType != null) {
        val ext = when {
            mimeType.contains("mpeg") -> ".mp3"
            mimeType.contains("opus") -> ".opus"
            mimeType.contains("ogg") -> ".ogg"
            mimeType.contains("flac") -> ".flac"
            mimeType.contains("aac") -> ".aac"
            mimeType.contains("x-m4a") -> ".m4a"
            mimeType.contains("wav") -> ".wav"
            mimeType.contains("mp4") -> ".m4a"
            else -> ""
        }
        if (ext.isNotEmpty()) return ext
    }
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0) {
                val name = it.getString(nameIdx)
                val dot = name.lastIndexOf('.')
                if (dot >= 0) return name.substring(dot)
            }
        }
    }
    return ""
}
