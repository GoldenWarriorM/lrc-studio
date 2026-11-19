package com.lrcstudio.app.domain.usecase

/**
 * Splits a lyric word into syllables.
 *
 * Letters alone are not enough to do this: "mixed" is a single syllable while
 * "beau-ti-ful" is three, so the implementation on the JVM side runs Liang's
 * hyphenation algorithm over the official TeX hyphenation patterns (hyph-utf8
 * en-US and ru) through Apache FOP's Liang engine (`org.apache.xmlgraphics:fop-core`).
 */
object Syllabifier {

    /**
     * Returns the syllables of [word], or a single element when the word has no
     * safe syllable boundary (or its script is not supported).
     */
    fun split(word: String): List<String> = splitWithPatterns(word)
}

/** Platform implementation of [Syllabifier.split]. */
internal expect fun splitWithPatterns(word: String): List<String>
