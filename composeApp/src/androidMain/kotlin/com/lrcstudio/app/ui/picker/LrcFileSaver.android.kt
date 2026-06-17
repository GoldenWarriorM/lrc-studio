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
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
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

                val out = tryWriteDocUri(context, treeUri, treeDocId, defaultName)
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

private fun sanitizeFileName(name: String): String {
    // SAF providers (especially on Android 16+) replace ':' with '_' in filenames
    return name.replace(':', '_')
}

private fun tryWriteDocUri(
    context: Context,
    treeUri: Uri,
    treeDocId: String,
    defaultName: String
): java.io.OutputStream? {
    val name = sanitizeFileName(defaultName)
    val resolver = context.contentResolver

    val sanitizedFileDocId = "$treeDocId/$name"
    for (uri in openExistingUris(treeUri, sanitizedFileDocId)) {
        val out = try { resolver.openOutputStream(uri) } catch (_: Exception) { null }
        if (out != null) return out
    }

    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val cursor = resolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                if (displayName == name) {
                    val docUri = treeUri.buildUpon()
                        .appendEncodedPath("document")
                        .appendEncodedPath(Uri.encode(docId))
                        .build()
                    val out = try { resolver.openOutputStream(docUri, "rwt") } catch (_: Exception) { null }
                    if (out != null) return out
                }
            }
        }
    } catch (_: Exception) {}

    try {
        val directDocUri = treeUri.buildUpon()
            .appendEncodedPath("document")
            .appendEncodedPath(Uri.encode(sanitizedFileDocId))
            .build()
        val out = try { resolver.openOutputStream(directDocUri, "rwt") } catch (_: Exception) { null }
        if (out != null) return out
    } catch (_: Exception) {}

    deleteDocumentInTree(resolver, treeUri, name, context)
    var suffixedFallback: Uri? = null
    for (parentUri in parentUrisForCreate(treeUri, treeDocId)) {
        try {
            val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            if (created != null) {
                val createdName = created.lastPathSegment?.substringAfterLast('/')
                if (createdName == name) {
                    val out = try { resolver.openOutputStream(created) } catch (_: Exception) { null }
                    if (out != null) return out
                } else {
                    if (suffixedFallback == null) {
                        suffixedFallback = created
                    } else {
                        try { DocumentsContract.deleteDocument(resolver, created) } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (suffixedFallback != null) {
        val out = try { resolver.openOutputStream(suffixedFallback) } catch (_: Exception) { null }
        if (out != null) return out
        try { DocumentsContract.deleteDocument(resolver, suffixedFallback) } catch (_: Exception) {}
    }

    for (dirUri in dirUris(treeUri, treeDocId)) {
        val insertUri = Uri.withAppendedPath(dirUri, "children")
        try {
            val values = ContentValues().apply {
                put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
                put(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
            }
            val created = resolver.insert(insertUri, values)
            if (created != null) {
                val out = try { resolver.openOutputStream(created) } catch (_: Exception) { null }
                if (out != null) return out
            }
        } catch (_: Exception) {}
    }

    return strategyFileFallback(context, treeUri, name)
}

private fun openExistingUris(treeUri: Uri, fileDocId: String): List<Uri> {
    val bare = DocumentsContract.buildDocumentUri(treeUri.authority, fileDocId)
    val treeAnchored = treeUri.buildUpon()
        .appendEncodedPath("document")
        .appendEncodedPath(Uri.encode(fileDocId))
        .build()
    return listOf(bare, treeAnchored)
}

private fun parentUrisForCreate(treeUri: Uri, treeDocId: String): List<Uri> {
    val bare = DocumentsContract.buildDocumentUri(treeUri.authority, treeDocId)
    val treeAnchored = treeUri.buildUpon()
        .appendEncodedPath("document")
        .appendEncodedPath(Uri.encode(treeDocId))
        .build()
    return listOf(bare, treeUri, treeAnchored)
}

private fun dirUris(treeUri: Uri, treeDocId: String): List<Uri> {
    val bare = DocumentsContract.buildDocumentUri(treeUri.authority, treeDocId)
    val treeAnchored = treeUri.buildUpon()
        .appendEncodedPath("document")
        .appendEncodedPath(Uri.encode(treeDocId))
        .build()
    return listOf(bare, treeAnchored)
}

private fun deleteDocumentInTree(resolver: android.content.ContentResolver, treeUri: Uri, fileName: String, context: Context?) {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)

    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val cursor = resolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                if (displayName == fileName) {
                    val docUri = treeUri.buildUpon()
                        .appendEncodedPath("document")
                        .appendEncodedPath(Uri.encode(docId))
                        .build()
                    DocumentsContract.deleteDocument(resolver, docUri)
                    return
                }
            }
        }
    } catch (_: Exception) {}

    try {
        val directUri = treeUri.buildUpon()
            .appendEncodedPath("document")
            .appendEncodedPath(Uri.encode("$treeDocId/$fileName"))
            .build()
        DocumentsContract.deleteDocument(resolver, directUri)
        return
    } catch (_: Exception) {}

    try {
        val bareUri = DocumentsContract.buildDocumentUri(treeUri.authority, "$treeDocId/$fileName")
        DocumentsContract.deleteDocument(resolver, bareUri)
        return
    } catch (_: Exception) {}

    if (context != null) {
        try {
            val realPath = getFullPathFromTreeUri(treeUri, context)
            if (realPath != null) {
                val f = File(realPath, fileName)
                if (f.exists()) f.delete()
            }
        } catch (_: Exception) {}
    }
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
    try {
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
    } catch (_: Exception) {}
    // Fallback for primary volume using deprecated-but-functional API
    if (volumeId == "primary") {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    return null
}
