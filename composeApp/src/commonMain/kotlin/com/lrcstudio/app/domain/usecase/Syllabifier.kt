package com.lrcstudio.app.domain.usecase

/**
 * Splits a lyric word into syllables.
 *
 * Letters alone are not enough to do this: "mixed" is a single syllable while
 * "beau-ti-ful" is three, so the implementation on the JVM side runs Liang's
 * hyphenation algorithm over the official TeX hyphenation patterns (hyph-utf8
 * en-US and ru) through Apache FOP's Liang engine (`org.apache.xmlgraphics:fop-core`).
 *
 * Japanese has no such patterns: its syllables are morae, one kana each, with
 * small kana and the long-vowel mark belonging to the mora in front of them.
 */
object Syllabifier {

    /**
     * Returns the syllables of [word], or a single element when the word has no
     * safe syllable boundary (or its script is not supported).
     */
    fun split(word: String): List<String> = splitKanaMorae(word) ?: splitWithPatterns(word)

    /** Small kana and the long-vowel mark extend the mora before them. */
    private val moraExtenders = "ぁぃぅぇぉっゃゅょゎゕゖゝゞァィゥェォッャュョヮヵヶヽヾーｰ"

    private fun isJapaneseKana(char: Char): Boolean =
        char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' || char in '\uFF66'..'\uFF9D'

    /**
     * Splits the kana of [word] into morae, leaving kanji runs in one piece
     * because their reading is not known here. Returns null when the word has no
     * kana or would not break into more than one piece.
     */
    private fun splitKanaMorae(word: String): List<String>? {
        if (word.none { isJapaneseKana(it) }) return null
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        val punctuation = StringBuilder()
        var inKanaRun = false

        fun flush() {
            if (current.isNotEmpty()) {
                pieces.add(current.toString())
                current.setLength(0)
            }
        }

        fun flushPunctuation() {
            if (punctuation.isNotEmpty()) {
                pieces.add(punctuation.toString())
                punctuation.setLength(0)
            }
        }

        for (char in word) {
            when {
                // A mark is a piece of its own: it is not sung, so the word
                // cursor skips it and it stays glued to the mora before it.
                Punctuation.isMark(char) -> {
                    if (inKanaRun) {
                        flush()
                        inKanaRun = false
                    }
                    punctuation.append(char)
                }

                !isJapaneseKana(char) -> {
                    // Kanji or latin: end the kana and punctuation runs, then
                    // keep consecutive non-kana characters together.
                    flushPunctuation()
                    if (inKanaRun) {
                        flush()
                        inKanaRun = false
                    }
                    current.append(char)
                }

                char in moraExtenders -> {
                    flushPunctuation()
                    current.append(char)
                }

                else -> {
                    flushPunctuation()
                    flush()
                    current.append(char)
                    inKanaRun = true
                }
            }
        }
        flushPunctuation()
        flush()
        return pieces.takeIf { it.size > 1 }
    }
}

/** Platform implementation of [Syllabifier.split] for pattern based scripts. */
internal expect fun splitWithPatterns(word: String): List<String>
