package com.lrcstudio.app.domain.usecase

/**
 * Standalone punctuation, including the full-width marks Japanese, Chinese and
 * Korean lyrics use.
 *
 * A word made of punctuation only is not sung: the word cursor skips it and the
 * export leaves it without a timestamp, so the mark is covered by the timing of
 * the word in front of it (the "interpolate punctuation" setting).
 */
object Punctuation {

    private const val MARKS: String =
        ".,!?;:-–—‐‑()[]{}\"'«»“”‘’…‥・" +
            "、。，．！？；：（）｛｝［］＜＞〜～" +
            "｡､･｢｣"

    /** True for a single character that is a punctuation mark, not a letter. */
    fun isMark(char: Char): Boolean = char in MARKS

    /** True when [text] contains punctuation only, e.g. "、", "?!", "…". */
    fun isStandalone(text: String): Boolean =
        text.isNotBlank() && text.all(::isMark)
}
