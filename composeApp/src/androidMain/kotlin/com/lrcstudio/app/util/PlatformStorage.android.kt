package com.lrcstudio.app.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

private var appContext: Context? = null

internal fun initPlatformContext(context: Context) {
    if (appContext == null) {
        appContext = context.applicationContext
    }
}

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
    val context = appContext ?: return false.also { Log.w("LrcSA", "lrcFileInDirectoryExists: no context") }
    Log.w("LrcSA", "lrcFileInDirectoryExists: directory=$directory fileName=$fileName")
    return fileExistsInTree(context, directory, fileName)
}

private fun sanitizeFileName(name: String): String {
    return name.replace(':', '_')
}

private fun fileExistsInTree(context: Context, treeUriString: String, fileName: String): Boolean {
    val safeName = sanitizeFileName(fileName)
    val treeUri = try { Uri.parse(treeUriString) } catch (e: Exception) { Log.w("LrcSA", "fileExistsInTree: bad URI: ${e.message}"); return false }
    val treeDocId = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (e: Exception) { Log.w("LrcSA", "fileExistsInTree: no treeDocId: ${e.message}"); return false }
    val resolver = context.contentResolver

    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        Log.w("LrcSA", "fileExistsInTree: query $childrenUri")
        val cursor = resolver.query(childrenUri, null, null, null, null)
        var childCount = 0
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                childCount++
                Log.w("LrcSA", "fileExistsInTree: child #$childCount name=$name")
                if (name == safeName) { Log.w("LrcSA", "fileExistsInTree: $fileName FOUND in SAF (as $safeName)"); return true }
            }
        }
        Log.w("LrcSA", "fileExistsInTree: SAF children done, count=$childCount, $fileName NOT found")
    } catch (e: Exception) { Log.w("LrcSA", "fileExistsInTree: SAF query exception: ${e.message}") }

    try {
        val realPath = getFullPathFromTreeUri(treeUri, context)
        Log.w("LrcSA", "fileExistsInTree: realPath=$realPath")
        if (realPath != null) {
            val f = File(realPath, safeName)
            val exists = f.exists()
            Log.w("LrcSA", "fileExistsInTree: File($realPath, $safeName).exists()=$exists")
            return exists
        }
        return false
    } catch (e: Exception) { Log.w("LrcSA", "fileExistsInTree: File fallback exception: ${e.message}"); return false }
}

@Composable
actual fun rememberFileExistsChecker(directory: String?): (String) -> Boolean {
    val context = LocalContext.current
    return { fileName -> directory != null && fileExistsInTree(context, directory, fileName) }
}

@Composable
actual fun rememberStorageDir(): String {
    val context = LocalContext.current
    initPlatformContext(context)
    return remember {
        File(context.filesDir, "lrcstudio").absolutePath.also {
            File(it).mkdirs()
        }
    }
}

// ---- SAF tree URI to real path resolution (from LRC-Editor) ----

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
