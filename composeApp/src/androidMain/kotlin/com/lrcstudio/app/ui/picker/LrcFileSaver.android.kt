package com.lrcstudio.app.ui.picker

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
actual fun rememberLrcFileSaveLauncher(defaultName: String, directory: String?): (content: String) -> Unit {
    val context = LocalContext.current
    if (directory != null) {
        return remember(defaultName, directory) {
            { content: String ->
                try {
                    val treeUri = Uri.parse(directory)
                    val docUri = DocumentsContract.createDocument(
                        context.contentResolver, treeUri,
                        "text/plain", defaultName
                    )
                    if (docUri != null) {
                        context.contentResolver.openOutputStream(docUri)?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val content = pendingContent ?: return@rememberLauncherForActivityResult
        pendingContent = null
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    return {
        pendingContent = it
        launcher.launch(defaultName)
    }
}
