package com.lrcstudio.app.ui.picker

import android.content.ContentValues
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
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var pendingContent by remember { mutableStateOf<String?>(null) }
    val currentOnSuccess = rememberUpdatedState(onSuccess)
    val currentOnError = rememberUpdatedState(onError)

    val pickerLauncher = rememberLauncherForActivityResult(
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

    if (directory != null) {
        return remember(defaultName, directory) {
            { content: String ->
                val treeUri = Uri.parse(directory)
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val fileDocId = "$treeDocId/$defaultName"
                val fileUri = DocumentsContract.buildDocumentUri(treeUri.authority, fileDocId)

                val out = tryWriteDocUri(context, treeUri, treeDocId, fileUri, defaultName)
                if (out != null) {
                    try {
                        out.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        }
                        onSuccess()
                    } catch (e: Exception) {
                        onError("Save error: ${e.message}")
                    }
                } else {
                    pendingContent = content
                    pickerLauncher.launch(defaultName)
                }
            }
        }
    }

    return {
        pendingContent = it
        pickerLauncher.launch(defaultName)
    }
}

private fun tryWriteDocUri(
    context: android.content.Context,
    treeUri: Uri,
    treeDocId: String,
    fileUri: Uri,
    defaultName: String
): java.io.OutputStream? {
    val authority = treeUri.authority ?: return null
    val resolver = context.contentResolver
    val strategies = listOf(
        // 1 — open existing file
        { strategyOpenExisting(resolver, fileUri) },
        // 2 — createDocument on tree URI (official SAF)
        { strategyCreateDocument(resolver, treeUri, defaultName) },
        // 3 — insert on /document/root/children
        { strategyInsertChildren(resolver, authority, treeDocId, defaultName) },
    )
    for (strategy in strategies) {
        val out = try { strategy() } catch (_: Exception) { null }
        if (out != null) return out
    }
    return null
}

private fun strategyOpenExisting(resolver: android.content.ContentResolver, fileUri: Uri): java.io.OutputStream? {
    return try {
        resolver.openOutputStream(fileUri)
    } catch (_: SecurityException) {
        null
    }
}

private fun strategyCreateDocument(resolver: android.content.ContentResolver, treeUri: Uri, name: String): java.io.OutputStream? {
    val created = DocumentsContract.createDocument(resolver, treeUri, "text/plain", name)
    return if (created != null) resolver.openOutputStream(created) else null
}

private fun strategyInsertChildren(resolver: android.content.ContentResolver, authority: String, treeDocId: String, name: String): java.io.OutputStream? {
    val docUri = DocumentsContract.buildDocumentUri(authority, treeDocId)
    val insertUri = Uri.withAppendedPath(docUri, "children")
    val values = ContentValues().apply {
        put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
        put(DocumentsContract.Document.COLUMN_MIME_TYPE, "text/plain")
    }
    val created = resolver.insert(insertUri, values)
    return if (created != null) resolver.openOutputStream(created) else null
}
