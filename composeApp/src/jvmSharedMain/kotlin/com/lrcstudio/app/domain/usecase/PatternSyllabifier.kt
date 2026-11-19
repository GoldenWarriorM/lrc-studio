package com.lrcstudio.app.domain.usecase

import org.apache.fop.hyphenation.Hyphenation
import org.apache.fop.hyphenation.HyphenationTree
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * TeX pattern based syllable splitter shared by the Android and desktop builds.
 *
 * The patterns are the official hyph-utf8 files for en-US and ru, converted to
 * the format Apache FOP reads; the algorithm itself (Liang's) is FOP's own
 * hyphenation engine from `org.apache.xmlgraphics:fop-core`.
 */
internal object PatternSyllabifier {

    private val english: HyphenationTree? by lazy { load("hyph/hyphen-en.xml") }
    private val russian: HyphenationTree? by lazy { load("hyph/hyphen-ru.xml") }

    fun split(word: String): List<String> {
        // Patterns only cover letters, so hyphenate the letters and map the cuts
        // back onto the original word to keep punctuation and case intact.
        val letters = StringBuilder()
        val letterPositions = ArrayList<Int>(word.length)
        word.forEachIndexed { index, ch ->
            if (ch.isLetter()) {
                letters.append(ch)
                letterPositions.add(index)
            }
        }
        if (letters.length < 3) return listOf(word)
        val isRussian = letters.all { it.isCyrillicLetter() }
        val tree = when {
            isRussian -> russian
            letters.all { it.isLatinLetter() } -> english
            else -> null
        } ?: return listOf(word)
        // hyph-utf8 suggests (2,2) for Russian; English uses (2,2) as well because
        // syllables such as "moth-er" are worth splitting in lyrics.
        val cuts = tree.breakPoints(letters.toString(), 2, 2)
            .map { letterPositions[it] }
        if (cuts.isEmpty()) return listOf(word)

        val syllables = mutableListOf<String>()
        var from = 0
        for (cut in cuts) {
            if (cut <= from || cut >= word.length) continue
            syllables.add(word.substring(from, cut))
            from = cut
        }
        if (from == 0) return listOf(word)
        syllables.add(word.substring(from))
        return syllables.filter { it.isNotEmpty() }
    }

    /** Character offsets where the word may be broken, e.g. "let-ter" -> [3]. */
    private fun HyphenationTree.breakPoints(word: String, left: Int, right: Int): List<Int> {
        val hyphenation: Hyphenation = hyphenate(word, left, right) ?: return emptyList()
        return hyphenation.hyphenationPoints.toList()
    }

    private fun load(resource: String): HyphenationTree? = try {
        val stream = PatternSyllabifier::class.java.classLoader?.getResourceAsStream(resource)
            ?: PatternSyllabifier::class.java.getResourceAsStream("/$resource")
        stream?.bufferedReader()?.use { reader ->
            val content = reader.readText()
            HyphenationTree().apply { loadPatterns(InputSource(StringReader(content))) }
        }
    } catch (e: Exception) {
        null
    }
}

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isCyrillicLetter(): Boolean =
    this in 'а'..'я' || this in 'А'..'Я' || this == 'ё' || this == 'Ё'
