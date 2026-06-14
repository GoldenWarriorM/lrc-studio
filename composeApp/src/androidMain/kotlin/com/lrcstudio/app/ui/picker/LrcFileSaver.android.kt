package com.lrcstudio.app.ui.picker

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberLrcFileSaveLauncher(defaultName: String, directory: String?, onSuccess: () -> Unit, onError: (String) -> Unit): (content: String) -> Unit {
    val context = LocalContext.current
    if (directory != null) {
        return remember(defaultName, directory) {
            { content: String ->
                try {
                    val treeUri = Uri.parse(directory)
                    val created = DocumentsContract.createDocument(
                        context.contentResolver, treeUri,
                        "text/plain", defaultName
                    )
                    if (created != null) {
                        context.contentResolver.openOutputStream(created)?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        }
                        onSuccess()
                    } else {
                        onError("Failed to create file in selected directory")
                    }
                } catch (e: Exception) {
                    onError(e.message ?: "Failed to save")
                }
            }
        }
    }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var pendingContent by remember { mutableStateOf<String?>(null) }
    val currentOnSuccess = rememberUpdatedState(onSuccess)
    val currentOnError = rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val content = pendingContent ?: return@rememberLauncherForActivityResult
        pendingContent = null
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) { currentOnSuccess.value() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { currentOnError.value(e.message ?: "Failed to save") }
                }
            }
        }
    }

    return {
        pendingContent = it
        launcher.launch(defaultName)
    }
}
