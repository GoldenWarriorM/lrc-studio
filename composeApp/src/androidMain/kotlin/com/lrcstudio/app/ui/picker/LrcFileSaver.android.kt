package com.lrcstudio.app.ui.picker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
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
    val tag = "LrcFileSaver"
    Log.w(tag, "treeUri=$treeUri treeDocId=$treeDocId name=$name (was=$defaultName)")

    // Strategy 1: open existing file for overwrite (try bare doc URI + tree-anchored)
    val sanitizedFileDocId = "$treeDocId/$name"
    for (uri in openExistingUris(treeUri, sanitizedFileDocId)) {
        Log.w(tag, "S1 trying openOutputStream on $uri")
        val out = try { resolver.openOutputStream(uri) } catch (e: Exception) { Log.w(tag, "S1 failed: ${e.message}"); null }
        if (out != null) { Log.w(tag, "S1 success on $uri"); return out }
    }

    // Strategy 1.5: find existing file via children query, open for write with tree-anchored URI
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        Log.w(tag, "S1.5 query children $childrenUri")
        val cursor = resolver.query(childrenUri, null, null, null, null)
        var childCount = 0
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                childCount++
                Log.w(tag, "S1.5 child #$childCount displayName=$displayName docId=$docId")
                if (displayName == name) {
                    val docUri = treeUri.buildUpon()
                        .appendEncodedPath("document")
                        .appendEncodedPath(Uri.encode(docId))
                        .build()
                    Log.w(tag, "S1.5 matched, trying openOutputStream($docUri, \"rwt\")")
                    val out = try { resolver.openOutputStream(docUri, "rwt") } catch (e: Exception) { Log.w(tag, "S1.5 openOutputStream(rwt) failed: ${e.message}"); null }
                    if (out != null) { Log.w(tag, "S1.5 success on $docUri"); return out }
                }
            }
        }
        Log.w(tag, "S1.5 done, childCount=$childCount")
    } catch (e: Exception) { Log.w(tag, "S1.5 exception: ${e.message}") }

    // Strategy 1.6: try openOutputStream on tree-anchored URI for the target file directly
    try {
        val directDocUri = treeUri.buildUpon()
            .appendEncodedPath("document")
            .appendEncodedPath(Uri.encode(sanitizedFileDocId))
            .build()
        Log.w(tag, "S1.6 trying openOutputStream($directDocUri, \"rwt\")")
        val out = try { resolver.openOutputStream(directDocUri, "rwt") } catch (e: Exception) { Log.w(tag, "S1.6 failed: ${e.message}"); null }
        if (out != null) { Log.w(tag, "S1.6 success"); return out }
    } catch (_: Exception) {}

    // Strategy 2: delete existing then create new (multiple parent URI formats)
    Log.w(tag, "S2 calling deleteDocumentInTree for $name")
    deleteDocumentInTree(resolver, treeUri, name, context, tag)
    var suffixedFallback: Uri? = null
    for (parentUri in parentUrisForCreate(treeUri, treeDocId)) {
        Log.w(tag, "S2 trying createDocument on parent=$parentUri mime=application/octet-stream name=$name")
        try {
            val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            if (created != null) {
                val createdName = created.lastPathSegment?.substringAfterLast('/')
                Log.w(tag, "S2 createDocument returned $created displayName=$createdName")
                if (createdName == name) {
                    val out = try { resolver.openOutputStream(created) } catch (e: Exception) { Log.w(tag, "S2 openOutputStream failed: ${e.message}"); null }
                    if (out != null) { Log.w(tag, "S2 success on $created"); return out }
                } else {
                    // Suffixed name — delete original might have failed (Android 16 ESP)
                    // Save as last-resort fallback, try next parent URI
                    Log.w(tag, "S2 got suffixed name, saving as fallback")
                    if (suffixedFallback == null) {
                        suffixedFallback = created
                    } else {
                        try { DocumentsContract.deleteDocument(resolver, created) } catch (_: Exception) {}
                    }
                }
            } else {
                Log.w(tag, "S2 createDocument returned null")
            }
        } catch (e: Exception) { Log.w(tag, "S2 createDocument threw: ${e.message}") }
    }
    // Try the suffixed fallback if all parent URIs created suffixed files
    if (suffixedFallback != null) {
        Log.w(tag, "S2 trying suffixed fallback: $suffixedFallback")
        val out = try { resolver.openOutputStream(suffixedFallback) } catch (e: Exception) { Log.w(tag, "S2 suffixed fallback failed: ${e.message}"); null }
        if (out != null) { Log.w(tag, "S2 success on suffixed fallback"); return out }
        try { DocumentsContract.deleteDocument(resolver, suffixedFallback) } catch (_: Exception) {}
    }

    // Strategy 3: insert via /children path (try bare doc URI + tree-anchored)
    for (dirUri in dirUris(treeUri, treeDocId)) {
        val insertUri = Uri.withAppendedPath(dirUri, "children")
        Log.w(tag, "S3 trying insert on $insertUri")
        try {
            val values = ContentValues().apply {
                put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
                put(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
            }
            val created = resolver.insert(insertUri, values)
            Log.w(tag, "S3 insert returned ${if (created != null) created else "null"}")
            if (created != null) {
                val out = try { resolver.openOutputStream(created) } catch (e: Exception) { Log.w(tag, "S3 openOutputStream failed: ${e.message}"); null }
                if (out != null) { Log.w(tag, "S3 success on $created"); return out }
            }
        } catch (e: Exception) { Log.w(tag, "S3 insert threw: ${e.message}") }
    }

    // Strategy 4: fallback to java.io.File via path resolution
    Log.w(tag, "S4 trying java.io.File fallback")
    val result = strategyFileFallback(context, treeUri, name)
    Log.w(tag, "S4 returned ${if (result != null) "OutputStream" else "null"}")
    return result
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

private fun deleteDocumentInTree(resolver: android.content.ContentResolver, treeUri: Uri, fileName: String, context: Context?, tag: String) {
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    Log.w(tag, "deleteDocumentInTree fileName=$fileName treeDocId=$treeDocId")

    // Attempt 1: find via children query + delete via tree-anchored child URI
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        Log.w(tag, "del-A1 childrenUri=$childrenUri")
        val cursor = resolver.query(childrenUri, null, null, null, null)
        var delChildCount = 0
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                delChildCount++
                Log.w(tag, "del-A1 child #$delChildCount displayName=$displayName docId=$docId")
                if (displayName == fileName) {
                    val docUri = treeUri.buildUpon()
                        .appendEncodedPath("document")
                        .appendEncodedPath(Uri.encode(docId))
                        .build()
                    Log.w(tag, "del-A1 matched, deleting $docUri")
                    DocumentsContract.deleteDocument(resolver, docUri)
                    Log.w(tag, "del-A1 delete succeeded")
                    return
                }
            }
        }
        Log.w(tag, "del-A1 no match, childCount=$delChildCount")
    } catch (e: Exception) { Log.w(tag, "del-A1 exception: ${e.message}") }

    // Attempt 2: delete via tree-anchored URI constructed directly
    try {
        val directUri = treeUri.buildUpon()
            .appendEncodedPath("document")
            .appendEncodedPath(Uri.encode("$treeDocId/$fileName"))
            .build()
        Log.w(tag, "del-A2 directUri=$directUri")
        DocumentsContract.deleteDocument(resolver, directUri)
        Log.w(tag, "del-A2 delete succeeded")
        return
    } catch (e: Exception) { Log.w(tag, "del-A2 exception: ${e.message}") }

    // Attempt 3: delete via bare document URI
    try {
        val bareUri = DocumentsContract.buildDocumentUri(treeUri.authority, "$treeDocId/$fileName")
        Log.w(tag, "del-A3 bareUri=$bareUri")
        DocumentsContract.deleteDocument(resolver, bareUri)
        Log.w(tag, "del-A3 delete succeeded")
        return
    } catch (e: Exception) { Log.w(tag, "del-A3 exception: ${e.message}") }

    // Attempt 4: java.io.File fallback via path resolution
    if (context != null) {
        try {
            val realPath = getFullPathFromTreeUri(treeUri, context)
            Log.w(tag, "del-A4 realPath=$realPath")
            if (realPath != null) {
                val f = File(realPath, fileName)
                Log.w(tag, "del-A4 file=${f.absolutePath} exists=${f.exists()}")
                if (f.exists()) {
                    f.delete()
                    Log.w(tag, "del-A4 file deleted=${!f.exists()}")
                }
            }
        } catch (e: Exception) { Log.w(tag, "del-A4 exception: ${e.message}") }
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
