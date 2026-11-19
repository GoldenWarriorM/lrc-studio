// SPDX-License-Identifier: GPL-3.0-only
package meow.nyx.glyphmind

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The fetch callback must bound downloads to [size] bytes and propagate cancellation. */
data class DictionaryPack(
    val id: String,
    val url: String,
    val size: Long,
    val sha256: String,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]+")))
        require(size in 1..64L * 1024 * 1024)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

object Dictionaries {
    val japanese =
        DictionaryPack(
            "japanese-v1",
            "https://github.com/nyxiereal/glyphmind/releases/download/dict-v1/japanese.gjp",
            14488108,
            "cc2436efdc09470f779e62eb370c92c28e6e3be9ab58bc1684329fb496bae4d8",
        )
    val mandarin =
        DictionaryPack(
            "mandarin-v1",
            "https://github.com/nyxiereal/glyphmind/releases/download/dict-v1/mandarin.bin",
            264352,
            "ed3f75c21aadf72819f226ab47d17ccc35166c1acc2e2d0ae1d0801ea77f4fa0",
        )
}

/** Installs immutable, verified packs. Instances share an in-process installation lock. */
class DictionaryPacks(
    storageDirectory: String,
    private val fetch: suspend (DictionaryPack) -> ByteArray,
) {

    private val root = File(storageDirectory)

    fun readInstalled(pack: DictionaryPack): ByteArray? =
        try {
            val file = File(File(root, pack.id), "pack.bin")
            if (!file.isFile || file.length() != pack.size) {
                null
            } else {
                val bytes = file.readBytes()
                bytes.takeIf { it.size.toLong() == pack.size && verify(pack, it) }
            }
        } catch (_: IOException) {
            null
        }

    suspend fun install(pack: DictionaryPack): ByteArray =
        installMutex.withLock {
            readInstalled(pack)?.let { return@withLock it }
            root.mkdirs()
            val staging = File(root, ".${pack.id}.staging")
            val target = File(root, pack.id)
            deleteTree(staging)
            try {
                val bytes = fetch(pack)
                require(verify(pack, bytes)) { "Dictionary size or SHA-256 mismatch: ${pack.id}" }
                staging.mkdirs()
                val staged = File(staging, "pack.bin")
                staged.writeBytes(bytes)
                val written = staged.readBytes()
                require(verify(pack, written)) { "Dictionary staging verification failed: ${pack.id}" }
                deleteTree(target)
                move(staging, target)
                bytes
            } finally {
                deleteTree(staging)
            }
        }

    private fun move(staging: File, target: File) {
        try {
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            check(staging.renameTo(target)) { "Dictionary pack could not be moved into place" }
        }
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        file.delete()
    }

    private companion object {
        val installMutex = Mutex()

        fun verify(
            pack: DictionaryPack,
            bytes: ByteArray,
        ): Boolean = bytes.size.toLong() == pack.size && sha256(bytes) == pack.sha256
    }
}
