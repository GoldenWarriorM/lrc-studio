// SPDX-License-Identifier: GPL-3.0-only
// Metrolist Project (C) 2026, contributors including l6t9. See NOTICE.md.
package meow.nyx.glyphmind

fun containsJapaneseKanji(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val first = text[index]
        val codePoint =
            if (first in '\ud800'..'\udbff' && index + 1 < text.length && text[index + 1] in '\udc00'..'\udfff') {
                val second = text[index + 1]
                index += 2
                0x10000 + ((first.code - 0xd800) shl 10) + (second.code - 0xdc00)
            } else {
                index++
                first.code
            }
        if (isJapaneseKanjiCodePoint(codePoint)) return true
    }
    return false
}

private fun isJapaneseKanjiCodePoint(codePoint: Int): Boolean =
    codePoint in 0x3400..0x9fff ||
        codePoint in 0xf900..0xfaff ||
        codePoint in 0x20000..0x2a6df ||
        codePoint in 0x2a700..0x2b73f ||
        codePoint in 0x2b740..0x2b81f ||
        codePoint in 0x2b820..0x2ceaf ||
        codePoint in 0x2ceb0..0x2ebef ||
        codePoint in 0x2ebf0..0x2ee5f ||
        codePoint in 0x2f800..0x2fa1f ||
        codePoint in 0x30000..0x3134f ||
        codePoint in 0x31350..0x323af
