package com.lrcstudio.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import meow.nyx.glyphmind.Dictionaries
import meow.nyx.glyphmind.DictionaryPack
import meow.nyx.glyphmind.DictionaryPacks
import meow.nyx.glyphmind.LanguageDetector
import meow.nyx.glyphmind.LyricsRomanizer
import meow.nyx.glyphmind.WordTimestamp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * Romanizes lyrics for display through the vendored Glyphmind engine.
 *
 * Kanji and Han characters need the engine's language packs, which ship
 * separately (14 MB for Japanese, 258 KB for Chinese) and are downloaded once
 * into `<storageDir>/romanization`. Without them kana and Hangul still convert
 * and unknown characters stay as they are.
 */
internal object RomanizerEngine {

    private var cachedDir: String? = null
    private var cachedEngine: LyricsRomanizer? = null
    private var cachedStore: DictionaryPacks? = null
    private val installed = mutableSetOf<String>()

    private fun engineFor(storageDir: String): Pair<LyricsRomanizer, DictionaryPacks> {
        val cached = cachedEngine
        val store = cachedStore
        if (cached != null && store != null && cachedDir == storageDir) return cached to store
        val packs = DictionaryPacks(File(storageDir, "romanization").absolutePath) { pack -> download(pack) }
        val engine = LyricsRomanizer(packs)
        cachedDir = storageDir
        cachedEngine = engine
        cachedStore = packs
        installed.clear()
        return engine to packs
    }

    suspend fun romanize(
        storageDir: String,
        lines: List<List<String>>,
        downloadPacks: Boolean
    ): RomanizationOutcome {
        if (lines.isEmpty()) return RomanizationOutcome(lines.map { null })
        val (engine, store) = engineFor(storageDir)
        val lineTexts = lines.map { words -> words.joinToString(" ").trim() }
        val fullText = lineTexts.joinToString("\n")
        val languages = LanguageDetector.languageShare(lineTexts).keys
            .mapNotNull(::romanizationLanguage)
            .toSet()
        if (languages.isEmpty()) return RomanizationOutcome(lines.map { null })

        val required = engine.requiredDictionaries(fullText, languages)
        val missing = required.filter { pack ->
            pack.id !in installed && store.readInstalled(pack) == null
        }
        // Both language packs ship inside the app, so they install themselves on
        // the first conversion. Only a pack the app does not bundle would need
        // the network, and that asks the user first.
        val needsNetwork = missing.filter { !hasBundledPack(it) }
        if (needsNetwork.isNotEmpty() && !downloadPacks) {
            val partial = convert(engine, lines, lineTexts, fullText, languages)
            return RomanizationOutcome(partial, pendingPacks = needsNetwork.map { it.toPack() })
        }

        var error: String? = null
        if (missing.isNotEmpty()) {
            error = try {
                engine.prepareDictionariesIfNeeded(fullText, languages)
                installed += required.map { it.id }
                null
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                "Language data could not be downloaded: ${failure.message ?: "unknown error"}"
            }
        }

        val converted = convert(engine, lines, lineTexts, fullText, languages)
        return RomanizationOutcome(converted, error = error)
    }

    private suspend fun convert(
        engine: LyricsRomanizer,
        lines: List<List<String>>,
        lineTexts: List<String>,
        fullText: String,
        languages: Set<String>
    ): List<List<String>?> = withContext(Dispatchers.Default) {
        lines.mapIndexed { index, words ->
            val lineText = lineTexts[index]
            if (lineText.isBlank() || words.isEmpty()) return@mapIndexed null
            val stamps = words.mapIndexed { wordIndex, word ->
                WordTimestamp(
                    text = word,
                    startTime = 0.0,
                    endTime = 0.0,
                    hasTrailingSpace = wordIndex < words.lastIndex
                )
            }
            val result = engine.romanizeWords(
                fullText = fullText,
                line = lineText,
                words = stamps,
                enabledLanguages = languages,
                cyrillicByLine = true
            ) ?: return@mapIndexed null
            val romanized = result.second.map { it.text.trim() }
            romanized.takeIf { it.size == words.size }
        }
    }

    /**
     * The detector reports BCP-47 tags while the engine's language switches use
     * the setting names above, so each tag is mapped to the name that enables
     * the matching conversion.
     */
    private fun romanizationLanguage(tag: String): String? = when {
        tag == "ja" -> "Japanese"
        tag.startsWith("zh", ignoreCase = true) -> "Chinese"
        tag == "ko" -> "Korean"
        tag == "ar" -> "Arabic"
        tag == "hi" -> "Hindi"
        tag == "pa" -> "Punjabi"
        tag == "th" -> "Thai"
        tag == "ru" -> "Russian"
        tag == "uk" -> "Ukrainian"
        tag == "sr" -> "Serbian"
        tag == "bg" -> "Bulgarian"
        tag == "be" -> "Belarusian"
        tag == "ky" -> "Kyrgyz"
        tag == "mk" -> "Macedonian"
        else -> null
    }

    private fun DictionaryPack.toPack() = RomanizationPack(
        id = id,
        label = if (id.startsWith("japanese")) "Japanese readings" else "Chinese readings",
        sizeBytes = size
    )

    private fun openBundledPack(pack: DictionaryPack): InputStream? =
        RomanizerEngine::class.java.getResourceAsStream("/packs/${pack.id}.gz")
            ?: RomanizerEngine::class.java.classLoader?.getResourceAsStream("packs/${pack.id}.gz")

    private fun hasBundledPack(pack: DictionaryPack): Boolean = openBundledPack(pack)?.let { stream ->
        stream.close()
        true
    } ?: false

    /**
     * Feeds the engine from the packs shipped in the APK/jar, so romanization
     * never depends on the device resolving github.com; the HTTP path stays as a
     * fallback for packs that are not bundled.
     */
    private suspend fun download(pack: DictionaryPack): ByteArray {
        val bundled = withContext(Dispatchers.IO) {
            openBundledPack(pack)?.use { raw ->
                GZIPInputStream(raw).use { it.readBytes() }
            }
        }
        if (bundled != null) return bundled
        return downloadFromNetwork(pack)
    }

    /** Bounded download so a broken response cannot exhaust memory. */
    private suspend fun downloadFromNetwork(pack: DictionaryPack): ByteArray = withContext(Dispatchers.IO) {
        val connection = (URI(pack.url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val output = ByteArrayOutputStream(pack.size.toInt())
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= pack.size) { "Response is larger than the expected pack" }
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
