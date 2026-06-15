package com.lrcstudio.app.ui.picker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
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
import java.io.File
import java.io.FileOutputStream

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
                    onError("Failed to save — unable to write to the selected directory")
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
    context: Context,
    treeUri: Uri,
    treeDocId: String,
    fileUri: Uri,
    defaultName: String
): java.io.OutputStream? {
    val authority = treeUri.authority ?: return null
    val resolver = context.contentResolver

    val strategies = listOf(
        { strategyOpenExisting(resolver, fileUri) },
        { strategyDeleteThenCreate(resolver, treeUri, fileUri, defaultName) },
        { strategyInsertChildren(resolver, authority, treeDocId, defaultName) },
    )
    for (strategy in strategies) {
        val out = try { strategy() } catch (_: Exception) { null }
        if (out != null) return out
    }

    return strategyFileFallback(context, treeUri, defaultName)
}

private fun strategyOpenExisting(resolver: android.content.ContentResolver, fileUri: Uri): java.io.OutputStream? {
    return try {
        resolver.openOutputStream(fileUri)
    } catch (_: SecurityException) {
        null
    }
}

private fun strategyDeleteThenCreate(
    resolver: android.content.ContentResolver,
    treeUri: Uri,
    fileUri: Uri,
    name: String
): java.io.OutputStream? {
    try {
        deleteDocumentInTree(resolver, treeUri, name)
    } catch (_: Exception) {}

    val created = DocumentsContract.createDocument(resolver, treeUri, "text/plain", name)
    return if (created != null) resolver.openOutputStream(created) else null
}

private fun deleteDocumentInTree(resolver: android.content.ContentResolver, treeUri: Uri, fileName: String) {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    val cursor = resolver.query(childrenUri, null, null, null, null) ?: return

    cursor.use {
        while (it.moveToNext()) {
            val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            if (displayName == fileName) {
                val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val docUri = treeUri.buildUpon()
                    .appendEncodedPath("document")
                    .appendEncodedPath(Uri.encode(docId))
                    .build()
                DocumentsContract.deleteDocument(resolver, docUri)
                return
            }
        }
    }
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

private fun strategyFileFallback(context: Context, treeUri: Uri, name: String): java.io.OutputStream? {
    try {
        val realPath = getFullPathFromTreeUri(treeUri, context) ?: return null
        val file = File(realPath, name)
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    } catch (_: Exception) { return null }
}

private fun getFullPathFromTreeUri(treeUri: Uri, context: Context): String? {
    if (treeUri.toString() == "content://com.android.providers.downloads.documents/tree/downloads") {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    val volumeId = getVolumeIdFromTreeUri(treeUri)
    val volumePath = getVolumePath(volumeId, context) ?: return null

    var documentPath = getDocumentPathFromTreeUri(treeUri)
    if (documentPath.endsWith("/")) documentPath = documentPath.substring(0, documentPath.length - 1)

    return if (documentPath.isNotEmpty()) {
        if (documentPath.startsWith("/")) "$volumePath$documentPath"
        else "$volumePath/$documentPath"
    } else volumePath
}

private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val split = docId.split(":")
    return if (split.isNotEmpty()) split[0] else null
}

private fun getDocumentPathFromTreeUri(treeUri: Uri): String {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val split = docId.split(":")
    return if (split.size >= 2) split[1] else "/"
}

private fun getVolumePath(volumeId: String?, context: Context): String? {
    if (volumeId == null) return null
    return try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumeClass = Class.forName("android.os.storage.StorageVolume")
        val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
        val getUuid = storageVolumeClass.getMethod("getUuid")
        val getPath = storageVolumeClass.getMethod("getPath")
        val isPrimary = storageVolumeClass.getMethod("isPrimary")

        val result = getVolumeList.invoke(storageManager)
        val length = java.lang.reflect.Array.getLength(result)

        for (i in 0 until length) {
            val storageVolume = java.lang.reflect.Array.get(result, i)!!
            val uuid = getUuid.invoke(storageVolume) as? String
            val primary = isPrimary.invoke(storageVolume) as Boolean

            if (primary && "primary" == volumeId) {
                return getPath.invoke(storageVolume) as String
            }
            if (uuid != null && uuid == volumeId) {
                return getPath.invoke(storageVolume) as String
            }
        }
        null
    } catch (e: Exception) { null }
}
