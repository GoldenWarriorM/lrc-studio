package com.lrcstudio.app.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
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
    val context = appContext ?: return false
    return fileExistsInTree(context, directory, fileName)
}

private fun fileExistsInTree(context: Context, treeUriString: String, fileName: String): Boolean {
    try {
        val treeUri = Uri.parse(treeUriString)
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUri(treeUri.authority, "$treeDocId/$fileName")
        val mimeType = context.contentResolver.getType(documentUri)
        if (mimeType != null) return true
    } catch (_: Exception) {}

    try {
        val realPath = getFullPathFromTreeUri(Uri.parse(treeUriString), context) ?: return false
        return File(realPath, fileName).exists()
    } catch (_: Exception) { return false }
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
